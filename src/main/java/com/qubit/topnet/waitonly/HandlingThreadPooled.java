/*
 * topNET
 * Fast HTTP Server Solution.
 * Copyright 2016, Qubit Group <www.qubit.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  
 * If not, see <https://www.gnu.org/licenses/lgpl-3.0.en.html>
 * 
 * Author: Peter Fronc <peter.fronc@qubitdigital.com>
 */

package com.qubit.topnet.waitonly;

import com.qubit.topnet.DataHandler;
import com.qubit.topnet.DataHandlerHolder;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class HandlingThreadPooled extends HandlingThread {

  private final DataHandlerHolder[] jobs;
  
  private final long maxIdle;

  private static final Logger log
          = Logger.getLogger(HandlingThreadPooled.class.getName());

  public HandlingThreadPooled(
          WaitTypeServer server,
          int jobsSize,
          int bufSize,
          long defaultMaxMessageSize,
          long maxIdle) {
    super(server);
    jobs = new DataHandlerHolder[jobsSize];
    for (int i = 0; i < jobs.length; i++) {
      jobs[i] = new DataHandlerHolder();
    }
    
    this.setDefaultMaxMessageSize(defaultMaxMessageSize);
    this.maxIdle = maxIdle;
  }

  @Override
  public boolean runSinglePass() {
    boolean waitForIO = true;
    
    for (int i = 0; i < highestJobNr; i++) {
      DataHandler dataHandler = this.jobs[i].dataHandler;

      if (dataHandler != null && dataHandler.owningThread == this) {
        boolean isFinished = true;
        SocketChannel channel = dataHandler.getChannel();
        
        try {
          if (this.handleMaxIdle(dataHandler, maxIdle)) {
            waitForIO = false;
            continue;
          }

          int processed = this.processJob(dataHandler);

          if (processed < 0) {
            waitForIO = false;
            // job not necessary anymore
          } else {
            // keep job
            if (processed > 0) {
              waitForIO = false;
            } else {
              // == 0 means no reads were done, or finished reads and asked to wait
            }
            isFinished = false;
          }
        } catch (IOException ioe) {
          log.log(Level.INFO, "IOException during handling data - probably connection lost, enter FINE level for more details.");
          log.log(Level.FINE, "Exception during handling data.", ioe);
          isFinished = true;
        } catch (Throwable es) {
          log.log(Level.SEVERE, "Exception during handling data.", es);
          isFinished = true;
        } finally {
          if (isFinished) {
            //key cancell!
            try {
              WaitTypeServer.close(channel);
              dataHandler.connectionClosedHandler();
            } finally {
              this.removeJobFromPool(i);
            }
          }
        }
      }
    }
    
    return waitForIO;
  }
  
  protected volatile int highestJobNr = 0;
  
  @Override
  public boolean addJob(SocketChannel channel, Long ts) {
    for (int i = 0; i < this.jobs.length; i++) {
      DataHandler job = this.jobs[i].dataHandler;
      if (job == null) {
        job = new DataHandler(this.server, channel);
        job.owningThread = this;
        job.setAcceptAndRunHandleStarted(ts);
        jobsAdded++;
        this.jobs[i].dataHandler = job;
        
        highestJobNr = Math.max(highestJobNr, i + 1);
        
        wakeup();

        return true;
      } else if (job.owningThread == null) {
        job.reset();
        job.init(this.server, channel);
        jobsAdded++;
        job.owningThread = this;
        job.setAcceptAndRunHandleStarted(ts);
                
        if (i >= highestJobNr) {
          highestJobNr = i + 1;
        }
        
        wakeup();
        
        return true;
      }
    }
    
    wakeup();
    
    return false;
  }

  @Override
  public boolean hasJobs() {
    return this.jobsLeft() > 0;
  }

  private void removeJobFromPool(int i) {
    if (this.server.isCachingBuffers()) {
      this.jobs[i].dataHandler.owningThread = null;
    } else {
      this.jobs[i].dataHandler = null;
    }
    jobsRemoved++;
  }

  @Override
  public boolean canAddJob() {
    return this.jobsLeft() < this.jobs.length;
  }

  @Override
  public List<DataHandler> getValidJobs() {
    List<DataHandler> tmp = new ArrayList<>();
    for (DataHandlerHolder job : jobs) {
      if (job.dataHandler != null && job.dataHandler.owningThread != null) {
        tmp.add(job.dataHandler);
      }
    }
    return tmp;
  }

  @Override
  public int getLimit() {
    return this.jobs.length;
  }

}
