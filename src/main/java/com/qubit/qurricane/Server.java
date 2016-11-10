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
  
  public final static String SERVER_VERSION = "1.4.0";
  
  private static final int THREAD_JOBS_SIZE;
  private static final int THREADS_POOL_SIZE;
  private static final int DEFAULT_BUFFER_SIZE = 64 * 1024;
  private static final int MAX_IDLE_TOUT = 3 * 1000; // miliseconds
  private static final int MAX_MESSAGE_SIZE_DEFAULTS = 64 * 1024 * 1024; // 64 MB
  static final int BUF_GROWING_LIMIT = 64 * 1024;

  static {
    THREADS_POOL_SIZE = 
      Math.max(2, Runtime.getRuntime().availableProcessors() -1);
    THREAD_JOBS_SIZE = 64;
  }
  
//  public static Log log = new Log(Server.class);
  public static final String POOL = "pool";
  public static final String POOL_SHARED = "pool-shared";
  public static final String QUEUE = "queue";
  public static final String QUEUE_SHARED = "queue-shared";
  
  private final int port;
  private long delayForNoIOReadsInSuite = 1;
  private boolean blockingReadsAndWrites = false;
  private long acceptDelay = 0;
  private final InetSocketAddress listenAddress;
  private final String address;
  private ServerSocketChannel serverChannel;
  private int jobsPerThread = THREAD_JOBS_SIZE;
  private int threadsAmount = THREADS_POOL_SIZE;
  private int maxGrowningBufferChunkSize = DEFAULT_BUFFER_SIZE;
  private int maxMessageSize = MAX_MESSAGE_SIZE_DEFAULTS;
  private long defaultIdleTime = MAX_IDLE_TOUT;
  private long defaultAcceptIdleTime = MAX_IDLE_TOUT * 2;
  private String poolType = POOL;
  private int dataHandlerWriteBufferSize = BUF_GROWING_LIMIT;
  private long singlePoolPassThreadDelay = 0;

 
  private final Map<String, Handler> plainPathHandlers = new HashMap<>();
  private final List<Handler> matchingPathHandlers = new ArrayList<>();
  private boolean notAllowingMoreAcceptsThanSlots = false;
  private boolean stoppingNow = false;
  private MainAcceptAndDispatchThread mainAcceptDispatcher;
  private boolean started = false;
  private boolean cachingBuffers = true;
  
  public Server(String address, int port) {
    this.port = port;
    this.address = address;
    this.listenAddress = new InetSocketAddress(address, port);
  }

  public void start() throws IOException {
    
    if (this.started) {
      log.info("Server already started.");
      return;
    }
    
    this.started = true;
    this.serverChannel = ServerSocketChannel.open();
    serverChannel.configureBlocking(false);
    ServerSocket socket = serverChannel.socket();
//    socket.setPerformancePreferences(maxMessageSize, port, port);
    socket.bind(listenAddress);

    // @todo move to cfg
    
    this.setHandlingThreads(new HandlingThread[this.getThreadsAmount()]);
    
    Selector acceptSelector = Selector.open();
    
    serverChannel.register(acceptSelector, SelectionKey.OP_ACCEPT);
    
    this.mainAcceptDispatcher = 
            new MainAcceptAndDispatchThread(
                    this,
                    acceptSelector, this.getDefaultAcceptIdleTime());

    this.setupThreadsList();

    mainAcceptDispatcher.setAcceptDelay(this.getAcceptDelay());
    mainAcceptDispatcher.setNotAllowingMoreAcceptsThanSlots(
            this.isNotAllowingMoreAcceptsThanSlots());
    mainAcceptDispatcher.start();
 
    log.log(Level.INFO,
            "Server starting at {0} on port {1}\nPool type: {2}", 
            new Object[]{
              listenAddress.getHostName(),
              port,
              this.getPoolType()});
  }

  public void stop() throws IOException {
    if (!this.started) {
      log.info("Server is not started.");
      return;
    }
    if (this.stoppingNow) {
      log.info("Server is being stopped. Please wait.");
      return;
    }
    
    this.stoppingNow = true;
    
    try {
      this.mainAcceptDispatcher.setRunning(false); // help it to finish
      this.mainAcceptDispatcher = null;
      for (int j = 0; j < handlingThreads.length; j++) {
        handlingThreads[j].setRunning(false); // help thread to finish
        handlingThreads[j] = null; // remove thread
      }

      serverChannel.close();
    } finally {
      this.stoppingNow = false;
      this.started = false;
    }
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
    boolean announced = false;
    String type = this.getPoolType();
    int jobsSize = this.getJobsPerThread();
    int bufSize = this.maxGrowningBufferChunkSize;
    int defaultMaxMessage = this.getMaxMessageSize();
    
    for (int i = 0; i < handlingThreads.length; i++) {
      HandlingThread t;
      
      switch (type) {
        case POOL:
          if (!announced) {
            log.info("Atomic Array  Pools type used.");
            announced = true;
          } t = new HandlingThreadPooled(
                  this,
                  jobsSize,
                  bufSize,
                  defaultMaxMessage,
                  defaultIdleTime);
          break;
        case QUEUE:
          if (!announced) {
            log.info("Concurrent Queue Pools type used.");
            announced = true;
          } t = new HandlingThreadQueued(
                  this,
                  jobsSize,
                  bufSize,
                  defaultMaxMessage,
                  defaultIdleTime);
          break;
        case QUEUE_SHARED:
          if (!announced) {
            log.info("Shared Concurrent Queue Pools type used.");
            announced = true;
          } t = new HandlingThreadSharedQueue(
                  this,
                  jobsSize,
                  bufSize,
                  defaultMaxMessage,
                  defaultIdleTime);
          break;
        default:
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
      channel.close();
    } catch (Exception ex) {
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
   * @return the notAllowingMoreAcceptsThanSlots
   */
  public boolean isNotAllowingMoreAcceptsThanSlots() {
    return notAllowingMoreAcceptsThanSlots;
  }

  /**
   * @param allowingMoreAcceptsThanSlots the notAllowingMoreAcceptsThanSlots to set
   */
  public void setNotAllowingMoreAcceptsThanSlots(
          boolean allowingMoreAcceptsThanSlots) {
    this.notAllowingMoreAcceptsThanSlots = allowingMoreAcceptsThanSlots;
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

  /**
   * @return the acceptDelay
   */
  public long getAcceptDelay() {
    return acceptDelay;
  }

  /**
   * @param acceptDelay the acceptDelay to set
   */
  public void setAcceptDelay(long acceptDelay) {
    this.acceptDelay = acceptDelay;
  }

  /**
   * @return the defaultAcceptIdleTime
   */
  public long getDefaultAcceptIdleTime() {
    return defaultAcceptIdleTime;
  }

  /**
   * @param defaultAcceptIdleTime the defaultAcceptIdleTime to set
   */
  public void setDefaultAcceptIdleTime(long defaultAcceptIdleTime) {
    this.defaultAcceptIdleTime = defaultAcceptIdleTime;
  }
  
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
    for (int i = 0; i < handlingThreads.length; i++) {
      if (handlingThreads[i] == thread) {
        handlingThreads[i] = null;
      }
    }
  }

  boolean hasThreads() {
    for (HandlingThread handlingThread : handlingThreads) {
      if (handlingThread != null) {
        return true;
      }
    }
    return false;
  }
  
  /**
   * Runs DataHandler.setGeneralGlobalHandlingHooks(...)
   * @param hooks 
   */
  public static void setGeneralGlobalHandlingHooks(
          GeneralGlobalHandlingHooks hooks) {
    DataHandler.setGeneralGlobalHandlingHooks(hooks);
  }

  /**
   * @return the maxGrowningBufferChunkSize
   */
  public int getMaxGrowningBufferChunkSize() {
    return maxGrowningBufferChunkSize;
  }

  /**
   * @param maxGrowningBufferChunkSize the maxGrowningBufferChunkSize to set
   */
  public void setMaxGrowningBufferChunkSize(int maxGrowningBufferChunkSize) {
    this.maxGrowningBufferChunkSize = maxGrowningBufferChunkSize;
  }

  /**
   * @return the cachingBuffers
   */
  public boolean isCachingBuffers() {
    return cachingBuffers;
  }

  /**
   * @param cacheBuffers the cachingBuffers to set
   */
  public void setCachingBuffers(boolean cacheBuffers) {
    this.cachingBuffers = cacheBuffers;
  }
}
