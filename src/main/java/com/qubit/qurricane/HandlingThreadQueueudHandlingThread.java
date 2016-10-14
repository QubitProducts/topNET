/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

//import static com.qubit.qurricane.MainAcceptAndDispatchThread.MSG_TOUT;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentLinkedDeque;
//import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThreadQueueud extends HandlingThread {

  private final ConcurrentLinkedDeque<SelectionKey> jobs;
  private final long maxIdle;
  private int limit = 16;

  static final Logger log = Logger.getLogger(
          HandlingThreadQueueud.class.getName());

  public HandlingThreadQueueud(
          int jobsSize, int bufSize, int defaultMaxMessageSize, long maxIdle) {
    jobs = new ConcurrentLinkedDeque<>();
    limit = jobsSize + 1;
    this.setBuffer(ByteBuffer.allocate(bufSize));
    this.setDefaultMaxMessageSize(defaultMaxMessageSize);
    this.maxIdle = maxIdle;
  }

  @Override
  public void run() {

    try {

      while (MainAcceptAndDispatchThread.keepRunning) {

        SelectionKey job;
        while ((job = this.jobs.pollFirst()) != null) {

          boolean isFinished = true;
          DataHandler dataHandler = (DataHandler) job.attachment();

          try {
            // important step! skip those busy
            if (dataHandler != null) {

              //check if connection is not open too long! Prevent DDoS
              if (this.handleMaxIdle(dataHandler, job, maxIdle)) {
                continue;
              }

              if (this.processKey(job, dataHandler)) {
                // job not necessary anymore
                // isFinished = true;
              } else {
                // keep job
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
              this.jobs.addLast(job); // put back to queue
            }
          }
        }

        try {

          if (!this.hasJobs()) {
            synchronized (this) {
              this.wait(500);
            }
          }
        } catch (InterruptedException ex) {
          log.log(Level.SEVERE, null, ex);
        }
      }

    } finally {
      MainAcceptAndDispatchThread.removeThread(this);
    }
  }

  /**
   *
   * @param key
   * @return
   */
  @Override
  public boolean addJob(DataHandler dataHandler, SelectionKey key) {
    if (limit > 0 && this.jobs.size() < limit) {
      dataHandler.locked = true; //single thread is deciding on this
      boolean added = this.jobs.add(key);
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

  @Override
  boolean canAddJob() {
    if (limit > this.jobs.size()) {
      return true;
    }
    return false;
  }
}
