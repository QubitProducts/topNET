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
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class HandlingThread extends Thread {

//  private volatile SelectionKey[] jobs = new SelectionKey[16];
  private AtomicReferenceArray<SelectionKey> jobs = 
          new AtomicReferenceArray<SelectionKey>(16);

  public HandlingThread() {
  }

  private final ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);

  @Override
  public void run() {

    try {

      MainAcceptAndDispatchThread.handlingThreads.add(this);

      {

        while (MainAcceptAndDispatchThread.keepRunning) {

          while (this.hasJobs()) {
            for (int i = 0; i < this.jobs.length(); i++) {
              SelectionKey job = this.jobs.get(i);
              if (job != null) {
                DataHandler dataHandler = (DataHandler) job.attachment();

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
                      if (this.processKey(job, dataHandler)) {
                        // key not necessary anymore
                      }
                    } catch (IOException es) {
                      // @todo metrics
                    }
                  }
                } finally {
                  this.jobs.set(i, null);
                  if (dataHandler != null) {
                    dataHandler.locked = false;
                  }
                }
              }
            }
          }
          
          try {
            synchronized (this) {
              if (!this.hasJobs()) {
                this.wait(100);
              }
            }
          } catch (InterruptedException ex) {

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
    for (int i = 0; i < this.jobs.length(); i++) {
      SelectionKey job = this.jobs.get(i);
      if (job == null) {
        this.jobs.set(i, key);
        return true;
      }
    }

    return false;
  }

  private boolean hasJobs() {
    for (int i = 0; i < this.jobs.length(); i++) {
      if (this.jobs.get(i) != null) {
        return true;
      }
    }
    return false;
  }

}
