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
import com.qubit.topnet.eventonly.SelectionKeyChain.SelectionKeyLink;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_READ;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class MainAcceptAndDispatchThread extends Thread {

  private final static Logger log = 
      Logger.getLogger(MainAcceptAndDispatchThread.class.getName());
  
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
  private boolean running;
  private long timeSinceCouldntAddJob = 0;
  private long noSlotsAvailableTimeout = 20;
  private long lastdownScaleTried = 0;
  private long scalingDownTryPeriodMS = 5000;
  private boolean autoScalingDown = true;

  MainAcceptAndDispatchThread(EventTypeServer server,
                              Selector channelSelector,
                              long maxIdleAfterAccept)
      throws IOException {
    this.server = server;
    this.maxIdleAfterAccept = maxIdleAfterAccept;
    this.channelSelector = channelSelector;
    this.unprocessedSelectionKeyChain = new SelectionKeyChain();
  }

  private int acceptedCnt = 0;
  private int currentThread = 0;
  long maxIdleAfterAccept;
  long lastMeassured = System.currentTimeMillis();
  SelectionKeyChain unprocessedSelectionKeyChain;
    
  @Override
  public void run() {
    this.lastdownScaleTried = lastMeassured;
    this.setRunning(true);
    while (this.isRunning()) {

      try {
        // pick current events list:
        getChannelSelector().select();
      } catch (IOException ex) {
        log.log(Level.SEVERE, null, ex);
      }

      Set<SelectionKey> selectionKeys = getChannelSelector().selectedKeys();
      
      if (selectionKeys.isEmpty()) continue;
      
      HandlingThread[] handlingThreads = this.server.getHandlingThreads();

      for (SelectionKey key : selectionKeys) {

        try {
          if (key.isValid()) {
            if (key.isAcceptable()) {
              SocketChannel channel;
              if ((channel = this.server.accept()) != null) {
                acceptedCnt++;
                if (this.maxIdleAfterAccept > 0) {
                  SelectionKey newKey = 
                    channel.register(getChannelSelector(), OP_READ);
                  this.checkOutdatedKeys();
                  
                  SelectionKeyLink skl = 
                      unprocessedSelectionKeyChain.new SelectionKeyLink(
                          newKey,
                          System.currentTimeMillis());
                  
                  newKey.attach(skl);
                  unprocessedSelectionKeyChain.add(skl);
                } else {
                  channel.register(
                      getChannelSelector(),
                      OP_READ,
                      System.currentTimeMillis());
                }
              }
            } else {
              if (key.attachment() instanceof HandlingThread) {
                ((HandlingThread) key.attachment()).wakeup();
              } else {
                if (!this.tryAddingJob(key, handlingThreads)) {
                  // failed adding job
                  if (this.timeSinceCouldntAddJob == 0) {
                    this.timeSinceCouldntAddJob = System.currentTimeMillis();
                  } else if (System.currentTimeMillis()
                      > (this.timeSinceCouldntAddJob + this.noSlotsAvailableTimeout)) {
                    this.timeSinceCouldntAddJob = 0;
                    if (this.server.addThread()) {
                      handlingThreads = this.server.getHandlingThreads();
                      this.tryAddingJob(key, handlingThreads);
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
                  "Max idle closed for ACCEPT:{0} and for CONNECTION:{1}",
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
                               HandlingThread[] handlingThreads) {
    
    if (this.maxIdleAfterAccept > 0) {
      SelectionKeyLink skl = (SelectionKeyLink) key.attachment();
      HandlingThread owningThread = this.startReading(handlingThreads,
                                                      key, skl.getAcceptTime());
      if (owningThread != null) {
        skl.remove(); // important to remove - SelectionKeyLink is used ti 
        key.attach(owningThread);
        return true;
      }
    } else {
      Long acceptTime = (Long) key.attachment();
      HandlingThread owningThread = this.startReading(handlingThreads,
                                                      key,
                                                      acceptTime);
      if (owningThread != null) {
        key.attach(owningThread);
        return true;
      }
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

  // counter fpor closed idle requests
  private long closedIdleCounter = 0;

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

  long lastChecked = 0;
  
  private void checkOutdatedKeys() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - this.lastChecked > this.maxIdleAfterAccept) {
      this.lastChecked = currentTime;

      SelectionKeyLink current = unprocessedSelectionKeyChain.getFirst();

      while (current != null) {
        SelectionKey sKey = current.getKey();
        SelectionKeyLink nextElem = current.getNext();
        Object numOrThread = sKey.attachment();
        
        if (numOrThread instanceof SelectionKeyLink) {
          Long acceptTime = ((SelectionKeyLink) numOrThread).getAcceptTime();

          if (this.handleMaxIdle(acceptTime, sKey)) {
            sKey.cancel(); // already too long 
            EventTypeServer.close((SocketChannel) sKey.channel());
            current.remove();
          }
        }
        
        current = nextElem;
      }
    }
  }

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
}
