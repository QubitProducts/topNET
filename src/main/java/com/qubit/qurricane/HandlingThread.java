/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import static com.qubit.qurricane.Server.BUF_SIZE;
import static com.qubit.qurricane.Server.MAX_SIZE;
import static com.qubit.qurricane.Server.TOUT;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThread extends Thread {

  private volatile SelectionKey currentJob = null;

  public HandlingThread() {
  }

  private final ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);

  @Override
  public void run() {

    try {

      MainAcceptAndDispatchThread.handlingThreads.add(this);

      {
        synchronized (this) {
          while (MainAcceptAndDispatchThread.keepRunning) {

            while (currentJob != null) {

              DataHandler dataHandler = (DataHandler) currentJob.attachment();

              try {
                // important step! skip those busy
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
                    if (this.processKey(currentJob, dataHandler)) {
                      // key not necessary anymore
                    }
                  } catch (IOException es) {
                    // @todo metrics
                  }
                }
              } finally {
                currentJob = null;
                if (dataHandler != null) {
                  dataHandler.locked = false;
                }
              }
            }

            try {

              if (currentJob == null) {
                this.wait(100);
              }

            } catch (InterruptedException ex) {

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
          int many = dataHandler.read(key, buffer);
          if (many < 0) { // if finished reading
            if (many == -2) {
              while (!dataHandler.write(key, buffer));
            }
            Server.close(key);
            return true; // can close key
          } else {
            return false;
          }
        } else {
          return true;
        }

      } catch (IOException ex) {
        return true;
      } finally {
        busy = false;
      }
    } else {
      return true;
    }
  }

  /**
   *
   * @param key
   * @return
   */
  public boolean addJob(SelectionKey key) {
    if (this.currentJob != null) {
      return false;
    }

    this.currentJob = key;

    return true;
  }

}
