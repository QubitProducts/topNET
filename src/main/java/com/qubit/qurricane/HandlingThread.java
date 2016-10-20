/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import static com.qubit.qurricane.HandlingThreadPooled.log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
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
  
  abstract boolean addJob(DataHandler dataHandler, SelectionKey key);

  abstract boolean canAddJob();
  
  private volatile long singlePassDelay = 0;
  
  private static volatile long closedIdleCounter = 0;
  
  protected boolean handleMaxIdle(
          DataHandler dataHandler, SelectionKey job, long maxIdle) {
    //check if connection is not open too long! Prevent DDoS
    long idle = dataHandler.getMaxIdle(maxIdle);
    if (idle != 0 && (System.currentTimeMillis() - dataHandler.getTouch()) > idle) {
      log.log(Level.INFO,
              "Max idle gained - closing, total: {0}", ++closedIdleCounter);
      Server.close(job); // just close - timedout
      return true;
    }

    // check if not too large
    int maxSize = dataHandler
            .getMaxMessageSize(getDefaultMaxMessageSize());

    if (maxSize != -1 && dataHandler.getSize() >= maxSize) {
      log.log(Level.INFO, "Max size reached - closing: {0}",
              dataHandler.getSize());
      Server.close(job);
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
          this.takeSomeBreak();
          if (this.runSinglePass() == 0) {
            noIOOccured = true;
          }
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
  private int writeResponse(SelectionKey key, DataHandler dataHandler)
          throws IOException {
    int written = dataHandler.write(key);
    if (written < 0) {
      dataHandler.writingResponse = false; // finished writing
      if (written == -1) {
        this.runOnFinishedHandler(dataHandler);
        if (this.closeIfNecessaryAndTellIfShouldReleaseJob(
                key, dataHandler, true)) {
          return -2;
        } else {
          return -1;
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
  protected int processKey(SelectionKey key, DataHandler dataHandler)
          throws IOException {
    if (key.isValid()) {
      if (dataHandler.writingResponse) { // in progress of writing
        return this.writeResponse(key, dataHandler);
      } else {
        try {
          if (key.isReadable()) {
            int many = dataHandler.read(key, getBuffer());
            if (many < 0) { // finished reading
              if (many == -2) {
                dataHandler.writingResponse = true; // started writing
                // writingResponse will be unchecked by writeResponse(...)
                return this.writeResponse(key, dataHandler);
              }
              // connection is closed - just close socket
              if (many == -1) {
                Server.close(key);
              }
              
              return -1;
            } else {
              return many;
            }
          } else {
            return -1;
          }
        } catch (CancelledKeyException ex) {
          log.info("Key already closed.");
          Server.close(key);
          return -1;
        }
      }
    } else {
      return -1;
    }
  }
  
  protected boolean closeIfNecessaryAndTellIfShouldReleaseJob(
          SelectionKey key,
          DataHandler dataHandler,
          boolean finishedWriting) {
    if (dataHandler.canClose(finishedWriting)) {
      Server.close(key);
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
  
  protected void waitForSomethingToIO() {
    if (this.getSinglePassDelay() > 0) {
      synchronized (this) {
        try {
          this.wait((long)((Math.random() * this.getDelayForNoIO()) + 0.5));
        } catch (InterruptedException ex) {
          log.log(Level.SEVERE, "Single pass delay interrupted.", ex);
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
          log.log(Level.SEVERE, "Single pass delay interrupted.", ex);
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
}
