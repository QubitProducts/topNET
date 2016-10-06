/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import static com.qubit.qurricane.Server.close;
import static com.qubit.qurricane.Server.log;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
          int defaultIdleTime) {
    handlingThreads = new HandlingThread[many];
    for (int i = 0; i < many; i++) {
      HandlingThread t = new HandlingThread(
              jobsSize,
              bufSize,
              defaultMaxMessage,
              defaultIdleTime);
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
//  private Lock lock = new ReentrantLock();

  MainAcceptAndDispatchThread(final Selector acceptSelector) {
    this.acceptSelector = acceptSelector;
  }

  @Override
  public void run() {
    int currentThread = 0;
    while (keepRunning) {
      try {
        // pick current events list:
        getAcceptSelector().select();
      } catch (IOException ex) {
        try {
          log.log(Level.SEVERE, null, ex);
          getAcceptSelector().close();
        } catch (IOException ex1) {
          log.log(Level.SEVERE, null, ex1);
        }
      }

      Set<SelectionKey> selectionKeys
              = getAcceptSelector().selectedKeys();

      for (SelectionKey key : selectionKeys) {
        if (key.isValid()) {
          try {
            
            DataHandler dataHandler = (DataHandler) key.attachment();
            
            if (dataHandler == null && key.isAcceptable()) {

              Server.accept(key, acceptSelector);

            } else {
              
              if (dataHandler == null) {
                dataHandler = new DataHandler();
                key.attach(dataHandler);
              }

              // add to worker
              if (!dataHandler.locked) {
                for (int i = currentThread, c = 0; c < handlingThreads.length; i++) {

                  int idx = i % handlingThreads.length;
                  i++;

                  HandlingThread handlingThread = handlingThreads[idx];

                  if (handlingThread != null && handlingThread.addJob(key)) {

                    synchronized (handlingThread) {
                      dataHandler.locked = true; //single thread is deciding on this
                      handlingThread.notifyAll();
                    }

                    break;
                  }
                }
              }
            }
          } catch (CancelledKeyException ex) {
            log.fine("Key already closed.");
            close(key);
          } catch (IOException ex) {
            log.log(Level.SEVERE, null, ex);
            close(key);
          }
        }
      }

      //selectionKeys.clear();
    }

  }

  /**
   * @return the acceptSelector
   */
  public Selector getAcceptSelector() {
    return acceptSelector;
  }
}
