/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThreadQueued extends HandlingThread {

  private final ConcurrentLinkedDeque<DataHandler> jobs = 
          new ConcurrentLinkedDeque<>();
  private final long maxIdle;
  private int limit = 16;
  private final Server server;

  static final Logger log = Logger.getLogger(
          HandlingThreadQueued.class.getName());

  public HandlingThreadQueued(
          Server server,
          int jobsSize, int bufSize,
          int defaultMaxMessageSize, long maxIdle) {
    this.server = server;
    limit = jobsSize + 1;
    this.setBuffer(ByteBuffer.allocate(bufSize));
    this.setDefaultMaxMessageSize(defaultMaxMessageSize);
    this.maxIdle = maxIdle;
  }

  @Override
  public int runSinglePass() {
    DataHandler job;
    int totalWroteRead = 0;
    while ((job = this.getJobs().pollFirst()) != null) {
     
      // important step! skip those busy
      if (job != null) {
        boolean isFinished = true;
        try {

          //check if connection is not open too long! Prevent DDoS
          if (this.handleMaxIdle(job, maxIdle)) {
            continue;
          }

          int processed = this.processKey(job);

          if (processed < -1) {
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
          Server.close(job.getChannel());
        } finally {
          if (isFinished) { // will be closed
            if (job != null) {
              job.locked = false;
            }
          } else {
            this.getJobs().addLast(job); // put back to queue
          }
        }
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
    if (limit > 0 && this.getJobs().size() < limit) {
      dataHandler.locked = true; //single thread is deciding on this
      boolean added = this.getJobs().add(dataHandler);
      if (added) {
        synchronized (this) {
          this.notify();
        }
      } else {
        dataHandler.locked = false;
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
  public ConcurrentLinkedDeque<DataHandler> getJobs() {
    return jobs;
  }

  /**
   * @return the server
   */
  public Server getServer() {
    return server;
  }
}
