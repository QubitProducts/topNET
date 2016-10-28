/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThreadPooledShared extends HandlingThread {

  private static DataHandlerHolder[] jobs;

  private final Server server;

  private final long maxIdle;

  static final Logger log
          = Logger.getLogger(HandlingThreadPooledShared.class.getName());

  public HandlingThreadPooledShared(
          Server server,
          int jobsSize, int bufSize,
          int defaultMaxMessageSize,
          long maxIdle) {
    this.server = server;
    if (jobs == null) {
      jobs = new DataHandlerHolder[jobsSize];
      for (int i = 0; i < jobs.length; i++) {
        jobs[i] = new DataHandlerHolder();
      }
    }
    
    this.setBuffer(ByteBuffer.allocate(bufSize));
    this.setDefaultMaxMessageSize(defaultMaxMessageSize);
    this.maxIdle = maxIdle;
  }

  @Override
  public int runSinglePass() {
    int totalWroteRead = 0;

    for (int i = 0; i < HandlingThreadPooledShared.jobs.length; i++) {
      DataHandler dataHandler = 
              HandlingThreadPooledShared.jobs[i].dataHandler;

      if (dataHandler != null) {
        try {
          if (dataHandler.atomicRefToHandlingThread == null || 
             !dataHandler.atomicRefToHandlingThread.compareAndSet(null, this)) {
            continue;
            // something else already works on it
          }

          // PROCESSING BLOCK
          boolean isFinished = true;
          SocketChannel channel = dataHandler.getChannel();

          try {
            // important step! skip those busy
            if (channel != null) {

              if (this.handleMaxIdle(dataHandler, maxIdle)) {
                continue;
              }

              // -2 close, -1 hold
              int processed = this.processKey(dataHandler);

              if (processed < 0) {
                // job not necessary anymore
              } else {
                // keep job
                totalWroteRead += processed;
                isFinished = false;
              }
            }
          } catch (Exception es) {
            log.log(Level.SEVERE, "Exception during handling data.", es);
            isFinished = true;
          } finally {
            if (isFinished) {
              //key cancell!
              try {
                this.removeJobFromPool(i);
                this.onJobFinished(dataHandler);
              } finally {
                Server.close(channel);
              }
            }
          }
          // PROCESSING BLOCK ***
        } finally {
          try {
            if (dataHandler.atomicRefToHandlingThread != null) {
              dataHandler.atomicRefToHandlingThread.compareAndSet(this, null);
            }
          } catch (IllegalMonitorStateException e) {
            log.log(Level.SEVERE, null, e);
          }
        }
      }
    }

    return totalWroteRead;
  }

  /**
   * This method should never be used within this object  thread!
 Note that initLock() initialises the atomicRefToHandlingThread on data handler.
   * Make sure it is never run by any handling threads.
   * @param key
   * @return
   */
  @Override
  public boolean addJob(DataHandler dataHandler) {
    for (int i = 0; i < HandlingThreadPooledShared.jobs.length; i++) {
      DataHandler job = HandlingThreadPooledShared.jobs[i].dataHandler;
      if (job == null) {
        dataHandler.initLock();
        HandlingThreadPooledShared.jobs[i].dataHandler = dataHandler;
        synchronized (this) {
          this.notify();
        }
        return true;
      }
    }

    return false;
  }

  @Override
  protected boolean hasJobs() {
    for (int i = 0; i < this.jobs.length; i++) {
      if (HandlingThreadPooledShared.jobs[i].dataHandler != null) {
        return true;
      }
    }
    return false;
  }

  private void removeJobFromPool(int i) {
    this.jobs[i].dataHandler = null;
  }

  @Override
  boolean canAddJob() {
    for (int i = 0; i < this.jobs.length; i++) {
      if (HandlingThreadPooledShared.jobs[i].dataHandler == null) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return the server
   */
  @Override
  public Server getServer() {
    return server;
  }
}
