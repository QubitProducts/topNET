/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import static com.qubit.qurricane.Server.log;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
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
//            DataHandler dataHandler = (DataHandler) key.attachment();
            if (key.isAcceptable()) {
              
              if (!isAllowingMoreAcceptsThanSlots()) {
                while(!thereAreFreeJobs(handlingThreads)) {
                  try {
                    totalWaitingAcceptMsCounter++;
                    Thread.sleep(1);
                  } finally {}
                }
              }
              
              SocketChannel channel = Server.accept(key, acceptSelector);
              
              if (channel != null) {
                acceptedCnt++;
                this.startReading(handlingThreads, channel);
              }
            } else {
//              this.startReading(handlingThreads, key.channel(), dataHandler);
            }
          } else {
            key.cancel();
          }
        } catch (CancelledKeyException ex) {
          log.info("Key already closed.");
        } catch (Exception ex) {
          log.log(Level.SEVERE, null, ex);
        }
      }
      
      if (System.currentTimeMillis() > lastMeassured + getInfoLogsFrequency()) {
        log.log(Level.INFO,
                "Accepted connections: {0}, total accept waited: {1}ms",
                new Object[]{acceptedCnt, totalWaitingAcceptMsCounter});
        lastMeassured = System.currentTimeMillis();
      }
    }
  }
  
//  int i = 0;
  private void startReading(
          HandlingThread[] handlingThreads,
          SocketChannel channel) {
    DataHandler dataHandler = new DataHandler(server, channel);

    // add to worker
    if (!dataHandler.locked) {
      // currently closeIfNecessaryAndTellIfShouldReleaseJob
      // decides that single job is bound to thread - and it's fine
      for (int c = 0; c < handlingThreads.length; c++) {
        HandlingThread handlingThread = handlingThreads[currentThread];
        currentThread = (currentThread + 1) % handlingThreads.length;
        
        if (handlingThread != null && 
                handlingThread.addJob(dataHandler)) {
//          log.info(">>>>>> " + i++);
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

}
