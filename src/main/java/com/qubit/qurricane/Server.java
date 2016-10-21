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
  
  private HandlingThread[] handlingThreads;
  
  public final static String SERVER_VERSION = "1.1.0";
  
  private static final int THREAD_JOBS_SIZE = 64;
  private static final int THREADS_POOL_SIZE = 16;
  private static final int DEFAULT_BUFFER_SIZE = 4 * 1024;
  public static final int MAX_IDLE_TOUT = 5 * 1000; // miliseconds
  public static final int MAX_MESSAGE_SIZE_DEFAULTS = 64 * 1024 * 1024; // 10 MB
  private volatile boolean serverRunning;
  private long delayForNoIOReadsInSuite = 0;
  private boolean blockingReadsAndWrites = false;

  /**
   * @return the handlingThreads
   */
  public HandlingThread[] getHandlingThreads() {
    return handlingThreads;
  }

  /**
   * @param aHandlingThreads the handlingThreads to set
   */
  public void setHandlingThreads(HandlingThread[] aHandlingThreads) {
    handlingThreads = aHandlingThreads;
  }

  void removeThread(HandlingThread thread) {
    HandlingThread[] handlingThreads = this.getHandlingThreads();
    for (int i = 0; i < handlingThreads.length; i++) {
      if (handlingThreads[i] == thread) {
        handlingThreads[i] = null;
      }
    }
  }

  boolean hasThreads() {
    HandlingThread[] handlingThreads = this.getHandlingThreads();
    for (int i = 0; i < handlingThreads.length; i++) {
      if (handlingThreads[i] != null) {
        return true;
      }
    }
    return false;
  }
  
//  public static Log log = new Log(Server.class);

  private final int port;

  private final InetSocketAddress listenAddress;
  private final String address;
  private ServerSocketChannel serverChannel;
  private int jobsPerThread = THREAD_JOBS_SIZE;
  private int threadsAmount = THREADS_POOL_SIZE;
  private int requestBufferSize = DEFAULT_BUFFER_SIZE;
  private int maxMessageSize = MAX_MESSAGE_SIZE_DEFAULTS;
  private long defaultIdleTime = MAX_IDLE_TOUT;
  private String poolType = "pool";
  private int dataHandlerWriteBufferSize = 4096;
  private long singlePoolPassThreadDelay = 0;

 
  private final Map<String, Handler> plainPathHandlers = new HashMap<>();
  private final List<Handler> matchingPathHandlers = 
          new ArrayList<>();
  private boolean allowingMoreAcceptsThanSlots = false;
  
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
    
    this.setHandlingThreads(new HandlingThread[this.getThreadsAmount()]);
    
    Selector acceptSelector = Selector.open();
    
    serverChannel.register(acceptSelector, SelectionKey.OP_ACCEPT);
    
    MainAcceptAndDispatchThread mainAcceptDispatcher = 
            new MainAcceptAndDispatchThread(this, acceptSelector);

    this.setServerRunning(true);

    this.setupThreadsList();

    mainAcceptDispatcher.setAllowingMoreAcceptsThanSlots(
            this.isAllowingMoreAcceptsThanSlots());
    mainAcceptDispatcher.start();
 
    log.info("Server starting at " + listenAddress.getHostName() +
            " on port " + port + "\nPool type: " + this.getPoolType());
  }

  public void stop() throws IOException {
    this.setServerRunning(false);
    
    // wait for all to finish
    while (this.hasThreads()) {
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

  protected SocketChannel accept(SelectionKey key, Selector readSelector)
          throws IOException {
    // pick socketChannel channel
    ServerSocketChannel serverSocketChannel = 
            (ServerSocketChannel) key.channel();
    // trigger accept
    SocketChannel channel = serverSocketChannel.accept();

    if (channel != null) {
      channel.configureBlocking(this.isBlockingReadsAndWrites());
      // now register readSelector for new event type (notice 
      // in loop accept and reading events)
      return channel;
    }
    return null;
  }

  
  private void setupThreadsList() {
    
    HandlingThread[] handlingThreads = this.getHandlingThreads();
    
    boolean announced = false;
    String type = this.getPoolType();
    int jobsSize = this.getJobsPerThread();
    int bufSize = this.getRequestBufferSize();
    int defaultMaxMessage = this.getMaxMessageSize();
    
    for (int i = 0; i < handlingThreads.length; i++) {
      HandlingThread t;
      
      if (type.equals("pool")) {
        if (!announced) {
          log.info("Atomic Array  Pools type used.");
          announced = true;
        }
        t = new HandlingThreadPooled(
                this,
                jobsSize,
                bufSize,
                defaultMaxMessage,
                defaultIdleTime);
      } else if (type.equals("queue")) {
        if (!announced) {
          log.info("Concurrent Queue Pools type used.");
          announced = true;
        }
        t = new HandlingThreadQueued(
                this,
                jobsSize,
                bufSize,
                defaultMaxMessage,
                defaultIdleTime);
      } else if (type.equals("queue-shared")) {
        if (!announced) {
          log.info("Shared Concurrent Queue Pools type used.");
          announced = true;
        }
        t = new HandlingThreadSharedQueue(
                this,
                jobsSize,
                bufSize,
                defaultMaxMessage,
                defaultIdleTime);
      } else {
        throw new RuntimeException(
                "Unknown thread handling type selected: " + type);
      }
      
      t.setSinglePassDelay(this.singlePoolPassThreadDelay);
      t.setDelayForNoIO(this.getDelayForNoIOReadsInSuite());
      
      t.start();
      
      handlingThreads[i] = t;
    }
  }
  
  protected static void close(SocketChannel channel) {
    try {
      // this method is used on "bad occurence - to cleanup any stuff left
      // cleaning will be reviewed again
//      key.cancel();
      channel.close();
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
  
  public void registerHandlerByPath(String path, Handler handler) {
    plainPathHandlers.put(path, handler);
  }

  public void registerPathMatchingHandler(Handler handler) {
    matchingPathHandlers.add(handler);
  }
  
  public Handler getHandlerForPath(
          String fullPath, String path, String params) {
    Handler handler = null;

    for (Handler matchingHandler : matchingPathHandlers) {
      if (matchingHandler.matches(fullPath, path, params)) {
        Handler instance = matchingHandler.getInstance();
        if (handler == null) {
          handler = instance;
        } else {
          handler.setNext(instance);
          handler = instance;
        }
      }
    }

    Handler plainHandler = plainPathHandlers.get(path);
    if (plainHandler != null) {
      if (handler == null) {
        handler = plainHandler.getInstance();
      } else {
        handler.setNext(plainHandler.getInstance());
      }
    }

    return handler;
  }
  
  
  /**
   * @return the dataHandlerWriteBufferSize
   */
  public int getDataHandlerWriteBufferSize() {
    return dataHandlerWriteBufferSize;
  }

  /**
   * @param dataHandlerWriteBufferSize the dataHandlerWriteBufferSize to set
   */
  public void setDataHandlerWriteBufferSize(int dataHandlerWriteBufferSize) {
    this.dataHandlerWriteBufferSize = dataHandlerWriteBufferSize;
  }

  /**
   * @return the poolType
   */
  public String getPoolType() {
    return poolType;
  }

  /**
   * @param poolType the poolType to set
   */
  public void setPoolType(String poolType) {
    this.poolType = poolType;
  }

  /**
   * @return the allowingMoreAcceptsThanSlots
   */
  public boolean isAllowingMoreAcceptsThanSlots() {
    return allowingMoreAcceptsThanSlots;
  }

  /**
   * @param allowingMoreAcceptsThanSlots the allowingMoreAcceptsThanSlots to set
   */
  public void setAllowingMoreAcceptsThanSlots(
          boolean allowingMoreAcceptsThanSlots) {
    this.allowingMoreAcceptsThanSlots = allowingMoreAcceptsThanSlots;
  }

  /**
   * @return the singlePoolPassThreadDelay
   */
  public long getSinglePoolPassThreadDelay() {
    return singlePoolPassThreadDelay;
  }

  /**
   * @param singlePoolPassThreadDelay the singlePoolPassThreadDelay to set
   */
  public void setSinglePoolPassThreadDelay(long singlePoolPassThreadDelay) {
    this.singlePoolPassThreadDelay = singlePoolPassThreadDelay;
  }

  /**
   * @return the serverRunning
   */
  public boolean isServerRunning() {
    return serverRunning;
  }

  /**
   * @param serverRunning the serverRunning to set
   */
  public void setServerRunning(boolean serverRunning) {
    this.serverRunning = serverRunning;
  }

  /**
   * @return the delayForNoIOReadsInSuite
   */
  public long getDelayForNoIOReadsInSuite() {
    return delayForNoIOReadsInSuite;
  }

  /**
   * @param delayForNoIOReadsInSuite the delayForNoIOReadsInSuite to set
   */
  public void setDelayForNoIOReadsInSuite(long delayForNoIOReadsInSuite) {
    this.delayForNoIOReadsInSuite = delayForNoIOReadsInSuite;
  }

  /**
   * @return the blockingReadsAndWrites
   */
  public boolean isBlockingReadsAndWrites() {
    return blockingReadsAndWrites;
  }

  /**
   * @param blockingReadsAndWrites the blockingReadsAndWrites to set
   */
  public void setBlockingReadsAndWrites(boolean blockingReadsAndWrites) {
    this.blockingReadsAndWrites = blockingReadsAndWrites;
  }
}
