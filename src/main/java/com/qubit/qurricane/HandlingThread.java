/*
 * Qurrican
 * Fast HTTP Server Solution.
 * Copyright 2016, Qubit Group
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  
 * If not, see <https://www.gnu.org/licenses/lgpl-3.0.en.html>
 * 
 * Author: Peter Fronc <peter.fronc@qubitdigital.com>
 */

package com.qubit.qurricane;

import static com.qubit.qurricane.DataHandler.bodyReadyHandler;
import static com.qubit.qurricane.HandlingThreadPooled.log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public abstract class HandlingThread extends Thread {
  
  static final Logger log
          = Logger.getLogger(HandlingThread.class.getName());
  
  private ByteBuffer buffer;
  
  private int defaultMaxMessageSize;
  private long delayForNoIO = 1;
  private boolean running;
  
  abstract boolean addJob(DataHandler dataHandler);

  abstract boolean canAddJob();
  
  private long singlePassDelay = 0;
  
  private static volatile long closedIdleCounter = 0; // less more counter...
  
  protected boolean handleMaxIdle(
          DataHandler dataHandler, long maxIdle) {
    //check if connection is not open too long! Prevent DDoS
    long idle = dataHandler.getMaxIdle(maxIdle);
    if (idle != 0 && (System.currentTimeMillis() - dataHandler.getTouch()) > idle) {
      log.log(Level.INFO,
              "Max idle gained - closing, total: {0}", ++closedIdleCounter);
      return true;
    }

    // check if not too large
    int maxSize = dataHandler
            .getMaxMessageSize(getDefaultMaxMessageSize());

    if (maxSize != -1 && dataHandler.getSize() >= maxSize) {
      log.log(Level.INFO, "Max size reached - closing: {0}",
              dataHandler.getSize());
      return true;
    }

    return false;
  }

  @Override
  public void run() {
    try {
      this.setRunning(true);
      while (this.isRunning()) {
        while (this.hasJobs()) {
          if (!this.waitForSomethingToIO(this.runSinglePass())) {
            // if it is not waiting for IO, try standard break
            this.takeSomeBreak();
          }
        }

        try {
          if (!this.hasJobs()) {
            synchronized (this) {
              this.wait();
            }
          }
        } catch (InterruptedException ex) {
          log.log(Level.SEVERE, null, ex);
        }
      }
    } finally {
      this.getServer().removeThread(this);
    }
  }

  
  
  /**
   * Returns false if not finished writing or
 "this.canCloseOrResetAndPutBack(...)" when finished. It
   * tells if job can be released.
   *
   * @param key
   * @param dataHandler
   * @return
   * @throws IOException
   */
  private int writeResponse(SocketChannel channel, DataHandler dataHandler)
          throws IOException {
    int written = dataHandler.write(channel);
    if (written < 0) {
      dataHandler.writingResponse = false; // finished writing
      if (written == -1) {
        this.runOnFinishedHandler(dataHandler);
        if (dataHandler.canCloseOrResetAndPutBack(channel, true)) {
          return -1;
        } else {
          return 0;
        }
      }
    }
    return written;
  }
  
  /**
   *
   * @param dataHandler
   * @return true only if key should be released
   * @throws IOException
   */
  protected int processJob(DataHandler dataHandler)
          throws IOException {
    SocketChannel channel = dataHandler.getChannel();
    if (channel.isConnected()) {
      if (dataHandler.writingResponse) { // in progress of writing
        return this.writeResponse(channel, dataHandler);
      } else {
        int many = dataHandler.read(channel, buffer);
        
        if (many < 0) { // reading is over
          if (many == -2) {// finished reading correctly, otherwise many is -1
            dataHandler.writingResponse = true; // running writing
            // writingResponse will be unchecked by writeResponse(...)
            bodyReadyHandler(dataHandler);
            return this.writeResponse(channel, dataHandler);
          } else if (many == -1) {
            log.fine("Premature EOS from channel.");
          }
        }
        
        return many;
      }
    } else {
      return 0; //no IO occured
    }
  }
  
  /**
   * 
   * @param channel
   * @param dataHandler
   * @param finishedWriting
   * @return true if closed connection, false otherwise and will reset handler
   */
  
  
  /**
   * @return the defaultMaxMessageSize
   */
  public int getDefaultMaxMessageSize() {
    return defaultMaxMessageSize;
  }

  /**
   * @param defaultMaxMessageSize the defaultMaxMessageSize to set
   */
  public void setDefaultMaxMessageSize(int defaultMaxMessageSize) {
    this.defaultMaxMessageSize = defaultMaxMessageSize;
  }

  /**
   * @param buffer the buffer to set
   */
  public void setBuffer(ByteBuffer buffer) {
    this.buffer = buffer;
  }
  
  protected abstract boolean hasJobs();
  
  public static volatile long totalWaitedIO = 0;
  
  protected boolean waitForSomethingToIO(int code) {
    if (this.delayForNoIO > 0 && code != 0) {// code is 0 if no IO occured
      long timeToWait = (long)(this.delayForNoIO);
      totalWaitedIO += timeToWait;
      synchronized (this) {
        try {
          this.wait(timeToWait);
          return true;
        } catch (InterruptedException ex) {
          log.log(Level.FINE, "waitForSomethingToIO delay interrupted.", ex);
        }
      }
    }
    return false;
  }

  protected void takeSomeBreak() {
    if (this.singlePassDelay > 0) {
      synchronized (this) {
        try {
          this.wait(this.singlePassDelay);
        } catch (InterruptedException ex) {
          log.log(Level.FINE, "takeSomeBreak delay interrupted.", ex);
        }
      }
    }
  }
  
  private void runOnFinishedHandler(DataHandler dataHandler) {
    if (dataHandler.getRequest() != null) {
      if (dataHandler.getRequest().getWriteFinishedHandler() != null) {
        try {
          dataHandler.getRequest().getWriteFinishedHandler().run();
        } catch (Exception e) {
          log.log(Level.SEVERE, "Error running finishing handler.", e);
        }
      }
    }
  }

  /**
   * Returns how many jobs had some IO operations.
   * IO operations.
   * @return 
   */
  protected abstract int runSinglePass();

  /**
   * @return the singlePassDelay
   */
  public long getSinglePassDelay() {
    return singlePassDelay;
  }

  /**
   * @param singlePassDelay the singlePassDelay to set
   */
  public synchronized void setSinglePassDelay(long singlePassDelay) {
    this.singlePassDelay = singlePassDelay;
  }

  /**
   * @return the server
   */
  public abstract Server getServer();

  /**
   * @return the delayForNoIO
   */
  public long getDelayForNoIO() {
    return delayForNoIO;
  }

  /**
   * @param delayForNoIO the delayForNoIO to set
   */
  public void setDelayForNoIO(long delayForNoIO) {
    this.delayForNoIO = delayForNoIO;
  }
  
  protected void onJobFinished(DataHandler dataHandler) {
    dataHandler.connectionClosedHandler();
  }

  /**
   * @return the running
   */
  public boolean isRunning() {
    return running;
  }

  /**
   * @param started the running to set
   */
  public synchronized void setRunning(boolean started) {
    this.running = started;
  }
}
