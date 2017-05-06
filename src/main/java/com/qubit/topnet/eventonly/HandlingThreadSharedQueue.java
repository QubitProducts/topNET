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

package com.qubit.topnet.eventonly;

import com.qubit.topnet.DataHandler;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class HandlingThreadSharedQueue extends HandlingThreadQueued {

  private final static ConcurrentLinkedDeque<DataHandler> jobs = 
          new ConcurrentLinkedDeque<>();
  
  public HandlingThreadSharedQueue(
          EventTypeServer server,
          int jobsSize,
          int bufSize,
          long defaultMaxMessageSize,
          long maxIdle) {
    super(server, jobsSize, bufSize, defaultMaxMessageSize, maxIdle);
    syncPlease = true;
  }

  @Override
  public ConcurrentLinkedDeque<DataHandler> getJobs() {
    return HandlingThreadSharedQueue.jobs;
  }

  private static volatile int limit = 16;
  
  @Override
  public int getLimit() {
    return HandlingThreadSharedQueue.limit;
  }
  /**
   * @param limit the limit to set
   */
  @Override
  public void setLimit(int limit) {
    HandlingThreadSharedQueue.limit = limit;
  }
}
