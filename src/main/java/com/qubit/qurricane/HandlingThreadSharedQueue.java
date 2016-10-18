/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class HandlingThreadSharedQueue extends HandlingThreadQueued {

  private final static ConcurrentLinkedDeque<SelectionKey> jobs = 
          new ConcurrentLinkedDeque<>();
  
  public HandlingThreadSharedQueue(
          int jobsSize, int bufSize,
          int defaultMaxMessageSize, long maxIdle) {
    super(jobsSize, bufSize, defaultMaxMessageSize, maxIdle);
  }

  @Override
  public ConcurrentLinkedDeque<SelectionKey> getJobs() {
    return HandlingThreadSharedQueue.jobs;
  }
}
