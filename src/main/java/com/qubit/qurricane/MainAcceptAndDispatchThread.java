/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

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

  private static long infoLogsFrequency = 60 * 1000;
  
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

  MainAcceptAndDispatchThread(Server server, final Selector acceptSelector) 
          throws IOException {
    this.server = server;
    this.acceptSelector = acceptSelector;
  }

  private int acceptedCnt = 0;
  private int currentThread = 0;
  
  @Override
  public void run() {
    HandlingThread[] handlingThreads = this.server.getHandlingThreads();
    
    long lastMeassured = System.currentTimeMillis();
    long totalWaitingAcceptMsCounter = 0;
    
    while (this.server.isServerRunning()) {
      
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
              
              if (channel != null) {
                channel.register(acceptSelector, OP_READ);
                acceptedCnt++;
              }
            } else {
              this.startReading(handlingThreads, key);
            }
          } else {
            key.channel().close();
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
  }
  
  LinkedList<DataHandler> waitingJobs = new LinkedList<>();
  
//  int i = 0;
  private void startReading(
          HandlingThread[] handlingThreads,
          SelectionKey key) {
    
    if (key.isReadable()) {
      DataHandler dataHandler = (DataHandler) key.attachment();
      
      if (dataHandler == null) {
        dataHandler = new DataHandler(server, (SocketChannel) key.channel());
        key.attach(dataHandler);
      }

      // currently closeIfNecessaryAndTellIfShouldReleaseJob
      // decides that single job is bound to thread - and it's fine
      for (int c = 0; c < handlingThreads.length; c++) {
        HandlingThread handlingThread = handlingThreads[currentThread];
        currentThread = (currentThread + 1) % handlingThreads.length;

        if (handlingThread != null && 
                handlingThread.addJob(dataHandler)) {
          key.cancel(); // remove key, handled channel is now by job processor
          break;
        }
      }
    }
  }

  private boolean thereAreFreeJobs(HandlingThread[] handlingThreads) {
    for (int c = 0; c < handlingThreads.length; c++) {
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

}
