/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.io.IOException;
import java.nio.ByteBuffer;
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
                  if ((System.currentTimeMillis() - dataHandler.getTouch())
                          > dataHandler.getMaxIdle(maxIdle)) {
                    Server.close(job); // jiust close - timedout
                  }

                  // check if not too large
                  int maxSize = dataHandler
                          .getMaxMessageSize(defaultMaxMessageSize);

                  if (maxSize != -1 && dataHandler.getSize() >= maxSize) {
                    Server.close(job);
                  }

                  if (this.processKey(job, dataHandler)) {
                    // key not necessary anymore
                    // remove = true;
                  } else {
                    remove = false;
                  }
                }
              } catch (Exception es) {
                log.log(Level.SEVERE, "Excpetion during handling data.", es);
                // @todo metrics
                this.jobs.set(i, null);

                if (dataHandler != null) {
                  dataHandler.locked = false;
                }

                Server.close(job);
              } finally {
                if (remove) {
                  this.jobs.set(i, null);

                  if (dataHandler != null) {
                    dataHandler.locked = false;
                  }
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

        }
      }

    } finally {
      MainAcceptAndDispatchThread.removeThread(this);
    }
  }

  private boolean processKey(SelectionKey key, DataHandler dataHandler)
          throws IOException {
    if (key.isValid()) {
      try {
        if (dataHandler.writingResponse) {
          if (this.writeResponse(key, dataHandler)) {
            dataHandler.writingResponse = false;
            return true;
          } else {
            return false;
          }
        } else if (key.isReadable()) {
          int many = dataHandler.read(key, buffer);
          if (many < 0) { // finished reading
            if (many == -2) {
              dataHandler.writingResponse = true;
              if (this.writeResponse(key, dataHandler)) {
                return this.closeIfNecessary(key, dataHandler, true);
              } else {
                return false;
              }
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
      } catch (IOException ex) {
        log.log(Level.SEVERE, null, ex);
        return true;
      } finally {
      }
    } else {
      return true;
    }
  }

  private boolean
          closeIfNecessary(SelectionKey key, DataHandler dataHandler, boolean finishedWriting) {
    try {
      if (dataHandler.canClose(finishedWriting)) {
        Server.close(key);
        return true;
      } else {
        dataHandler.reset();
      }
    } finally {
    }

    return false;
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

  private boolean writeResponse(SelectionKey key, DataHandler dataHandler)
          throws IOException {
    if (dataHandler.write(key, buffer)) {
      dataHandler.writingResponse = false;
      return this.closeIfNecessary(key, dataHandler, true);
    }
    return false;
  }

}
