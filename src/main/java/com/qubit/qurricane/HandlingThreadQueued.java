/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThreadQueued extends HandlingThread {

  private final ConcurrentLinkedDeque<SelectionKey> jobs = 
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
    SelectionKey job;
    int totalWroteRead = 0;
    while ((job = this.getJobs().pollFirst()) != null) {

      boolean isFinished = true;
      DataHandler dataHandler = (DataHandler) job.attachment();

      try {
        // important step! skip those busy
        if (dataHandler != null) {

          //check if connection is not open too long! Prevent DDoS
          if (this.handleMaxIdle(dataHandler, job, maxIdle)) {
            continue;
          }
          
          int processed = this.processKey(job, dataHandler);
          
          if (processed < 0) {
                // job not necessary anymore
            // isFinished = true;
          } else {
            // keep job
            totalWroteRead += processed;
            isFinished = false;
          }
        }
      } catch (Exception es) {
        log.log(Level.SEVERE, "Exception during handling data.", es);
        isFinished = true;
        Server.close(job);
      } finally {
        if (isFinished) { // will be closed
          if (dataHandler != null) {
            dataHandler.locked = false;
          }
        } else {
          this.getJobs().addLast(job); // put back to queue
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
  public boolean addJob(DataHandler dataHandler, SelectionKey key) {
    if (limit > 0 && this.getJobs().size() < limit) {
      dataHandler.locked = true; //single thread is deciding on this
      boolean added = this.getJobs().add(key);
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
  public ConcurrentLinkedDeque<SelectionKey> getJobs() {
    return jobs;
  }

  /**
   * @return the server
   */
  public Server getServer() {
    return server;
  }
}
