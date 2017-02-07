/*
 * topNET
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
package com.qubit.topnet.eventonly;

import com.qubit.topnet.DataHandler;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
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

  private static final Logger log = Logger.getLogger(
      HandlingThreadQueued.class.getName());
  boolean syncPlease = false;

  public HandlingThreadQueued(
      EventTypeServer server,
      int jobsSize,
      int bufSize,
      int defaultMaxMessageSize,
      long maxIdle) {
    super(server);
    this.limit = jobsSize;
    this.setDefaultMaxMessageSize(defaultMaxMessageSize);
    this.maxIdle = maxIdle;
  }

  @Override
  public boolean runSinglePass() {
    boolean waitForIO = true;
    DataHandler job;

    while ((job = this.jobs.pollFirst()) != null) {

      boolean isFinished = true;

      try {

        //check if connection is not open too long! Prevent DDoS
        if (this.handleMaxIdle(job, maxIdle)) {
          waitForIO = false;
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
          waitForIO = false;
          // job not necessary anymore
        } else {
          // keep job
          if (processed > 0) {
            waitForIO = false;
          } else {
            // == 0 means no reads were done
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
          EventTypeServer.close(job.getSelectionKey(), job.getChannel());
          job.connectionClosedHandler();
          jobsRemoved++;
          if (getServer().isCachingBuffers()) {
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

    return waitForIO;
  }

  /**
   *
   * @param key
   * @return
   */
  @Override
  public boolean addJob(SelectionKey key, Long ts) {
    if (getLimit() < 0 || this.jobsLeft() < getLimit()) { // @todo getSize is not good
      DataHandler job = this.getNewJob(key);
      job.owningThread = this;
      job.setAcceptAndRunHandleStarted(ts);
      this.jobs.addLast(job);
      jobsAdded++;

      wakeup();

      return true;
    }

    wakeup();

    return false;
  }

  @Override
  public boolean hasJobs() {
    return this.jobsLeft() > 0;
  }

  /**
   * @return the limit
   */
  @Override
  public int getLimit() {
    return limit;
  }

  /**
   * @param limit the limit to set
   */
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Override
  boolean canAddJob() {
    return getLimit() < 0 || getLimit() > this.jobsLeft();
  }

  /**
   * @return the jobs
   */
  protected ConcurrentLinkedDeque<DataHandler> getJobs() {
    return jobs;
  }

  @Override
  public List<DataHandler> getValidJobs() {
    return Arrays.asList(jobs.toArray(new DataHandler[]{}));
  }

  private ConcurrentLinkedDeque<DataHandler> recycledJobs
      = new ConcurrentLinkedDeque<>();

  private DataHandler getNewJob(SelectionKey key) {

    if (!this.getServer().isCachingBuffers()) {
      return new DataHandler(getServer(), key);
    }

    DataHandler job = this.recycledJobs.pollFirst();
    if (job != null) {
      job.reset();
      job.init(this.getServer(), key);
      return job;
    } else {
      return new DataHandler(this.getServer(), key);
    }
  }
}
