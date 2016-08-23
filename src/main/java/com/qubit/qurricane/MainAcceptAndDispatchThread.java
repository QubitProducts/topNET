/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class MainAcceptAndDispatchThread extends Thread {

  public static volatile boolean keepRunning;
  public static final List<HandlingThread> handlingThreads;

  static {
    handlingThreads = new ArrayList<>();
    keepRunning = true;
  }

  private final Selector acceptSelector;
  private Lock lock = new ReentrantLock();

  MainAcceptAndDispatchThread(final Selector acceptSelector) {
    this.acceptSelector = acceptSelector;
  }

  @Override
  public void run() {

    while (keepRunning) {
      try {
        // pick current events list:
        getAcceptSelector().select();
      } catch (IOException ex) {
        try {
          // some trouble, metrics???
          getAcceptSelector().close();
        } catch (IOException ex1) {
          // try to close 
        }
      }

      Set<SelectionKey> selectionKeys
              = getAcceptSelector().selectedKeys();

      for (SelectionKey key : selectionKeys) {
        if (key.isValid()) {
          try {
            if (key.isAcceptable()) {

              Server.accept(key, acceptSelector);

            } else {
              DataHandler dataHandler = (DataHandler) key.attachment();

              if (dataHandler == null) {
                dataHandler = new DataHandler();
                key.attach(dataHandler);
              }

              // add to worker
              if (!dataHandler.locked) {
                for (HandlingThread handlingThread : handlingThreads) {
                  if (handlingThread.addJob(key)) {
                    synchronized (handlingThread) {
                      dataHandler.locked = true; //single thread is deciding on this
                      handlingThread.notifyAll();
                    }
                    break;
                  }
                }
              }
            }
          } catch (CancelledKeyException | IOException ex) {
            try {

              key.channel().close();
              key.cancel();
            } catch (IOException ex1) {
            }
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
