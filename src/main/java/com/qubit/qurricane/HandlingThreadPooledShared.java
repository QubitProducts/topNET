/*
 * Qurrican
 * Fast HTTP Server Solution.
 * Copyright 2016, Qubit Group <www.qubit.com>
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

import java.nio.channels.ClosedChannelException;
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
          if (dataHandler.atomicRefToHandlingThread.get() != this) {
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
              
              int processed = this.processJob(dataHandler);

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
          } catch (ClosedChannelException e) {
            log.info("Closed early connection.");
          } catch (Exception es) {
            log.log(Level.SEVERE, "Exception during handling data.", es);
            isFinished = true;
          } finally {
            if (isFinished) {
              //key cancell!
              try {
                try {
                  this.removeJobFromPool(i);
                } catch (IllegalMonitorStateException e) {}
              } finally {
                Server.close(channel);
                this.onJobFinished(dataHandler);
              }
            }
          // PROCESSING BLOCK ***
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
  public boolean addJob(SocketChannel channel) {
    for (int i = 0; i < this.jobs.length; i++) {
      DataHandler job = this.jobs[i].dataHandler;
      if (job == null) {
        job = new DataHandler(server, channel);
        job.initLock();
        if (job.atomicRefToHandlingThread.compareAndSet(null, this)) {
          this.jobs[i].dataHandler = job;
          job.owningThread = this;
          job.startedAnyHandler();
          synchronized (sleepingLocker) {
            sleepingLocker.notify();
          }
          return true;
        }
      } else if (job.owningThread == null) {
        job.owningThread = this;
        job.init(server, channel);
        job.startedAnyHandler();
        synchronized (sleepingLocker) {
          sleepingLocker.notify();
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasJobs() {
    for (int i = 0; i < this.jobs.length; i++) {
      if (this.jobs[i].dataHandler != null &&
          this.jobs[i].dataHandler.owningThread != null &&
          this.jobs[i].dataHandler.atomicRefToHandlingThread.get() == this) {
        return true;
      }
    }
    return false;
  }

  private void removeJobFromPool(int i) {
    this.jobs[i].dataHandler.atomicRefToHandlingThread.compareAndSet(this, null);
    this.jobs[i].dataHandler.owningThread = null;
  }

  @Override
  boolean canAddJob() {
    for (int i = 0; i < this.jobs.length; i++) {
      if (this.jobs[i].dataHandler == null
          || this.jobs[i].dataHandler.owningThread == null) {
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
