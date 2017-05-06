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

import com.qubit.topnet.BytesStream;
import com.qubit.topnet.PoolType;
import com.qubit.topnet.examples.AsyncAppenderHandler;
import com.qubit.topnet.examples.DumpHandler;
import com.qubit.topnet.examples.EchoHandler;
import com.qubit.topnet.examples.JobsNumHandler;
import com.qubit.topnet.examples.SleepyHandler;
import com.qubit.topnet.plugins.filesserve.FilesBrowserHandler;

import java.io.IOException;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Demo {

  /**
   * Example main.
   *
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws Exception {

    int jobs = 16;

    int bufChunkMax = 2 * 32 * 1024;

    int channelBufSize = -1;//4 * 1024 * 1024;
    int channelWriteBufSize = -1;//4 * 1024 * 1024;
    int th = 3;

    BytesStream.setShrinkingBuffersAfterJob(false);
    boolean scalingDown = true;
    boolean autoScaling = true;

    EventTypeServer s = new EventTypeServer("localhost", 4456);

    s.setJobsPerThread(jobs);
    // one byte buffer!
    s.setMaxFromContentSizeBufferChunkSize(bufChunkMax);
    s.setMinimumThreadsAmount(th);
    s.setPoolType(PoolType.POOL);
    s.setChannelReceiveBufferSize(channelBufSize);
    s.setChannelSendBufferSize(channelWriteBufSize);
    s.setAutoscalingThreads(autoScaling);
    s.setAutoScalingDown(scalingDown);
    s.start();
    Thread.sleep(200);
    s.stop();
    s.start();

    //s.registerPathMatchingHandler(new PrefixToAllHandlers());
    s.registerHandlerByPath("/sleep", new SleepyHandler());
    s.registerHandlerByPath("/echo", new EchoHandler());
    s.registerHandlerByPath("/jobs", new JobsNumHandler(s));
    s.registerHandlerByPath("/dump", new DumpHandler());
    s.registerHandlerByPath("/appender", new AsyncAppenderHandler());
    s.registerMatchingHandler(new FilesBrowserHandler("/browser", "./"));

    s = new EventTypeServer("localhost", 4457);

    s.setJobsPerThread(-1);
    // one byte buffer!
    s.setMaxFromContentSizeBufferChunkSize(bufChunkMax);
    s.setMinimumThreadsAmount(th);
    s.setPoolType(PoolType.QUEUE);
    s.setChannelReceiveBufferSize(channelBufSize);
    s.setChannelSendBufferSize(channelWriteBufSize);
    s.setAutoScalingDown(scalingDown);
    s.start();
    Thread.sleep(200);
    s.stop();
    s.start();

    //s.registerPathMatchingHandler(new PrefixToAllHandlers());
    s.registerHandlerByPath("/sleep", new SleepyHandler());
    s.registerHandlerByPath("/jobs", new JobsNumHandler(s));
    s.registerHandlerByPath("/echo", new EchoHandler());
    s.registerHandlerByPath("/dump", new DumpHandler());
    s.registerHandlerByPath("/appender", new AsyncAppenderHandler());
    s.registerMatchingHandler(new FilesBrowserHandler("/browser", "./"));

    s = new EventTypeServer("localhost", 4458);

    s.setJobsPerThread(jobs);
    // one byte buffer!
    s.setMaxFromContentSizeBufferChunkSize(bufChunkMax);
    s.setMinimumThreadsAmount(th);
    s.setPoolType(PoolType.QUEUE);
    s.setChannelReceiveBufferSize(channelBufSize);
    s.setChannelSendBufferSize(channelWriteBufSize);
    s.setAutoScalingDown(scalingDown);
    s.start();
    Thread.sleep(200);
    s.stop();
    s.start();

    //s.registerPathMatchingHandler(new PrefixToAllHandlers());
    s.registerHandlerByPath("/sleep", new SleepyHandler());
    s.registerHandlerByPath("/jobs", new JobsNumHandler(s));
    s.registerHandlerByPath("/echo", new EchoHandler());
    s.registerHandlerByPath("/dump", new DumpHandler());
    s.registerHandlerByPath("/appender", new AsyncAppenderHandler());
    s.registerMatchingHandler(new FilesBrowserHandler("/browser", "./"));

    s = new EventTypeServer("localhost", 4459);

    s.setJobsPerThread(jobs);
    // one byte buffer!
    s.setMaxFromContentSizeBufferChunkSize(bufChunkMax);
    s.setMinimumThreadsAmount(2 * th);
    s.setPoolType(PoolType.QUEUE_SHARED);
    s.setChannelReceiveBufferSize(channelBufSize);
    s.setChannelSendBufferSize(channelWriteBufSize);
    s.setAutoScalingDown(scalingDown);
    s.start();
    Thread.sleep(200);
    s.stop();
    s.start();

    //s.registerPathMatchingHandler(new PrefixToAllHandlers());
    s.registerHandlerByPath("/sleep", new SleepyHandler());
    s.registerHandlerByPath("/echo", new EchoHandler());
    s.registerHandlerByPath("/jobs", new JobsNumHandler(s));
    s.registerHandlerByPath("/dump", new DumpHandler());
    s.registerHandlerByPath("/appender", new AsyncAppenderHandler());
    s.registerMatchingHandler(new FilesBrowserHandler("/browser", "./"));

  }
}
