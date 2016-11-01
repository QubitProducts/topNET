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
    
    this.setBuffer(ByteBuffer.allocateDirect(bufSize));
    this.setDefaultMaxMessageSize(defaultMaxMessageSize);
    this.maxIdle = maxIdle;
  }

  @Override
  public int runSinglePass() {
    int ifIOOccuredCount = 0;

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
                ifIOOccuredCount += 1;
                continue;
              }
              
              int processed = 0;
              
              synchronized (dataHandler) {
                // -2 close, -1 hold
                processed = this.processJob(dataHandler);
              }

              if (processed < 0) {
                ifIOOccuredCount += 1; // job finished, io occured
                // job not necessary anymore
              } else {
                // keep job
                if (processed > 0) {
                  ifIOOccuredCount += 1;
                }
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
              } finally {
                Server.close(channel);
                this.onJobFinished(dataHandler);
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

    return ifIOOccuredCount;
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
