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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThreadPooled extends HandlingThread {

  private final DataHandlerHolder[] jobs;
  
  private final Server server;
  
  private final long maxIdle;

  static final Logger log
          = Logger.getLogger(HandlingThreadPooled.class.getName());

  public HandlingThreadPooled(
          Server server,
          int jobsSize,
          int bufSize,
          int defaultMaxMessageSize,
          long maxIdle) {
    this.server = server;
    jobs = new DataHandlerHolder[jobsSize];
    for (int i = 0; i < jobs.length; i++) {
      jobs[i] = new DataHandlerHolder();
    }
    
    this.setDefaultMaxMessageSize(defaultMaxMessageSize);
    this.maxIdle = maxIdle;
  }

  @Override
  public int runSinglePass() {
    int ifIOOccuredCount = 0;
    
    for (int i = 0; i < this.jobs.length; i++) {
      DataHandler dataHandler = this.jobs[i].dataHandler;

      if (dataHandler != null && dataHandler.owningThread == this) {
        boolean isFinished = true;
        SocketChannel channel = dataHandler.getChannel();

        try {
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
        } catch (ClosedChannelException e) {
          log.info("Closed early connection.");
        } catch (Exception es) {
          log.log(Level.SEVERE, "Exception during handling data.", es);
          isFinished = true;
        } finally {
          if (isFinished) {
            //key cancell!
            try {
              ifIOOccuredCount++;
              Server.close(channel);
              this.onJobFinished(dataHandler);
            } finally {
              this.removeJobFromPool(i);
            }
          }
        }
      }
    }
    
    return ifIOOccuredCount;
  }

  AtomicInteger jobsCounter = new AtomicInteger(0);
  
  @Override
  public boolean addJob(SocketChannel channel) {
    for (int i = 0; i < this.jobs.length; i++) {
      DataHandler job = this.jobs[i].dataHandler;
      if (job == null) {
        job = new DataHandler(server, channel);
        jobsCounter.incrementAndGet();
        job.owningThread = this;
        job.startedAnyHandler();
        this.jobs[i].dataHandler = job;
        synchronized (sleepingLocker) {
          sleepingLocker.notify();
        }
        return true;
      } else if (job.owningThread == null) {
        jobsCounter.incrementAndGet();
        job.init(server, channel);
        job.owningThread = this;
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
    return jobsCounter.get() > 0;
//    for (int i = 0; i < this.jobs.length; i++) {
//      if (this.jobs[i].dataHandler != null &&
//          this.jobs[i].dataHandler.owningThread != null) {
//        return true;
//      }
//    }
//    return false;
  }


  private void removeJobFromPool(int i) {
//    this.jobs[i].dataHandler = null;
    this.jobs[i].dataHandler.owningThread = null;
    jobsCounter.decrementAndGet();
  }

  @Override
  boolean canAddJob() {
    return jobsCounter.get() < this.jobs.length;
//    
//    for (int i = 0; i < this.jobs.length; i++) {
//      if (this.jobs[i].dataHandler == null ||
//          this.jobs[i].dataHandler.owningThread == null) {
//        return true;
//      }
//    }
//    return false;
  }

  /**
   * @return the server
   */
  public Server getServer() {
    return server;
  }
}
