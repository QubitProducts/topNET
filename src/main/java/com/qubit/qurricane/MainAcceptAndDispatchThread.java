/*
 * Qurrican
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
package com.qubit.qurricane;

import static com.qubit.qurricane.HandlingThread.totalWaitedIO;
import static com.qubit.qurricane.Server.log;
import java.io.IOException;
import static java.lang.Thread.State.TIMED_WAITING;
import static java.lang.Thread.State.WAITING;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_READ;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
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
  private final Server server;
  private boolean notAllowingMoreAcceptsThanSlots = false;
  private long acceptDelay;
  private final long maxIdleAfterAccept;
  private boolean running;
  private boolean waitingForReadEvents = true;

  MainAcceptAndDispatchThread(Server server,
      final Selector acceptSelector,
      long maxIdleAfterAccept)
      throws IOException {
    this.server = server;
    this.acceptSelector = acceptSelector;
    this.maxIdleAfterAccept = maxIdleAfterAccept;
  }

  private int acceptedCnt = 0;
  private int currentThread = 0;

  @Override
  public void run() {
    HandlingThread[] handlingThreads = this.server.getHandlingThreads();

    long lastMeassured = System.currentTimeMillis();
    long totalWaitingAcceptMsCounter = 0;
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
        acceptSelector.select();
      } catch (IOException ex) {
        log.log(Level.SEVERE, null, ex);
      }

      Set<SelectionKey> selectionKeys = acceptSelector.selectedKeys();

      for (SelectionKey key : selectionKeys) {

        try {
          if (key.isValid()) {
            if (key.isAcceptable()) {
              if (isNotAllowingMoreAcceptsThanSlots()) {
                while (!thereAreFreeJobs(handlingThreads)) {
                  try {
                    Thread.sleep(1);
                  } catch (InterruptedException ex) {
                  }
                }
              }

              SocketChannel channel = this.server.accept(key, acceptSelector);

              if (channel != null) {
                acceptedCnt++;
                if (this.isWaitingForReadEvents()
                    || !this.startReading(handlingThreads, channel, null)) {
                  SelectionKey newKey
                      = channel.register(acceptSelector, OP_READ);

                  newKey.attach(new Long(System.currentTimeMillis()));
                }
              }
            } else if (key.isReadable()) {
              // jobs that werent added immediatelly on accept
              Long acceptTime = (Long) key.attachment();
              if (this.handleMaxIdle(acceptTime, this.maxIdleAfterAccept, key)) {
                key.cancel(); // already too long 
                Server.close((SocketChannel) key.channel());
              } else if (this.startReading(handlingThreads,
                  (SocketChannel) key.channel(),
                  acceptTime)) {
                // job added, remove from selector
                key.cancel();
              }
            }
          } else {
            key.cancel();
            key.channel().close();
          }
        } catch (CancelledKeyException ex) {
          log.info("Key already cancelled.");
        } catch (Exception ex) {
          log.log(Level.SEVERE, null, ex);
        }
      }

      for (int i = 0; i < handlingThreads.length; i++) {
        HandlingThread th = handlingThreads[i];
        if (th.hasJobs()) {
          if (th.getState() == WAITING || 
              th.getState() == TIMED_WAITING) {
            synchronized(th.sleepingLocker) {
              th.sleepingLocker.notify();
            }
          }
        }
      }

//      selectionKeys.clear();

      if (System.currentTimeMillis() > (lastMeassured + getInfoLogsFrequency())) {
        log.log(Level.INFO,
            "Accepted connections: {0}, total accept waited: {1}ms ,total waited IO: {3}",
            new Object[]{
              acceptedCnt,
              totalWaitingAcceptMsCounter,
              totalWaitedIO});
        lastMeassured = System.currentTimeMillis();
      }
    }

    try {
      acceptSelector.close();
    } catch (IOException ex) {
      log.log(Level.SEVERE, null, ex);
    }
  }

  LinkedList<DataHandler> waitingJobs = new LinkedList<>();

//  int i = 0;
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
    int len = handlingThreads.length;
    for (int c = 0; c < len; c++) {
      HandlingThread handlingThread = handlingThreads[c];
      if (handlingThread != null && handlingThread.canAddJob()) {
        return true;
      }
    }

    return false;
  }

  /**
   * @return the notAllowingMoreAcceptsThanSlots
   */
  public boolean isNotAllowingMoreAcceptsThanSlots() {
    return notAllowingMoreAcceptsThanSlots;
  }

  /**
   * @param allowingMoreAcceptsThanSlots the notAllowingMoreAcceptsThanSlots to
   * set
   */
  public void setNotAllowingMoreAcceptsThanSlots(boolean allowingMoreAcceptsThanSlots) {
    this.notAllowingMoreAcceptsThanSlots = allowingMoreAcceptsThanSlots;
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
        log.log(Level.INFO,
            "ML Max accept idle gained - closing, total: {0}",
            ++closedIdleCounter);
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
    int len = handlingThreads.length;
    for (int c = 0; c < len; c++) {
      HandlingThread handlingThread = handlingThreads[c];
      if (handlingThread != null) {
        if (handlingThread.addJob(channel, acceptTime)) {
          return true;
        }
      }
    }
    return false;
  }
}
