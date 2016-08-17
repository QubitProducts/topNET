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
import java.nio.channels.Selector;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThread extends Thread {

  public HandlingThread(Selector serverChannelSelector) {
  }

  @Override
  public void run() {

    try {

      MainAcceptAndDispatchThread.handlingThreads.add(this);

      {
        
        
        while (MainAcceptAndDispatchThread.keepRunning) {

          SelectionKey key;

          while ((key = keysQueue.poll()) != null) {
            //selectionKeys.remove(key);
            DataHandler dataHandler = (DataHandler) key.attachment();
            
            if (dataHandler != null) {
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
              // important moment - selector loop will know to add again theis key
              // to queue by setting it
              dataHandler.setQueued(false);
            }
          }
        }
        
        
      }
      
    } finally {
      MainAcceptAndDispatchThread.handlingThreads.remove(this);
    }
  }

  public volatile boolean busy = false;

  private boolean processKey(SelectionKey key, DataHandler dataHandler)
          throws IOException {
    if (key.isValid()) {
      try {
        busy = true;
        if (key.isReadable()) {
          dataHandler.read(key);
          return true;
        }
      } catch (IOException ex) {
        // some trouble, metrics???
      } finally {
        busy = false;
      }
    }

    return false;
  }

}
