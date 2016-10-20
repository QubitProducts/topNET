/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThreadPooled extends HandlingThread {

  private final AtomicReferenceArray<SelectionKey> jobs;
  
  private final Server server;
  
  private final long maxIdle;

  static final Logger log
          = Logger.getLogger(HandlingThreadPooled.class.getName());

  public HandlingThreadPooled(
          Server server,
          int jobsSize, int bufSize,
          int defaultMaxMessageSize, long maxIdle) {
    this.server = server;
    jobs = new AtomicReferenceArray<>(jobsSize);
    this.setBuffer(ByteBuffer.allocate(bufSize));
    this.setDefaultMaxMessageSize(defaultMaxMessageSize);
    this.maxIdle = maxIdle;
  }

  @Override
  public int runSinglePass() {
    int totalWroteRead = 0;
    
    for (int i = 0; i < this.jobs.length(); i++) {
      SelectionKey job = this.jobs.get(i);

      if (job != null) {
        boolean isFinished = true;
        DataHandler dataHandler = (DataHandler) job.attachment();

        try {
          // important step! skip those busy
          if (dataHandler != null) {

            if (this.handleMaxIdle(dataHandler, job, maxIdle)) {
              continue;
            }
            
            int processed = this.processKey(job, dataHandler);
            
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
          Server.close(job);
        } finally {
          if (isFinished) {
            this.removeJobFromPool(i, dataHandler);
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
  public boolean addJob(DataHandler dataHandler, SelectionKey key) {
    if (!dataHandler.locked) {
      for (int i = 0; i < this.jobs.length(); i++) {
        SelectionKey job = this.jobs.get(i);
        if (job == null) {
          dataHandler.locked = true;
          this.jobs.set(i, key);
          synchronized (this) {
            this.notify();
          }
          return true;
        }
      }
    }

    return false;
  }

  @Override
  protected boolean hasJobs() {
    for (int i = 0; i < this.jobs.length(); i++) {
      if (this.jobs.get(i) != null) {
        return true;
      }
    }
    return false;
  }


  private void removeJobFromPool(int i, DataHandler dataHandler) {
    this.jobs.set(i, null);

    if (dataHandler != null) {
      dataHandler.locked = false;
    }
  }

  @Override
  boolean canAddJob() {
    for (int i = 0; i < this.jobs.length(); i++) {
      if (this.jobs.get(i) == null) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return the server
   */
  public Server getServer() {
    return server;
  }
}
