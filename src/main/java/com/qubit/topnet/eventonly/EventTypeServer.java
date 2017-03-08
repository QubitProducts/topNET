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

import com.qubit.topnet.AbstractHandlingThread;
import com.qubit.topnet.PoolType;
import static com.qubit.topnet.PoolType.POOL;
import com.qubit.topnet.ServerBase;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class EventTypeServer extends ServerBase {

  private final static Logger log = Logger.getLogger(EventTypeServer.class.getName());

  private HandlingThread[] handlingThreads;

  private MainAcceptAndDispatchThread mainAcceptDispatcher;

  public EventTypeServer(String address, int port) {
    super(address, port);
  }

  public void start() throws IOException {

    if (this.isStarted()) {
      log.info("Server already started.");
      return;
    }

    this.setStarted(true);

    this.setServerChannel(ServerSocketChannel.open());
    this.getServerChannel().configureBlocking(false);

    this.setServerSocket(getServerChannel().socket());

    this.getServerSocket().setPerformancePreferences(
        this.getConnectionTimePerformancePref(),
        this.getLatencyPerformancePref(),
        this.getBandwithPerformancePref());

    if (this.getChannelReceiveBufferSize() > 0) {
      this.getServerSocket().setReceiveBufferSize(this.getChannelReceiveBufferSize());
    }

    this.getServerSocket().bind(getListenAddress());

    // @todo move to cfg
    this.setHandlingThreads(new HandlingThread[this.getMinimumThreadsAmount()]);

    this.setChannelSelector(Selector.open());

    getServerChannel().register(getChannelSelector(), SelectionKey.OP_ACCEPT);

    this.mainAcceptDispatcher
        = new MainAcceptAndDispatchThread(
            this,
            getChannelSelector(),
            this.getDefaultAcceptIdleTime());

    log.log(Level.INFO, "Threads handling type used: {0}", this.getPoolType().name());

    this.setupThreadsList();

    mainAcceptDispatcher.setNoSlotsAvailableTimeout(
        this.getNoSlotsAvailableTimeout());

    mainAcceptDispatcher.setScalingDownTryPeriodMS(this.getScalingDownTryPeriodMS());

    mainAcceptDispatcher.setAutoScalingDown(this.isAutoScalingDown());

    mainAcceptDispatcher.start();

    log.log(Level.INFO,
        "Server starting at {0} on port {1}\nPool type: {2}",
        new Object[]{
          getListenAddress().getHostName(),
          this.getPort(),
          this.getPoolType()});
  }

  public void stop() throws IOException {
    if (!this.isStarted()) {
      log.info("Server is not started.");
      return;
    }
    if (this.isStoppingNow()) {
      log.info("Server is being stopped. Please wait.");
      return;
    }

    this.setStoppingNow(true);

    try {
      this.mainAcceptDispatcher.setRunning(false); // help it to finish
      this.mainAcceptDispatcher = null;
      for (int j = 0; j < handlingThreads.length; j++) {
        handlingThreads[j].setRunning(false); // help thread to finish
        handlingThreads[j] = null; // remove thread
      }
      this.clearThreadsCache();
      this.allRegisteringHandlingThreads.clear();
      this.getServerChannel().close();
    } finally {
      this.setStoppingNow(false);
      this.setStarted(false);
    }
  }

  @Override
  public void removeThread(AbstractHandlingThread thread) {
    this.allRegisteringHandlingThreads.remove(thread);
    
    for (int i = 0; i < handlingThreads.length; i++) {
      if (handlingThreads[i] == thread) {
        handlingThreads[i] = null;
      }
    }
    
    for(Iterator<HandlingThread> it = threadsCache.iterator(); it.hasNext();) {
      HandlingThread handlingThread = it.next();
      if (handlingThread.getClass().equals(thread.getClass())) {
        it.remove();
      }
    }
  }

  @Override
  public boolean hasThreads() {
    for (HandlingThread handlingThread : handlingThreads) {
      if (handlingThread != null) {
        return true;
      }
    }
    return false;
  }

  private void setupThreadsList() {
    int len = handlingThreads.length;
    for (int i = 0; i < len; i++) {
      this.addThreadDirectly();
    }
  }

  public boolean addThread() {
    if (this.getScalingMax() > 0
        && handlingThreads.length >= this.getScalingMax()) {
      return false;
    }

    return this.addThreadDirectly();
  }

  private boolean addThreadDirectly() {

    int idx = -1;
    for (int i = 0; i < handlingThreads.length; i++) {
      if (handlingThreads[i] == null) {
        idx = i;
        break;
      }
    }

    if (idx == -1) {
      if (this.isAutoscalingThreads()) {
        HandlingThread[] newArray
            = new HandlingThread[handlingThreads.length + 1];

        System.arraycopy(handlingThreads, 0,
            newArray, 0,
            handlingThreads.length);

        idx = handlingThreads.length;
        // update reference
        this.setHandlingThreads(newArray);
      } else {
        return false;
      }
    }

    handlingThreads[idx] = this.getCachedOrNewThread();

    return true;
  }

  public int cleanupThreadsExcess() {
    int threadsThatShouldBe = this.getMinimumThreadsAmount();
    HandlingThread[] threads = this.handlingThreads;

    if (threadsThatShouldBe >= threads.length) {
      return 0;
    }

    double jobs = 0;
    double max = 0;

    if (this.getPoolType() == PoolType.QUEUE_SHARED) {
      for (HandlingThread thread : threads) {
        if (thread != null) {
          jobs = thread.jobsLeft(); // jobs are same so same value "="
          max += thread.getLimit();
        }
      }
    } else {
      for (HandlingThread thread : threads) {
        if (thread != null) {
          jobs += thread.jobsLeft();
          max += thread.getLimit();
        }
      }
    }

    int threadsRequired = (int) ((jobs / max) * threads.length) + 1;

    if (threadsRequired >= threads.length) {
      return 0;
    }

    if (threadsRequired < threadsThatShouldBe) {
      threadsRequired = threadsThatShouldBe;
    }

    // less aggressive scaling down
    int threadsToRemove = threads.length - threadsRequired;

    if (threadsToRemove > 1) {
      threadsToRemove = Math.min(threadsToRemove, 1 + (threads.length / 6));
    }

    if (threadsToRemove > 0) {

      int newAmount = threads.length - threadsToRemove;
      HandlingThread[] newThreads = new HandlingThread[newAmount];

      int threadCount = 0;

      for (int i = 0; i < threads.length; i++) {
        HandlingThread thread = threads[i];
        if (thread != null) {
          if (threadCount >= newAmount) {
            if (this.isCachingThreads()) {
              putThreadToCache(threads[i]);
              thread.wakeup();
            } else {
              this.allRegisteringHandlingThreads.remove(threads[i]);
              threads[i].setRunning(false);
            }
          } else {
            newThreads[threadCount] = threads[i];
          }
          threadCount++;
        }
      }

      this.setHandlingThreads(newThreads);

      return threadsToRemove;
    }

    return 0;
  }

  private HandlingThread getNewThread() {
    int jobsSize = this.getJobsPerThreadValue();
    int bufSize = this.getMaxGrowningBufferChunkSize();
    int defaultMaxMessage = this.getMaxMessageSize();
    HandlingThread t;

    switch (this.getPoolType()) {
      case POOL:
        t = new HandlingThreadPooled(
            this,
            jobsSize,
            bufSize,
            defaultMaxMessage,
            getDefaultIdleTime());
        break;
      case QUEUE:
        t = new HandlingThreadQueued(
            this,
            jobsSize,
            bufSize,
            defaultMaxMessage,
            getDefaultIdleTime());
        break;
      case QUEUE_SHARED:
        t = new HandlingThreadSharedQueue(
            this,
            jobsSize,
            bufSize,
            defaultMaxMessage,
            getDefaultIdleTime());
        break;
      default:
        throw new RuntimeException(
            "Unknown thread handling type selected: " + this.getPoolType());
    }

    t.start();

    this.allRegisteringHandlingThreads.add(t);

    return t;
  }

  private final ArrayDeque<HandlingThread> threadsCache = new ArrayDeque<>();

  public HandlingThread getCachedOrNewThread() {
    HandlingThread t = threadsCache.pollFirst();
    if (t != null) {
      return t;
    } else {
      return this.getNewThread();
    }
  }

  public void putThreadToCache(HandlingThread t) {
    if (t != null) {
      threadsCache.addLast(t);
    }
  }

  private void clearThreadsCache() {
    HandlingThread t;
    while ((t = threadsCache.pollFirst()) != null) {
      t.setRunning(false);
      t.wakeup();
    }
  }

  /**
   * @return the handlingThreads
   */
  public HandlingThread[] getHandlingThreads() {
    return handlingThreads;
  }

  @Override
  public AbstractHandlingThread[] getAllHandlingThreads() {
    return handlingThreads;
  }

  /**
   * @param handlingThreads the handlingThreads to set
   */
  public void setHandlingThreads(HandlingThread[] handlingThreads) {
    this.handlingThreads = handlingThreads;
  }
}
