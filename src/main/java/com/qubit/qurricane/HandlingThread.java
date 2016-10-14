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
  
  abstract boolean addJob(DataHandler dataHandler, SelectionKey key);

  abstract boolean canAddJob();
  
  protected boolean handleMaxIdle(DataHandler dataHandler, SelectionKey job, long maxIdle) {
    //check if connection is not open too long! Prevent DDoS
    long idle = dataHandler.getMaxIdle(maxIdle);
    if (idle != 0 && (System.currentTimeMillis() - dataHandler.getTouch()) > idle) {
      log.log(Level.INFO, "Max idle gained - closing: {0}", idle);
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
  private boolean writeResponse(SelectionKey key, DataHandler dataHandler)
          throws IOException {
    int written = dataHandler.write(key);
    if (written < 0) {
      dataHandler.writingResponse = false; // finished writing
      if (written == -1) {
        return this.closeIfNecessaryAndTellIfShouldReleaseJob(
                key, dataHandler, true);
      }
    }
    return false;
  }
  
  /**
   *
   * @param key
   * @param dataHandler
   * @return true only if key should be released
   * @throws IOException
   */
  protected boolean processKey(SelectionKey key, DataHandler dataHandler)
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
              return true;
            } else {
              return false;
            }
          } else {
            return true;
          }
        } catch (CancelledKeyException ex) {
          log.info("Key already closed.");
          Server.close(key);
          return true;
        }
      }
    } else {
      return true;
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
}
