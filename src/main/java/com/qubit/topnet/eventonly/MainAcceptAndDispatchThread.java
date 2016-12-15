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

import static com.qubit.topnet.eventonly.HandlingThread.handlingClosedIdleCounter;
import static com.qubit.topnet.eventonly.EventTypeServer.log;
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

  private final Selector channelSelector;
  private final EventTypeServer server;
  private long acceptDelay;
  private boolean running;
  private long timeSinceCouldntAddJob = 0;
  private long noSlotsAvailableTimeout = 20;
  private long lastdownScaleTried = 0;
  private long scalingDownTryPeriodMS = 5000;
  private boolean autoScalingDown = true;

  MainAcceptAndDispatchThread(final EventTypeServer server,
                                  final Selector channelSelector)
      throws IOException {
    this.server = server;
    this.channelSelector = channelSelector;
  }

  private int acceptedCnt = 0;
  private int currentThread = 0;

  long lastMeassured = System.currentTimeMillis();
  
  @Override
  public void run() {
    this.lastdownScaleTried = lastMeassured;
    this.setRunning(true);
    while (this.isRunning()) {

      if (this.getAcceptDelay() > 0) {
        try {
          Thread.sleep(this.getAcceptDelay());
        } catch (InterruptedException ex) {
        }
      }

      try {
        // pick current events list:
        getChannelSelector().select();
      } catch (IOException ex) {
        log.log(Level.SEVERE, null, ex);
      }

      Set<SelectionKey> selectionKeys = getChannelSelector().selectedKeys();
      HandlingThread[] handlingThreads = this.server.getHandlingThreads();

      for (SelectionKey key : selectionKeys) {

        try {
          if (key.isValid()) {
            if (key.isAcceptable()) {
              SocketChannel channel;
              if ((channel = this.server.accept()) != null) {
                acceptedCnt++;
                channel.register(getChannelSelector(),
                    OP_READ,
                    System.currentTimeMillis());
              }
            } else {
              if (key.attachment() instanceof HandlingThread) {
                ((HandlingThread) key.attachment()).wakeup();
              } else {
                Long acceptTime = (Long) key.attachment();

                if (!this.tryAddingJob(key, acceptTime, handlingThreads)) {
                  // failed adding job
                  if (this.timeSinceCouldntAddJob == 0) {
                    this.timeSinceCouldntAddJob = System.currentTimeMillis();
                  } else if (System.currentTimeMillis()
                      > (this.timeSinceCouldntAddJob + this.noSlotsAvailableTimeout)) {
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
          // normal to happen if one of threads already cancelled a key
        } catch (Exception ex) {
          log.log(Level.SEVERE, "Exception in notifier loop.", ex);
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
          log.log(Level.INFO, "Accepted connections: {0}", acceptedCnt);
          log.log(Level.INFO,
                  "Max accept idle gained total ML:{0} HL:{1}",
                   new Object[]{closedIdleCounter, handlingClosedIdleCounter});
          lastMeassured = System.currentTimeMillis();
      }
    }

    try {
      getChannelSelector().close();
    } catch (IOException ex) {
      log.log(Level.SEVERE, null, ex);
    }
  }

  private boolean tryAddingJob(SelectionKey key,
                                Long acceptTime,
                                HandlingThread[] handlingThreads) {
    HandlingThread owningThread = this.startReading(
        handlingThreads,
        key,
        acceptTime);
    if (owningThread != null) {
      // job added, remove from selector
      key.attach(owningThread);
      return true;
    }
    return false;
  }

  private HandlingThread startReading(HandlingThread[] handlingThreads,
      SelectionKey key,
      Long acceptTime) {

    if (this.server.isPuttingJobsEquallyToAllThreads()) {
      return this.spreadEqually(handlingThreads, key, acceptTime);
    } else {
      return this.fillUpThreadsOneByOne(handlingThreads, key, acceptTime);
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

  /**
   * @return the acceptDelay
   */
  public long getAcceptDelay() {
    return acceptDelay;
  }

  /**
   * @param acceptDelay the acceptDelay to set
   */
  public void setAcceptDelay(long acceptDelay) {
    this.acceptDelay = acceptDelay;
  }

  private long closedIdleCounter = 0;

  protected boolean handleMaxIdle(Long ts, long idle, SelectionKey key) {
    //check if connection is not open too long! Prevent DDoS
    if (idle != 0 && (System.currentTimeMillis() - ts) > idle) {
      if (this.server.getLimitsHandler() != null) {
        return this.server.getLimitsHandler().handleTimeout(key, idle, null);
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

  private HandlingThread spreadEqually(HandlingThread[] handlingThreads,
      SelectionKey key,
      Long acceptTime) {
    int len = handlingThreads.length;
    for (int c = 0; c < len; c++) {
      HandlingThread handlingThread = handlingThreads[currentThread];
      currentThread = (currentThread + 1) % len;
      if (handlingThread != null) {
        if (handlingThread.addJob(key, acceptTime)) {
          return handlingThread;
        }
      }
    }
    return null;
  }

  private HandlingThread fillUpThreadsOneByOne(HandlingThread[] handlingThreads,
      SelectionKey key,
      Long acceptTime) {
    for (HandlingThread handlingThread : handlingThreads) {
      if (handlingThread != null) {
        if (handlingThread.addJob(key, acceptTime)) {
          return handlingThread;
        }
      }
    }
    return null;
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

  /**
   * @return the channelSelector
   */
  public Selector getChannelSelector() {
    return channelSelector;
  }
}
