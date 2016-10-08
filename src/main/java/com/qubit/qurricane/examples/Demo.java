/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane.examples;

import static com.qubit.qurricane.Handler.registerHandlerByPath;
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
  public static void main(String[] args) throws IOException {    
    Server s = new Server("localhost", 3456);
    
    s.setJobsPerThread(20000);
    // one byte buffer!
    s.setRequestBufferSize(8192);
    s.setThreadsAmount(4);
    s.start();
    
    registerHandlerByPath("/echo", new EchoHandler());
    registerHandlerByPath("/appender", new AsyncAppenderHandler());
  }
}
