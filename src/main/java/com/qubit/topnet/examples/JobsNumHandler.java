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

package com.qubit.topnet.examples;

import com.qubit.topnet.AbstractHandlingThread;
import com.qubit.topnet.Handler;
import com.qubit.topnet.Request;
import com.qubit.topnet.Response;
import com.qubit.topnet.ServerBase;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class JobsNumHandler extends Handler {
  static List<ServerBase> servers = new ArrayList<>();

  public JobsNumHandler(){}
  
  public JobsNumHandler(ServerBase s) {
    servers.add(s);
  }

  @Override
  public boolean process(Request request, Response response) 
          throws Exception {
    int counts = 0;
    for (ServerBase server : servers) {
      int count = 0;
      response.print("SERVER " + counts++ + ":\n");
      for (AbstractHandlingThread handlingThread : server.getAllHandlingThreads()) {
        response.print("  [" + (count++) + "] jobs: " +
            handlingThread.getValidJobs().size() + 
            " [TA:" + handlingThread.getJobsAdded() + 
            ", TR: " + handlingThread.getJobsRemoved()
            + "] \n");
      }
      response.print("\n");
    }
    return true;
  }

}
