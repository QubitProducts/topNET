/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class MainAcceptAndDispatchThread extends Thread {

  public static volatile boolean keepRunning;
  public static final List<HandlingThread> handlingThreads;

  public static final ConcurrentLinkedQueue<SelectionKey> keysQueue
          = new ConcurrentLinkedQueue<>();

  static {
    handlingThreads = new ArrayList<>();
    keepRunning = true;
  }

  private final Selector serverChannelSelector;
  private Lock lock = new ReentrantLock();

  MainAcceptAndDispatchThread(final Selector serverChannelSelector) {
    this.serverChannelSelector = serverChannelSelector;
  }

  @Override
  public void run() {
    
      while (keepRunning) {
        try {
          // pick current events list:
          getServerChannelSelector().select();
        } catch (IOException ex) {
          try {
            // some trouble, metrics???
            getServerChannelSelector().close();
          } catch (IOException ex1) {
            // try to close 
          }
          continue;
        }

        Set<SelectionKey> selectionKeys = 
                getServerChannelSelector().selectedKeys();

        Iterator<SelectionKey> keysIterator = selectionKeys.iterator();

        while (keysIterator.hasNext()) {
          if (keysIterator.hasNext()) {
            SelectionKey key = keysIterator.next();

            if (key.isValid()) {
              if (key.isAcceptable()) {

                try {
                  // maybe move to handlingThreads too?
                  Server.accept(key, this.getServerChannelSelector());
                } catch (IOException ex) {
                  // @todo metrics?
                }

              } else if (key.isReadable()) {
                // reads will be handler by handlingThreads
                // @todo consider queueing data handlers instead
                DataHandler dataHandler = (DataHandler) key.attachment();
                
                if (dataHandler == null) {
                  dataHandler = new DataHandler();
                  key.attach(dataHandler);
                }

                if (!dataHandler.isQueued()) {
                  dataHandler.setQueued(true);
                  keysQueue.add(key);
                }
              }
            }
          }
        }
        selectionKeys.clear();
      }

  }

  /**
   *
   * @param key
   * @return true only if key was processed.
   * @throws IOException
   */
  protected boolean processKey(SelectionKey key)
          throws IOException {

    return true;
  }

  /**
   * @return the serverChannelSelector
   */
  public Selector getServerChannelSelector() {
    return serverChannelSelector;
  }
}
