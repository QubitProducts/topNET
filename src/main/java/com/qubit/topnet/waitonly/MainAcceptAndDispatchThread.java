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
package com.qubit.topnet.waitonly;

import static com.qubit.topnet.waitonly.HandlingThread.handlingClosedIdleCounter;
import static com.qubit.topnet.waitonly.HandlingThread.totalWaitedIO;
import static com.qubit.topnet.waitonly.WaitTypeServer.log;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_READ;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.logging.Level;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class MainAcceptAndDispatchThread extends Thread {

  private static long infoLogsFrequency = 10 * 1000;

  /**
   * @return the infoLogsFrequency
   */
  public static long getInfoLogsFrequency() {
    return infoLogsFrequency;
  }

  /**
   * @param aInfoLogsFrequency the infoLogsFrequency to set
   */
  public static void setInfoLogsFrequency(long aInfoLogsFrequency) {
    infoLogsFrequency = aInfoLogsFrequency;
  }

  private final Selector acceptSelector;
  private final WaitTypeServer server;
  private final long maxIdleAfterAccept;
  private boolean running;
  private boolean waitingForReadEvents = true;
  private long timeSinceCouldntAddJob = 0;
  private long noSlotsAvailableTimeout = 20;
  private long lastdownScaleTried = 0;
  private long scalingDownTryPeriodMS = 5000;
  private boolean autoScalingDown = true;

  MainAcceptAndDispatchThread(WaitTypeServer server,
      final Selector acceptSelector,
      long maxIdleAfterAccept)
      throws IOException {
    this.server = server;
    this.acceptSelector = acceptSelector;
    this.maxIdleAfterAccept = maxIdleAfterAccept;
  }

  private int acceptedCnt = 0;
  private int currentThread = 0;

  long lastMeassured = System.currentTimeMillis();
  
  @Override
  public void run() {
    this.lastdownScaleTried = lastMeassured;
    long totalWaitingAcceptMsCounter = 0;
    this.setRunning(true);
    while (this.isRunning()) {

      try {
        // pick current events list:
        acceptSelector.select();
      } catch (IOException ex) {
        log.log(Level.SEVERE, null, ex);
      }

      Set<SelectionKey> selectionKeys = acceptSelector.selectedKeys();
      HandlingThread[] handlingThreads = this.server.getHandlingThreads();

      for (SelectionKey key : selectionKeys) {

        try {
          if (key.isValid()) {
            if (key.isAcceptable()) {
              SocketChannel channel;
              if ((channel = this.server.accept()) != null) {
                acceptedCnt++;
                if (this.isWaitingForReadEvents()
                    || !this.startReading(handlingThreads, channel, null)) {
                  channel.register(
                      acceptSelector,
                      OP_READ,
                      System.currentTimeMillis());
                }
              }
            } else {//if (key.isReadable()) { // only READ is registered
              Long acceptTime = (Long) key.attachment();
              if (this.handleMaxIdle(acceptTime, key)) {
                key.cancel(); // already too long 
                WaitTypeServer.close((SocketChannel) key.channel());
              } else {
                if (!this.tryAddingJob(key, acceptTime, handlingThreads)) {
                  // failed adding job
                  if (this.timeSinceCouldntAddJob == 0) {
                    this.timeSinceCouldntAddJob = System.currentTimeMillis();
                  } else if (System.currentTimeMillis() >
                      (this.timeSinceCouldntAddJob + this.noSlotsAvailableTimeout)) {
                    this.timeSinceCouldntAddJob = 0;
                    if (this.server.addThread()) {
                      handlingThreads = this.server.getHandlingThreads();
                      this.tryAddingJob(key, acceptTime, handlingThreads);
                    }
                  }
                }
              }
            }
          } else {
            key.cancel();
            key.channel().close();
          }
        } catch (CancelledKeyException ex) {
          log.info("Key already cancelled.");
        } catch (Exception ex) {
          log.log(Level.SEVERE, "Exception in main loop.", ex);
        }
      }
      
      for (HandlingThread handlingThread : handlingThreads) {
        if (handlingThread != null && handlingThread.hasJobs()) {
          handlingThread.wakeup();
        }
      }

      selectionKeys.clear();
      
      if (this.autoScalingDown) {
        this.scaleDownIfCan();
      }
      
      if (System.currentTimeMillis() > (lastMeassured + getInfoLogsFrequency())) {
        log.log(Level.INFO,
            "Accepted connections: {0}, total accept waited: {1}ms , waited IO count: {2}",
            new Object[]{
              acceptedCnt,
              totalWaitingAcceptMsCounter,
              totalWaitedIO});
        log.log(Level.INFO,
            "Max accept idle gained total ML:{0} HL:{1}",
            new Object[]{closedIdleCounter, handlingClosedIdleCounter});
        lastMeassured = System.currentTimeMillis();
      }
    }

    try {
      acceptSelector.close();
    } catch (IOException ex) {
      log.log(Level.SEVERE, null, ex);
    }
  }

  private boolean tryAddingJob(SelectionKey key,
                                 Long acceptTime,
                                 HandlingThread[] handlingThreads) {
    if (this.startReading(
            handlingThreads,
            (SocketChannel) key.channel(),
            acceptTime)) {
      // job added, remove from selector
      key.cancel();
      return true;
    }
    return false;
  }
  
  private boolean startReading(
      HandlingThread[] handlingThreads,
      SocketChannel channel, Long acceptTime) {
    if (this.server.isPuttingJobsEquallyToAllThreads()) {
      return this.spreadEqually(handlingThreads, channel, acceptTime);
    } else {
      return this.fillUpThreadsOneByOne(handlingThreads, channel, acceptTime);
    }
  }

  private boolean thereAreFreeJobs(HandlingThread[] handlingThreads) {
    for (HandlingThread handlingThread : handlingThreads) {
      if (handlingThread != null && handlingThread.canAddJob()) {
        return true;
      }
    }

    return false;
  }

  private long closedIdleCounter = 0;

  protected boolean handleMaxIdle(Long ts, SelectionKey key) {
    //check if connection is not open too long! Prevent DDoS
    if (this.maxIdleAfterAccept != 0 &&
        (System.currentTimeMillis() - ts) > this.maxIdleAfterAccept) {
      if (this.server.getLimitsHandler() != null) {
        return this.server.getLimitsHandler()
            .handleTimeout(key, this.maxIdleAfterAccept, null);
      } else {
        closedIdleCounter++;
        return true;
      }
    }
    return false;
  }

  /**
   * @return the running
   */
  public boolean isRunning() {
    return running;
  }

  /**
   * @param running the running to set
   */
  public synchronized void setRunning(boolean running) {
    this.running = running;
  }

  /**
   * @return the waitingForReadEvents
   */
  public boolean isWaitingForReadEvents() {
    return waitingForReadEvents;
  }

  /**
   * @param waitingForReadEvents the waitingForReadEvents to set
   */
  public void setWaitingForReadEvents(boolean waitingForReadEvents) {
    this.waitingForReadEvents = waitingForReadEvents;
  }

  private boolean spreadEqually(HandlingThread[] handlingThreads,
                                  SocketChannel channel,
                                  Long acceptTime) {
    int len = handlingThreads.length;
    for (int c = 0; c < len; c++) {
      HandlingThread handlingThread = handlingThreads[currentThread];
      currentThread = (currentThread + 1) % len;
      if (handlingThread != null) {
        if (handlingThread.addJob(channel, acceptTime)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean fillUpThreadsOneByOne(HandlingThread[] handlingThreads,
                                           SocketChannel channel,
                                           Long acceptTime) {
    for (HandlingThread handlingThread : handlingThreads) {
      if (handlingThread != null) {
        if (handlingThread.addJob(channel, acceptTime)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return the noSlotsAvailableTimeout
   */
  public long getNoSlotsAvailableTimeout() {
    return noSlotsAvailableTimeout;
  }

  /**
   * @param noSlotsAvailableTimeout the noSlotsAvailableTimeout to set
   */
  public void setNoSlotsAvailableTimeout(long noSlotsAvailableTimeout) {
    this.noSlotsAvailableTimeout = noSlotsAvailableTimeout;
  }  
  
  private void scaleDownIfCan() {
    if (System.currentTimeMillis() > (lastdownScaleTried + getScalingDownTryPeriodMS())) {
      if (this.server.cleanupThreadsExcess() > 0) {
        currentThread = 0;
      }
      lastdownScaleTried = System.currentTimeMillis();
    }
  }

  /**
   * @return the scalingDownTryPeriodMS
   */
  public long getScalingDownTryPeriodMS() {
    return scalingDownTryPeriodMS;
  }

  /**
   * @param scalingDownTryPeriodMS the scalingDownTryPeriodMS to set
   */
  public void setScalingDownTryPeriodMS(long scalingDownTryPeriodMS) {
    this.scalingDownTryPeriodMS = scalingDownTryPeriodMS;
  }

  /**
   * @return the autoScalingDown
   */
  public boolean isAutoScalingDown() {
    return autoScalingDown;
  }

  /**
   * @param autoScalingDown the autoScalingDown to set
   */
  public void setAutoScalingDown(boolean autoScalingDown) {
    this.autoScalingDown = autoScalingDown;
  }
                  }
