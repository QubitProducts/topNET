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
          synchronized (this) {
            jobcounter--;
          }
          Server.close(job.getChannel());
          this.onJobFinished(job);
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
  public boolean addJob(DataHandler dataHandler) {
    if (limit > 0 && jobcounter < limit) { // @todo getSize is not good
      synchronized (this) { 
        this.jobs.addLast(dataHandler);
        jobcounter++;
        this.notify();
      }
      return true;
    }
    return false;
  }

  @Override
  protected boolean hasJobs() {
    return !this.jobs.isEmpty();
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
  
  private int jobcounter = 0;
  
  @Override
  boolean canAddJob() {
    if (limit > jobcounter) {
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
