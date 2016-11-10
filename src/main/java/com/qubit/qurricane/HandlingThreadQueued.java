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

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThreadQueued extends HandlingThread {

  private final ConcurrentLinkedDeque<DataHandler> jobs
          = new ConcurrentLinkedDeque<>();
  private final long maxIdle;
  private int limit = 16;
  private final Server server;

  static final Logger log = Logger.getLogger(
          HandlingThreadQueued.class.getName());
  boolean syncPlease = false;

  public HandlingThreadQueued(
          Server server,
          int jobsSize,
          int bufSize,
          int defaultMaxMessageSize,
          long maxIdle) {
    this.server = server;
    limit = jobsSize;
    this.setDefaultMaxMessageSize(defaultMaxMessageSize);
    this.maxIdle = maxIdle;
  }

  @Override
  public int runSinglePass() {
    DataHandler job;
    int ifIOOccuredCount = 0;
    
    while ((job = this.jobs.pollFirst()) != null) {

      boolean isFinished = true;

      try {

        //check if connection is not open too long! Prevent DDoS
        if (this.handleMaxIdle(job, maxIdle)) {
          ifIOOccuredCount += 1;
          continue;
        }

        int processed;
        
        if (this.syncPlease) {
          synchronized (job) {
            processed = this.processJob(job);
          }
        } else {
          processed = this.processJob(job);
        }

        if (processed < 0) {
          // job not necessary anymore
          // isFinished = true;
          ifIOOccuredCount += 1; // job finished, io occured
        } else {
          // keep job
          if (processed > 0) {
            ifIOOccuredCount += 1;
          }
          isFinished = false;
        }

      } catch (Exception es) {
        log.log(Level.SEVERE, "Exception during handling data.", es);
        isFinished = true;
      } finally {
        if (!isFinished) { // will be closed
          this.jobs.addLast(job); // put back to queue
        } else {
          Server.close(job.getChannel());
          this.onJobFinished(job);
          jobsRemoved++;
          if (server.isCachingBuffers()) {
            this.recycledJobs.addLast(job);
          }
        }
      }

        // *** PROCESSING BLOCK
      try {
        // added last is the last
        if (job == jobs.getLast()) {
          break; // take some rest!
        }
      } catch (NoSuchElementException ne) {
        // can occur when multi threaded, very rare
      }
    }

    return ifIOOccuredCount;
  }

  /**
   *
   * @param key
   * @return
   */
  @Override
  public boolean addJob(SocketChannel channel, Long ts) {
    if (limit < 0 || this.jobsLeft() < limit) { // @todo getSize is not good
      DataHandler job = this.getNewJob(channel);
      job.owningThread = this;
      job.setAcceptAndRunHandleStarted(ts);
      this.jobs.addLast(job);
      jobsAdded++;
      synchronized (sleepingLocker) {
        sleepingLocker.notify();
      }
      return true;
    }
    synchronized (sleepingLocker) {
      sleepingLocker.notify();
    }
    return false;
  }

  @Override
  public boolean hasJobs() {
    return this.jobsLeft() > 0;
  }

  /**
   * @return the limit
   */
  public int getLimit() {
    return limit;
  }

  /**
   * @param limit the limit to set
   */
  public void setLimit(int limit) {
    this.limit = limit;
  }
  
  volatile long jobsAdded = 0;
  volatile long jobsRemoved = 0;
  
  @Override
  boolean canAddJob() {
    if (limit < 0 || limit > this.jobsLeft()) {
      return true;
    }
    return false;
  }

  /**
   * @return the jobs
   */
  protected ConcurrentLinkedDeque<DataHandler> getJobs() {
    return jobs;
  }

  /**
   * @return the server
   */
  public Server getServer() {
    return server;
  }
  
  @Override
  public List<DataHandler> getValidJobs() {
    List<DataHandler> tmp = new ArrayList<>();
    for (DataHandler job : jobs.toArray(new DataHandler[]{})) {
      tmp.add(job);
    }
    return tmp;
  }

  private ConcurrentLinkedDeque<DataHandler> recycledJobs = 
      new ConcurrentLinkedDeque<>();
  
  private DataHandler getNewJob(SocketChannel channel) {
    
    if (!server.isCachingBuffers()) {
      return new DataHandler(server, channel);
    }
    
    DataHandler job = recycledJobs.pollFirst();
    if (job != null) {
      job.reset();
      job.init(server, channel);
      return job;
    } else {
      return new DataHandler(server, channel);
    }
  }

  private long jobsLeft() {
    if (jobsAdded < 0) {
      if (jobsRemoved > 0) {
        return (Long.MAX_VALUE - jobsRemoved) + (jobsAdded - Long.MIN_VALUE);
      } else {
        return jobsAdded - jobsRemoved;
      }
    } else {
      return jobsAdded - jobsRemoved;
    }
  }
}
