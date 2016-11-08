/*
 * Qurrican
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

package com.qubit.qurricane.examples;

import com.qubit.qurricane.Handler;
import com.qubit.qurricane.HandlingThread;
import com.qubit.qurricane.Request;
import com.qubit.qurricane.Response;
import com.qubit.qurricane.Server;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class JobsNumHandler extends Handler {
  static List<Server> servers = new ArrayList<>();

  public JobsNumHandler(){}
  
  public JobsNumHandler(Server s) {
    servers.add(s);
  }

  @Override
  public boolean process(Request request, Response response) 
          throws Exception {
    int counts = 0;
    for (Server server : servers) {
      int count = 0;
      response.print("SERVER " + counts++ + ":");
      for (HandlingThread handlingThread : server.getHandlingThreads()) {
        response.print("  [" + (count++) + "] jobs: " +
            handlingThread.getValidJobs().size() + "\n");
      }
      response.print("\n");
    }
    return true;
  }

}
