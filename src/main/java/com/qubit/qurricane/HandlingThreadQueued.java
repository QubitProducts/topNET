/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.nio.ByteBuffer;
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
          int jobsSize, int bufSize,
          int defaultMaxMessageSize, long maxIdle) {
    this.server = server;
    limit = jobsSize + 1;
    this.setBuffer(ByteBuffer.allocateDirect(bufSize));
    this.setDefaultMaxMessageSize(defaultMaxMessageSize);
    this.maxIdle = maxIdle;
  }

  @Override
  public int runSinglePass() {
    DataHandler job;
    int totalWroteRead = 0;
    
    while ((job = this.getJobs().pollFirst()) != null) {

      boolean isFinished = true;

      try {

        //check if connection is not open too long! Prevent DDoS
        if (this.handleMaxIdle(job, maxIdle)) {
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
        } else {
          // keep job
          totalWroteRead += processed;
          isFinished = false;
        }

      } catch (Exception es) {
        log.log(Level.SEVERE, "Exception during handling data.", es);
        isFinished = true;
      } finally {
        if (!isFinished) { // will be closed
          this.getJobs().addLast(job); // put back to queue
        } else {
          Server.close(job.getChannel());
          this.onJobFinished(job);
        }
      }

        // *** PROCESSING BLOCK
      try {
        // added last is the last
        if (job == getJobs().getLast()) {
          break; // take some rest!
        }
      } catch (NoSuchElementException ne) {
        // can occur when multi threaded, very rare
      }
    }

    return totalWroteRead;
  }

  /**
   *
   * @param key
   * @return
   */
  @Override
  public boolean addJob(DataHandler dataHandler) {
    if (limit > 0 && this.getJobs().size() < limit) { // @todo getSize is not good
      boolean added = this.getJobs().add(dataHandler);
      if (added) {
        synchronized (this) {
          this.notify();
        }
      }
      return added;
    }
    return false;
  }

  @Override
  protected boolean hasJobs() {
    return !this.getJobs().isEmpty();
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

  @Override
  boolean canAddJob() {
    if (limit > this.getJobs().size()) {
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
}
