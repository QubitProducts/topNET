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
import java.util.Set;
import java.util.logging.Level;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class MainAcceptAndDispatchThread extends Thread {

  public static volatile boolean keepRunning = true;
  private static HandlingThread[] handlingThreads;

  public static void setupThreadsList(
          int many,
          int jobsSize,
          int bufSize,
          int defaultMaxMessage,
          long defaultIdleTime,
          boolean pooled) {
    
    handlingThreads = new HandlingThread[many];
    
    for (int i = 0; i < many; i++) {
      HandlingThread t;
      if (pooled) {
        t = new HandlingThreadPooled(
                jobsSize,
                bufSize,
                defaultMaxMessage,
                defaultIdleTime);
        
      } else {
        t = new HandlingThreadQueueud(
                jobsSize,
                bufSize,
                defaultMaxMessage,
                defaultIdleTime);
      }
      t.start();
      handlingThreads[i] = t;
    }
  }

  static void removeThread(HandlingThread thread) {
    for (int i = 0; i < handlingThreads.length; i++) {
      if (handlingThreads[i] == thread) {
        handlingThreads[i] = null;
      }
    }
  }

  static boolean hasThreads() {
    for (int i = 0; i < handlingThreads.length; i++) {
      if (handlingThreads[i] != null) {
        return true;
      }
    }
    return false;
  }

  private final Selector acceptSelector;
  private final Server server;

  MainAcceptAndDispatchThread(Server server, final Selector acceptSelector) throws IOException {
    this.server = server;
    this.acceptSelector = acceptSelector;
  }

  volatile int acceptedCnt = 0;
  private int currentThread = 0;
  
  @Override
  public void run() {

    long lastMeassured = System.currentTimeMillis();
    
    while (keepRunning) {
      try {
        // pick current events list:
        getAcceptSelector().select();
      } catch (IOException ex) {
          log.log(Level.SEVERE, null, ex);
      }

      Set<SelectionKey> selectionKeys
              = getAcceptSelector().selectedKeys();

      for (SelectionKey key : selectionKeys) {
        try {
          if (key.isValid()) {
            DataHandler dataHandler = (DataHandler) key.attachment();
            if (dataHandler == null && key.isAcceptable()) {
              if (Server.accept(key, acceptSelector) != null) {
                acceptedCnt++;
              }
            } else {
              this.startReading(key, dataHandler);
            }
          }
        } catch (CancelledKeyException ex) {
          log.info("Key already closed.");
        } catch (Exception ex) {
          log.log(Level.SEVERE, null, ex);
        }
      }
      
      if (System.currentTimeMillis() > lastMeassured + 10000) {
        log.log(Level.INFO, "Accepted connections: {0}", acceptedCnt);
        lastMeassured = System.currentTimeMillis();
      }
    }
  }

  /**
   * @return the acceptSelector
   */
  public Selector getAcceptSelector() {
    return acceptSelector;
  }
//  int i = 0;
  private void startReading(SelectionKey key, DataHandler dataHandler) {
    
    if (dataHandler == null) {
      dataHandler = new DataHandler(this.server);
      key.attach(dataHandler);
    }

    // add to worker
    if (!dataHandler.locked) {
      // currently closeIfNecessaryAndTellIfShouldReleaseJob
      // decides that single job is bound to thread - and it's fine
      for (int c = 0; c < handlingThreads.length; c++) {
        HandlingThread handlingThread = handlingThreads[currentThread];
        currentThread = (currentThread + 1) % handlingThreads.length;
        
        if (handlingThread != null && 
                handlingThread.addJob(dataHandler, key)) {
//          log.info(">>>>>> " + i++);
          break;
        }
      }
    }
  }
}
