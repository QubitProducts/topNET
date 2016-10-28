/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class HandlingThreadSharedQueue extends HandlingThreadQueued {

  private final static ConcurrentLinkedDeque<DataHandler> jobs = 
          new ConcurrentLinkedDeque<>();
  
  public HandlingThreadSharedQueue(
          Server server,
          int jobsSize, int bufSize,
          int defaultMaxMessageSize, long maxIdle) {
    super(server, jobsSize, bufSize, defaultMaxMessageSize, maxIdle);
    syncPlease = true;
  }

  @Override
  protected ConcurrentLinkedDeque<DataHandler> getJobs() {
    return HandlingThreadSharedQueue.jobs;
  }
}
