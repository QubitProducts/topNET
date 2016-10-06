/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThread extends Thread {

  private AtomicReferenceArray<SelectionKey> jobs;
  private final ByteBuffer buffer;
  private final int defaultMaxMessageSize;
  private final int maxIdle;
  
  static final Logger log = Logger.getLogger(HandlingThread.class.getName());

  public HandlingThread(
          int jobsSize, int bufSize, int defaultMaxMessageSize, int maxIdle) {
    jobs = new AtomicReferenceArray<>(jobsSize);
    buffer = ByteBuffer.allocate(bufSize);
    this.defaultMaxMessageSize = defaultMaxMessageSize;
    this.maxIdle = maxIdle;
  }

  @Override
  public void run() {

    try {

      while (MainAcceptAndDispatchThread.keepRunning) {

        while (this.hasJobs()) {

          for (int i = 0; i < this.jobs.length(); i++) {
            SelectionKey job = this.jobs.get(i);

            if (job != null) {
              boolean remove = true;
              DataHandler dataHandler = (DataHandler) job.attachment();

              try {
                // important step! skip those busy
                if (dataHandler != null) {

                  //check if connection is not open too long! Prevent DDoS
                  long idle = System.currentTimeMillis() - dataHandler.getTouch();
                  if (idle > dataHandler.getMaxIdle(maxIdle)) {
                    log.log(Level.INFO, "Max idle gained - closing: {0}", idle);
                    Server.close(job); // just close - timedout
                    continue;
                  }

                  // check if not too large
                  int maxSize = dataHandler
                          .getMaxMessageSize(defaultMaxMessageSize);

                  if (maxSize != -1 && dataHandler.getSize() >= maxSize) {
                    log.log(Level.INFO, "Max size reached - closing: {0}",
                            dataHandler.getSize());
                    Server.close(job);
                    continue;
                  }

                  if (this.processKey(job, dataHandler)) {
                    // job not necessary anymore
                    // remove = true;
                  } else {
                    // keep job
                    remove = false;
                  }
                }
              } catch (Exception es) {
                log.log(Level.SEVERE, "Exception during handling data.", es);
                remove = true;
                Server.close(job);
              } finally {
                if (remove) {
                  this.removeJobFromPool(i, dataHandler);
                }
              }
            }
          }
        }

        try {
          synchronized (this) {
            if (!this.hasJobs()) {
              this.wait(100);
            }
          }
        } catch (InterruptedException ex) {
          log.log(Level.SEVERE, null, ex);
        }
      }

    } finally {
      MainAcceptAndDispatchThread.removeThread(this);
    }
  }

  /**
   * 
   * @param key
   * @param dataHandler
   * @return true only is key should be released
   * @throws IOException 
   */
  private boolean processKey(SelectionKey key, DataHandler dataHandler)
          throws IOException {
    if (key.isValid()) {
      try {
        if (dataHandler.writingResponse) { // in progress of writing
          return this.writeResponse(key, dataHandler);
        } else {
          try {
            if (key.isReadable()) {
              int many = dataHandler.read(key, buffer);
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
            log.fine("Key already closed.");
            return true;
          }
        }
      } catch (IOException ex) {
        log.log(Level.SEVERE, null, ex);
        return true;
      }
    } else {
      return true;
    }
  }

  private boolean
          closeIfNecessaryAndTellIfShouldReleaseJob(
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
   *
   * @param key
   * @return
   */
  public boolean addJob(SelectionKey key) {
    for (int i = 0; i < this.jobs.length(); i++) {
      SelectionKey job = this.jobs.get(i);
      if (job == null) {
        this.jobs.set(i, key);
        return true;
      }
    }

    return false;
  }

  private boolean hasJobs() {
    for (int i = 0; i < this.jobs.length(); i++) {
      if (this.jobs.get(i) != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns false if not finished writing or 
   * "this.closeIfNecessaryAndTellIfShouldReleaseJob(...)" 
   * when finished. It tells if job can be released.
   * @param key
   * @param dataHandler
   * @return
   * @throws IOException 
   */
  private boolean writeResponse(SelectionKey key, DataHandler dataHandler)
          throws IOException {
    if (dataHandler.write(key, buffer)) {
      dataHandler.writingResponse = false; // finished writing
      return this.closeIfNecessaryAndTellIfShouldReleaseJob(
                                                  key, dataHandler, true);
    }
    return false;
  }

  private void removeJobFromPool(int i, DataHandler dataHandler) {
    this.jobs.set(i, null);

    if (dataHandler != null) {
      dataHandler.locked = false;
    }
  }

}
