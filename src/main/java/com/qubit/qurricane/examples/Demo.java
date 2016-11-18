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

import com.qubit.qurricane.BytesStream;
import com.qubit.qurricane.PoolType;
import com.qubit.qurricane.Server;
import java.io.IOException;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Demo {
  
  /**
   * Example main.
   * @param args
   * @throws IOException 
   */
  public static void main(String[] args) throws Exception {
    
    int jobs = 64;
    int bufChunkMax = 64 * 1024;
    int th = 3;
    long noIOdelay = 1;
    boolean usingSleep = false;
    BytesStream.doNotShrinkBuffersAfterJob = true;
    boolean limitedAccepts = false;
    long acceptDelay = 0;
    long breakStop = 0; // if no io delay occures, this has chance
    
    Server s = new Server("localhost", 3456);
    
    s.setJobsPerThread(jobs);
    // one byte buffer!
    s.setMaxGrowningBufferChunkSize(bufChunkMax);
    s.setThreadsAmount(th);
    s.setPoolType(PoolType.POOL);
    s.setDelayForNoIOReadsInSuite(noIOdelay);
    s.setSinglePoolPassThreadDelay(breakStop);
    s.setAcceptDelay(acceptDelay);
    s.setNotAllowingMoreAcceptsThanSlots(limitedAccepts);
    s.setUsingSleep(usingSleep);
    s.start();
    Thread.sleep(200);
    s.stop();
    s.start();
    
//    s.registerPathMatchingHandler(new PrefixToAllHandlers());
    s.registerHandlerByPath("/sleep", new SleepyHandler());
    s.registerHandlerByPath("/echo", new EchoHandler());
    s.registerHandlerByPath("/jobs", new JobsNumHandler(s));
    s.registerHandlerByPath("/dump", new DumpHandler());
    s.registerHandlerByPath("/appender", new AsyncAppenderHandler());
    
    s = new Server("localhost", 3457);

    s.setJobsPerThread(-1);
    // one byte buffer!
    s.setMaxGrowningBufferChunkSize(bufChunkMax);
    s.setThreadsAmount(th);
    s.setPoolType(PoolType.QUEUE);
    s.setDelayForNoIOReadsInSuite(noIOdelay);
    s.setSinglePoolPassThreadDelay(breakStop);
    s.setAcceptDelay(acceptDelay);
    s.setNotAllowingMoreAcceptsThanSlots(limitedAccepts);
    s.setUsingSleep(usingSleep);
    s.start();
    Thread.sleep(200);
    s.stop();
    s.start();

//    s.registerPathMatchingHandler(new PrefixToAllHandlers());
    s.registerHandlerByPath("/sleep", new SleepyHandler());
    s.registerHandlerByPath("/jobs", new JobsNumHandler(s));
    s.registerHandlerByPath("/echo", new EchoHandler());
    s.registerHandlerByPath("/dump", new DumpHandler());
    s.registerHandlerByPath("/appender", new AsyncAppenderHandler());
    
    s = new Server("localhost", 3458);
    
    s.setJobsPerThread(jobs);
    // one byte buffer!
    s.setMaxGrowningBufferChunkSize(bufChunkMax);
    s.setThreadsAmount(th);
    s.setPoolType(PoolType.QUEUE);
    s.setDelayForNoIOReadsInSuite(noIOdelay);
    s.setSinglePoolPassThreadDelay(breakStop);
    s.setAcceptDelay(acceptDelay);
    s.setNotAllowingMoreAcceptsThanSlots(limitedAccepts);
    s.setUsingSleep(usingSleep);
    s.start();
    Thread.sleep(200);
    s.stop();
    s.start();
    
//    s.registerPathMatchingHandler(new PrefixToAllHandlers());
    s.registerHandlerByPath("/sleep", new SleepyHandler());
    s.registerHandlerByPath("/jobs", new JobsNumHandler(s));
    s.registerHandlerByPath("/echo", new EchoHandler());
    s.registerHandlerByPath("/dump", new DumpHandler());
    s.registerHandlerByPath("/appender", new AsyncAppenderHandler());
    
    s = new Server("localhost", 3459);
    
    s.setJobsPerThread(jobs);
    // one byte buffer!
    s.setMaxGrowningBufferChunkSize(bufChunkMax);
    s.setThreadsAmount(th);
    s.setPoolType(PoolType.QUEUE_SHARED);
    s.setDelayForNoIOReadsInSuite(noIOdelay);
    s.setSinglePoolPassThreadDelay(breakStop);
    s.setAcceptDelay(acceptDelay);
    s.setNotAllowingMoreAcceptsThanSlots(limitedAccepts);
    s.setUsingSleep(usingSleep);
    s.start();
    Thread.sleep(200);
    s.stop();
    s.start();
    
//    s.registerPathMatchingHandler(new PrefixToAllHandlers());
    s.registerHandlerByPath("/sleep", new SleepyHandler());
    s.registerHandlerByPath("/echo", new EchoHandler());
    s.registerHandlerByPath("/jobs", new JobsNumHandler(s));
    s.registerHandlerByPath("/dump", new DumpHandler());
    s.registerHandlerByPath("/appender", new AsyncAppenderHandler());
  }
}
