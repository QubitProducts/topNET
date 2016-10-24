/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

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
  private volatile long delayForNoIO = 1;
  
  abstract boolean addJob(DataHandler dataHandler);

  abstract boolean canAddJob();
  
  private volatile long singlePassDelay = 0;
  
  private static volatile long closedIdleCounter = 0;
  
  protected boolean handleMaxIdle(
          DataHandler dataHandler, long maxIdle) {
    //check if connection is not open too long! Prevent DDoS
    long idle = dataHandler.getMaxIdle(maxIdle);
    if (idle != 0 && (System.currentTimeMillis() - dataHandler.getTouch()) > idle) {
      log.log(Level.INFO,
              "Max idle gained - closing, total: {0}", ++closedIdleCounter);
      Server.close(dataHandler.getChannel()); // just close - timedout
      return true;
    }

    // check if not too large
    int maxSize = dataHandler
            .getMaxMessageSize(getDefaultMaxMessageSize());

    if (maxSize != -1 && dataHandler.getSize() >= maxSize) {
      log.log(Level.INFO, "Max size reached - closing: {0}",
              dataHandler.getSize());
      Server.close(dataHandler.getChannel());
      return true;
    }

    return false;
  }

  @Override
  public void run() {
    try {

      while (this.getServer().isServerRunning()) {
        boolean noIOOccured = false;
        
        while (this.hasJobs()) {
          if (this.runSinglePass() == 0) {
            noIOOccured = true;
          }
          this.takeSomeBreak();
        }

        try {
          if (!this.hasJobs()) {
            synchronized (this) {
              this.wait(500);
            }
          } else {
            if (noIOOccured) {
              this.waitForSomethingToIO();
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
   * "this.closeIfNecessaryAndTellIfShouldReleaseJob(...)" when finished. It
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
        if (this.closeIfNecessaryAndTellIfShouldReleaseJob(
                channel, dataHandler, true)) {
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
   * @param key
   * @param dataHandler
   * @return true only if key should be released
   * @throws IOException
   */
  protected int processKey(DataHandler dataHandler)
          throws IOException {
    SocketChannel channel = dataHandler.getChannel();
    if (channel.finishConnect()) {
      if (dataHandler.writingResponse) { // in progress of writing
        return this.writeResponse(channel, dataHandler);
      } else {
        int many = dataHandler.read(channel, getBuffer());
        
        if (many < 0) { // reading is over
          if (many == -2) {// finished reading correctly, otherwise many is -1
            dataHandler.writingResponse = true; // started writing
            // writingResponse will be unchecked by writeResponse(...)
            return this.writeResponse(channel, dataHandler);
          } else if (many == -1) {
            // end of stream reached! this is an error normally.
            Server.close(channel);
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
  protected boolean closeIfNecessaryAndTellIfShouldReleaseJob(
          SocketChannel channel,
          DataHandler dataHandler,
          boolean finishedWriting) {
    if (dataHandler.canClose(finishedWriting)) {
      Server.close(channel);
      return true;
    } else {
      dataHandler.reset();
      return false;
    }
  }
  
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
   * @return the buffer
   */
  public ByteBuffer getBuffer() {
    return buffer;
  }

  /**
   * @param buffer the buffer to set
   */
  public void setBuffer(ByteBuffer buffer) {
    this.buffer = buffer;
  }
  
  protected abstract boolean hasJobs();
  
  public static volatile long totalWaitedIO = 0;
  
  protected void waitForSomethingToIO() {
    if (this.getSinglePassDelay() > 0) {
      synchronized (this) {
        try {
          long timeToWait =
              (long)((Math.random() * this.getDelayForNoIO()) + 0.5);
          totalWaitedIO += timeToWait;
          this.wait(timeToWait);
        } catch (InterruptedException ex) {
          log.log(Level.FINE, "waitForSomethingToIO delay interrupted.", ex);
        }
      }
    }
  }

  protected void takeSomeBreak() {
    if (this.getSinglePassDelay() > 0) {
      synchronized (this) {
        try {
          this.wait((long)((Math.random() * this.getSinglePassDelay()) + 0.5));
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
  public void setSinglePassDelay(long singlePassDelay) {
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
  }
}
