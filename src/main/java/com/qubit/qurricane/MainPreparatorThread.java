/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class MainPreparatorThread extends Thread {

  public static volatile int counter;
  public static volatile boolean keepRunning;
  public static final List<HandlingThread> handlers;

  static {
    handlers = new LinkedList<>();
    counter = 0;
    keepRunning = true;
  }

  private final Selector serverChannelSelector;
  public final int threadNum;
  private Lock lock = new ReentrantLock();

  MainPreparatorThread(final Selector serverChannelSelector) {
    this.serverChannelSelector = serverChannelSelector;
    this.threadNum = counter;
  }

  @Override
  public void run() {
    counter++;

    try {

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

        Set<SelectionKey> selectionKeys = getServerChannelSelector().selectedKeys();

        Iterator<SelectionKey> keysIterator = selectionKeys.iterator();

        while (keysIterator.hasNext()) {
          
          for (HandlingThread handler : handlers) {
            
            if (keysIterator.hasNext()) {
              
              SelectionKey key = keysIterator.next();

              if (key.isValid()) {
                if (key.isAcceptable()) {
                  
                  try {
                    // maybe move to handlers too?
                    Server.accept(key, this.getServerChannelSelector());

                  } catch (IOException ex) {
                    // @todo metrics?
                  }
                  
                } else if (key.isReadable()) {
                  // reads will be handler by handlers
                  if (key.attachment() == null) {
                    key.attach(new DataHandler());
                    handler.addKey(key);
                  }
                  
                }
              }
            }
          }
        }
      }

    } finally {
      counter--;
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
