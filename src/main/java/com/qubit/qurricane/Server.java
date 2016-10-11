/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_READ;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Server {
  
  final static Logger log = Logger.getLogger(Server.class.getName());
  
  public final static String SERVER_VERSION = "1.0.1";
  
  private static final int THREAD_JOBS_SIZE = 64;
  private static final int THREADS_POOL_SIZE = 16;
  private static final int DEFAULT_BUFFER_SIZE = 4 * 1024;
  public static final int MAX_IDLE_TOUT = 5 * 1000; // miliseconds
  public static final int MAX_MESSAGE_SIZE_DEFAULTS = 64 * 1024 * 1024; // 10 MB

//  public static Log log = new Log(Server.class);

  private final int port;

  private final InetSocketAddress listenAddress;
  private final String address;
  private boolean readPreparatorSet;
  private ServerSocketChannel serverChannel;
  private int jobsPerThread = THREAD_JOBS_SIZE;
  private int threadsAmount = THREADS_POOL_SIZE;
  private int requestBufferSize = DEFAULT_BUFFER_SIZE;
  private int maxMessageSize = MAX_MESSAGE_SIZE_DEFAULTS;
  private long defaultIdleTime = MAX_IDLE_TOUT;
  private boolean pooled = false;
 
  private final Map<String, Handler> plainPathHandlers = new HashMap<>();
  private final List<Handler> matchingHandlersAfterPlainHandlers = 
          new ArrayList<>();
  
  public Server(String address, int port) {
    this.port = port;
    this.address = address;
    this.listenAddress = new InetSocketAddress(address, port);
  }

  public void start() throws IOException {
    
    this.serverChannel = ServerSocketChannel.open();
    serverChannel.configureBlocking(false);
    ServerSocket socket = serverChannel.socket();
//    socket.setPerformancePreferences(maxMessageSize, port, port);
    socket.bind(listenAddress);

    // @todo move to cfg
    
    MainAcceptAndDispatchThread.keepRunning = true;
    
    MainAcceptAndDispatchThread.setupThreadsList(this.getThreadsAmount(),
            this.getJobsPerThread(),
            this.getRequestBufferSize(),
            this.getMaxMessageSize(),
            this.getDefaultIdleTime(),
            this.isPooled());
    
    if (!this.readPreparatorSet) {
      this.readPreparatorSet = true;
      
      Selector acceptSelector = Selector.open();
      serverChannel.register(acceptSelector, SelectionKey.OP_ACCEPT);
      MainAcceptAndDispatchThread mainAcceptDispatcher = 
              new MainAcceptAndDispatchThread(this, acceptSelector);
      mainAcceptDispatcher.start();
    }
    
    log.info("Server starting at " + listenAddress.getHostName() +
            " on port " + port + "\nPooled: " + this.isPooled());
  }

  public void stop() throws IOException {
    MainAcceptAndDispatchThread.keepRunning = false;
    
    // wait for all to finish
    while (MainAcceptAndDispatchThread.hasThreads()) {
      try {
        Thread.sleep(5);
      } catch (InterruptedException ex) {
        log.log(Level.SEVERE, null, ex);
      }
    }
    
    serverChannel.close();
  }

  /**
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * @return the listenAddress
   */
  public InetSocketAddress getListenAddress() {
    return listenAddress;
  }

  /**
   * @return the address
   */
  public String getAddress() {
    return address;
  }

  protected static SelectionKey accept(SelectionKey key, Selector readSelector)
          throws IOException {
    // pick socketChannel channel
    ServerSocketChannel serverSocketChannel = 
            (ServerSocketChannel) key.channel();
    // trigger accept
    SocketChannel channel = serverSocketChannel.accept();

    if (channel != null) {
      channel.configureBlocking(false);
      // now register readSelector for new event type (notice 
      // in loop accept and reading events)
      return channel.register(readSelector, OP_READ);
    }
    return null;
  }

  protected static void close(SelectionKey key) {
    try {
      // this method is used on "bad occurence - to cleanup any stuff left
      // cleaning will be reviewed again
      key.cancel();
      key.channel().close();
    } catch (IOException ex) {
      log.log(Level.SEVERE, null, ex);
    }
  }

  /**
   * @return the jobsPerThread
   */
  public int getJobsPerThread() {
    return jobsPerThread;
  }

  /**
   * @param jobsPerThread the jobsPerThread to set
   */
  public void setJobsPerThread(int jobsPerThread) {
    this.jobsPerThread = jobsPerThread;
  }

  /**
   * @return the threadsAmount
   */
  public int getThreadsAmount() {
    return threadsAmount;
  }

  /**
   * @param threadsAmount the threadsAmount to set
   */
  public void setThreadsAmount(int threadsAmount) {
    this.threadsAmount = threadsAmount;
  }

  /**
   * @return the requestBufferSize
   */
  public int getRequestBufferSize() {
    return requestBufferSize;
  }

  /**
   * @param bufferSize the requestBufferSize to set
   */
  public void setRequestBufferSize(int bufferSize) {
    this.requestBufferSize = bufferSize;
  }

  /**
   * @return the maxMessageSize
   */
  public int getMaxMessageSize() {
    return maxMessageSize;
  }

  /**
   * @param maxMessageSize the maxMessageSize to set
   */
  public void setMaxMessageSize(int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  /**
   * @return the defaultIdleTime
   */
  public long getDefaultIdleTime() {
    return defaultIdleTime;
  }

  /**
   * @param defaultIdleTime the defaultIdleTime to set
   */
  public void setDefaultIdleTime(long defaultIdleTime) {
    this.defaultIdleTime = defaultIdleTime;
  }

  /**
   * @return the pooled
   */
  public boolean isPooled() {
    return pooled;
  }

  /**
   * @param pooled the pooled to set
   */
  public void setPooled(boolean pooled) {
    this.pooled = pooled;
  }
  
  public void registerHandlerByPath(String path, Handler handler) {
    plainPathHandlers.put(path, handler);
  }

  public void registerHandlerForMatching(Handler handler) {
    matchingHandlersAfterPlainHandlers.add(handler);
  }
  
  public Handler getHandlerForPath(String fullPath, String path) {
    Handler handler = plainPathHandlers.get(path);

    if (handler == null) {
      for (Handler matchingHandler : matchingHandlersAfterPlainHandlers) {
        if (matchingHandler.matches(fullPath)) {
          return matchingHandler.getInstance();
        }
      }
    } else {
      return handler.getInstance();
    }

    return null;
  }
}
