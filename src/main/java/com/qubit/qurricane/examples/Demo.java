/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane.examples;

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
    
    int jobs = 128;
    int buf = 8192;
    int th = 8;
    long delay = 10;
    long acceptDelay = 0;
    long breakStop = 0;
    
    Server s = new Server("localhost", 3456);
    
    s.setJobsPerThread(jobs);
    // one byte buffer!
    s.setRequestBufferSize(buf);
    s.setThreadsAmount(th);
    s.setPoolType("pool");
    s.setDelayForNoIOReadsInSuite(delay);
    s.setSinglePoolPassThreadDelay(breakStop);
    s.setAcceptDelay(acceptDelay);
    s.start();
    
    s.registerPathMatchingHandler(new PrefixToAllHandlers());
    s.registerHandlerByPath("/echo", new EchoHandler());
    s.registerHandlerByPath("/appender", new AsyncAppenderHandler());
    
    // second version
    
    s = new Server("localhost", 3457);
    
    s.setJobsPerThread(jobs);
    // one byte buffer!
    s.setRequestBufferSize(buf);
    s.setThreadsAmount(th);
    s.setPoolType("queue");
    s.setDelayForNoIOReadsInSuite(delay);
    s.setSinglePoolPassThreadDelay(breakStop);
    s.setAcceptDelay(acceptDelay);
    s.start();
    Thread.sleep(200);
    s.stop();
    s.start();
    
    s.registerPathMatchingHandler(new PrefixToAllHandlers());
    s.registerHandlerByPath("/echo", new EchoHandler());
    s.registerHandlerByPath("/appender", new AsyncAppenderHandler());
    
    s = new Server("localhost", 3458);
    
    s.setJobsPerThread(jobs);
    // one byte buffer!
    s.setRequestBufferSize(buf);
    s.setThreadsAmount(th);
    s.setPoolType("queue-shared");
    s.setDelayForNoIOReadsInSuite(delay);
    s.setSinglePoolPassThreadDelay(breakStop);
    s.setAcceptDelay(acceptDelay);
    s.start();
    
    s.registerPathMatchingHandler(new PrefixToAllHandlers());
    s.registerHandlerByPath("/echo", new EchoHandler());
    s.registerHandlerByPath("/appender", new AsyncAppenderHandler());
  }
}
