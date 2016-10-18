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
  final static long MSG_TOUT = 10000;

  public static void setupThreadsList(
          int many,
          int jobsSize,
          int bufSize,
          int defaultMaxMessage,
          long defaultIdleTime,
          String type) {
    
    handlingThreads = new HandlingThread[many];
    boolean announced = false;
    for (int i = 0; i < many; i++) {
      HandlingThread t;
      if (type.equals("pool")) {
        if (!announced) {
          log.info("Atomic Array  Pools type used.");
          announced = true;
        }
        t = new HandlingThreadPooled(
                jobsSize,
                bufSize,
                defaultMaxMessage,
                defaultIdleTime);
        
      } else if (type.equals("queue")) {
        if (!announced) {
          log.info("Concurrent Queue Pools type used.");
          announced = true;
        }
        t = new HandlingThreadQueued(
                jobsSize,
                bufSize,
                defaultMaxMessage,
                defaultIdleTime);
      } else if (type.equals("queue-shared")) {
        if (!announced) {
          log.info("Shared Concurrent Queue Pools type used.");
          announced = true;
        }
        t = new HandlingThreadSharedQueue(
                jobsSize,
                bufSize,
                defaultMaxMessage,
                defaultIdleTime);
      } else {
        throw new RuntimeException(
                "Unknown thread handling type selected: " + type);
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
  private boolean acceptOnlyIfThereAreFreeSlots = true;

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
        acceptSelector.select();
      } catch (IOException ex) {
          log.log(Level.SEVERE, null, ex);
      }

      Set<SelectionKey> selectionKeys
              = acceptSelector.selectedKeys();

      for (SelectionKey key : selectionKeys) {
        try {
          if (key.isValid()) {
            DataHandler dataHandler = (DataHandler) key.attachment();
            if (dataHandler == null && key.isAcceptable()) {
              
              if (isAcceptOnlyIfThereAreFreeSlots()) {
                while(!thereAreFreeJobs()) {
                  try {
                    log.info("waiting for free pools...");
                    Thread.sleep(1);
                  } finally {}
                }
              }
              
              if (Server.accept(key, acceptSelector) != null) {
                acceptedCnt++;
              }
            } else {
              this.startReading(key, dataHandler);
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
      
      if (System.currentTimeMillis() > lastMeassured + MSG_TOUT) {
        log.log(Level.FINE, "Accepted connections: {0}", acceptedCnt);
        lastMeassured = System.currentTimeMillis();
      }
    }
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

  private boolean thereAreFreeJobs() {
    for (int c = 0; c < handlingThreads.length; c++) {
      HandlingThread handlingThread = handlingThreads[c];

      if (handlingThread != null && handlingThread.canAddJob()) {
        return true;
      }
    }
    
    return false;
  }

  /**
   * @return the acceptOnlyIfThereAreFreeSlots
   */
  public boolean isAcceptOnlyIfThereAreFreeSlots() {
    return acceptOnlyIfThereAreFreeSlots;
  }

  /**
   * @param acceptOnlyIfThereAreFreeSlots the acceptOnlyIfThereAreFreeSlots to set
   */
  public void setAcceptOnlyIfThereAreFreeSlots(boolean acceptOnlyIfThereAreFreeSlots) {
    this.acceptOnlyIfThereAreFreeSlots = acceptOnlyIfThereAreFreeSlots;
  }

}
