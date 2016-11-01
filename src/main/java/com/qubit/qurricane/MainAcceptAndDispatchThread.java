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

import static com.qubit.qurricane.DataHandler.startedAnyHandler;
import static com.qubit.qurricane.HandlingThread.totalWaitedIO;
import static com.qubit.qurricane.Server.log;
import java.io.IOException;
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

  private static long infoLogsFrequency = 1 * 1000;
  
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
  private boolean allowingMoreAcceptsThanSlots = false;
  private long acceptDelay;
  private final long maxIdleAfterAccept;
  private boolean running;

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

      Set<SelectionKey> selectionKeys
              = acceptSelector.selectedKeys();
      
      for (SelectionKey key : selectionKeys) {
        
        try {
          if (key.isValid()) {
            if (key.isAcceptable()) {
              if (!isAllowingMoreAcceptsThanSlots()) {
                while(!thereAreFreeJobs(handlingThreads)) {
                  try {
                    totalWaitingAcceptMsCounter++;
                    Thread.sleep(1);
                  } finally {}
                }
              }
              
              SocketChannel channel = this.server.accept(key, acceptSelector);
              acceptedCnt++;
              
              if (channel != null) {
                if (this.startReading(handlingThreads, channel, null) == null) {
                  // too many jobs! drop key to registry and pick later
                  SelectionKey newKey = 
                        channel.register(acceptSelector, OP_READ);
                  DataHandler dataHandler = new DataHandler(
                          server, (SocketChannel) newKey.channel());
                  startedAnyHandler(dataHandler);
                  
                  newKey.attach(dataHandler);
                  // consider Thread.wait(1) here instead registration
                }
              }
            } else if (key.isReadable()) {
              // jobs that werent added immediatelly on accept
              DataHandler dh = (DataHandler) key.attachment();
              if (this.handleMaxIdle(dh, this.maxIdleAfterAccept)) {
                key.cancel(); // already too long 
                Server.close((SocketChannel) key.channel());
              } else {
                DataHandler handler = this.startReading(
                        handlingThreads,
                        (SocketChannel) key.channel(),
                        dh);
                if (handler != null) {
                  // job added, remove from selector
                  key.cancel();
                }
              }
            }
          } else {
            //key.channel().close();
          }
        } catch (CancelledKeyException ex) {
          log.info("Key already closed.");
        } catch (Exception ex) {
          log.log(Level.SEVERE, null, ex);
        }
      }

      selectionKeys.clear();
      
      if (System.currentTimeMillis() > lastMeassured + getInfoLogsFrequency()) {
        log.log(Level.INFO,
                "Accepted connections: {0}, total accept waited: {1}ms, total waited IO: {2}",
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
  private DataHandler startReading(
          HandlingThread[] handlingThreads,
          SocketChannel channel,
          DataHandler dataHandler) {
    
    int len = handlingThreads.length;
    for (int c = 0; c < len; c++) {
      HandlingThread handlingThread = handlingThreads[currentThread];
      currentThread = (currentThread + 1) % len;

      if (handlingThread != null) {
        if (dataHandler == null) { // if its not passed, create one
          dataHandler = new DataHandler(server, channel);
          startedAnyHandler(dataHandler);
        }

        if (handlingThread.addJob(dataHandler)) {
            //key.cancel(); // remove key, handled channel is
          // now by job processor
          return dataHandler;
        }
      }
    }
    return null;
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
   * @return the allowingMoreAcceptsThanSlots
   */
  public boolean isAllowingMoreAcceptsThanSlots() {
    return allowingMoreAcceptsThanSlots;
  }

  /**
   * @param allowingMoreAcceptsThanSlots the allowingMoreAcceptsThanSlots to set
   */
  public void setAllowingMoreAcceptsThanSlots(boolean allowingMoreAcceptsThanSlots) {
    this.allowingMoreAcceptsThanSlots = allowingMoreAcceptsThanSlots;
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
  
  protected boolean handleMaxIdle(
          DataHandler dataHandler, long maxIdle) {
    //check if connection is not open too long! Prevent DDoS
    long idle = dataHandler.getMaxIdle(maxIdle);
    if (idle != 0 &&
            (System.currentTimeMillis() - dataHandler.getTouch()) > idle) {
      log.log(Level.INFO,
              "Max accept idle gained - closing, total: {0}",
              ++closedIdleCounter);
      return true;
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
}
