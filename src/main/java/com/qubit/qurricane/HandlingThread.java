/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import static com.qubit.qurricane.MainAcceptAndDispatchThread.keysQueue;
import static com.qubit.qurricane.Server.MAX_SIZE;
import static com.qubit.qurricane.Server.TOUT;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_WRITE;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThread extends Thread {

  private final Selector serverChannelSelector;

  public HandlingThread(Selector serverChannelSelector) {
    this.serverChannelSelector = serverChannelSelector;
  }

  @Override
  public void run() {

    try {

      MainAcceptAndDispatchThread.handlingThreads.add(this);

      {

        while (MainAcceptAndDispatchThread.keepRunning) {

          SelectionKey key;

          while ((key = keysQueue.peek()) != null) {
            //selectionKeys.remove(key);
            DataHandler dataHandler = (DataHandler) key.attachment();

            if (dataHandler == null) {
              keysQueue.remove(key);
            } else if (dataHandler.getLock().tryLock()) {
              // important step! skip those busy
              try {
                keysQueue.remove(key);

                //check if connection is not open too long! Prevent DDoS
                if (dataHandler.getTouch() < System.currentTimeMillis() - TOUT) {
                  ///Server.close(key);
                }
                // check if not too large
                if (dataHandler.getSize() > MAX_SIZE) {
                  ///Server.close(key);
                }

                try {
                  this.processKey(key, dataHandler);
                } catch (IOException es) {
                  // @todo metrics
                }
              } finally {
                dataHandler.getLock().unlock();
                dataHandler.canNotAddToQueue = false;
              }
            } else {
            }
          }
        }
      }
    } finally {
      MainAcceptAndDispatchThread.handlingThreads.remove(this);
    }
  }

  public volatile boolean busy = false;

  private void processKey(SelectionKey key, DataHandler dataHandler)
          throws IOException {
    if (key.isValid()) {
      try {
        busy = true;
        if (key.isReadable()) {
          if (dataHandler.read(key)) { // if finished reading
            while(!dataHandler.write(key));
            Server.close(key); 
          }
        } else if (key.isWritable()) {
          if (dataHandler.write(key)) {
            key.cancel();
            key.channel().close(); // done
          }
        }
      } catch (IOException ex) {
        // some trouble, metrics???
      } finally {
        busy = false;
      }
    }
  }

}
