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

import static com.qubit.qurricane.PoolType.POOL;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
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
public final class Server {
  
  final static Logger log = Logger.getLogger(Server.class.getName());
  
  private HandlingThread[] handlingThreads;
  
  public final static String SERVER_VERSION = "1.5.0";
  
  private static final int THREAD_JOBS_SIZE;
  private static final int THREADS_POOL_SIZE;
  private static final int MAX_IDLE_TOUT = 15 * 1000; // miliseconds

  static {
    THREADS_POOL_SIZE = 
      Math.max(2, Runtime.getRuntime().availableProcessors() -1);
    THREAD_JOBS_SIZE = 64;
  }
  
//  public static Log log = new Log(Server.class);
  
  public static final int SCALING_UNLIMITED = 0;
  
  private final Map<String, Handler> plainPathHandlers = new HashMap<>();
  private final List<Handler> matchingPathHandlers = new ArrayList<>();
  private MainAcceptAndDispatchThread mainAcceptDispatcher;
  
  private final int port;
  private int delayForNoIOReadsInSuite = 1;
  private long acceptDelay = 0;
  private final InetSocketAddress listenAddress;
  private final String address;
  private ServerSocketChannel serverChannel;
  private int jobsPerThread = THREAD_JOBS_SIZE;
  private int threadsAmount = THREADS_POOL_SIZE;
  private int maxGrowningBufferChunkSize = 512 * 1024;
  private int maxMessageSize = 4 * 1024 * 1024;
  private long defaultIdleTime = MAX_IDLE_TOUT;
  private long defaultAcceptIdleTime = MAX_IDLE_TOUT * 2;
  private PoolType poolType = POOL;
  private int singlePoolPassThreadDelay = 0;
  private boolean waitingForReadEvents = true;
  private long scalingDownTryPeriodMS = 20 * 1000;
  private boolean stoppingNow = false;
  private boolean started = false;
  private boolean cachingBuffers = true;
  private LimitsHandler limitsHandler;
  private boolean puttingJobsEquallyToAllThreads = true;
  private boolean autoscalingThreads = true;
  private int noSlotsAvailableTimeout = 5;
  private int scalingMax = SCALING_UNLIMITED;  // unlimited
  private boolean autoScalingDown = true;
  private boolean cachingThreads = true;
  private int defaultHeaderSizeLimit = 16 * 1024;
  private int channelReceiveBufferSize = -1;
  private int channelSendBufferSize = -1;
  private ServerSocket serverSocket;
  private int connectionTimePerformancePref = 0;
  private int latencyPerformancePref = 0;
  private int bandwithPerformancePref = 0;
  
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

    this.serverSocket = serverChannel.socket();

    this.serverSocket.setPerformancePreferences(this.getConnectionTimePerformancePref(), this.getLatencyPerformancePref(), this.getBandwithPerformancePref());
    
    if (this.getChannelReceiveBufferSize() > 0) {
      this.serverSocket.setReceiveBufferSize(this.channelReceiveBufferSize);
    }
    
    this.serverSocket.bind(listenAddress);

    // @todo move to cfg
    
    this.setHandlingThreads(new HandlingThread[this.getThreadsAmount()]);
    
    Selector acceptSelector = Selector.open();
    
    serverChannel.register(acceptSelector, SelectionKey.OP_ACCEPT);
    
    this.mainAcceptDispatcher = 
            new MainAcceptAndDispatchThread(
                    this,
                    acceptSelector, this.getDefaultAcceptIdleTime());

    log.info("Threads handling type used: " + this.getPoolType().name());
    this.setupThreadsList();

    mainAcceptDispatcher.setAcceptDelay(this.getAcceptDelay());
    
    mainAcceptDispatcher.setWaitingForReadEvents(this.waitingForReadEvents);
    
    mainAcceptDispatcher.setNoSlotsAvailableTimeout(
        this.getNoSlotsAvailableTimeout());
    
    mainAcceptDispatcher.setScalingDownTryPeriodMS(this.scalingDownTryPeriodMS);
    
    mainAcceptDispatcher.setAutoScalingDown(this.isAutoScalingDown());
    
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
      this.clearThreadsCache();
      this.serverChannel.close();
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

  protected SocketChannel accept()
          throws IOException {
    SocketChannel channel = this.serverChannel.accept();

    if (channel != null) {

      channel.configureBlocking(false);

      if (this.channelSendBufferSize > 0) {
        channel.socket().setSendBufferSize(this.channelSendBufferSize);
      }

      return channel;
    }
    
    return null;
  }

  
  private void setupThreadsList() {
    int len = handlingThreads.length;
    for (int i = 0; i < len; i++) {
      this.addThreadDirectly();
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
   * @return the singlePoolPassThreadDelay
   */
  public int getSinglePoolPassThreadDelay() {
    return singlePoolPassThreadDelay;
  }

  /**
   * @param singlePoolPassThreadDelay the singlePoolPassThreadDelay to set
   */
  public void setSinglePoolPassThreadDelay(int singlePoolPassThreadDelay) {
    this.singlePoolPassThreadDelay = singlePoolPassThreadDelay;
  }

  /**
   * @return the delayForNoIOReadsInSuite
   */
  public int getDelayForNoIOReadsInSuite() {
    return delayForNoIOReadsInSuite;
  }

  /**
   * @param delayForNoIOReadsInSuite the delayForNoIOReadsInSuite to set
   */
  public void setDelayForNoIOReadsInSuite(int delayForNoIOReadsInSuite) {
    this.delayForNoIOReadsInSuite = delayForNoIOReadsInSuite;
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
  public synchronized void setHandlingThreads(HandlingThread[] aHandlingThreads) {
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

  /**
   * @return the waitingForReadEvents
   */
  public boolean isWaitingForReadEvents() {
    return waitingForReadEvents;
  }

  /**
   * @param waitingForReadEvents the waitingForReadEvents to set
   */
  public void setWaitingForReadEvents(boolean waitingForReadEvents) {
    this.waitingForReadEvents = waitingForReadEvents;
  }

  /**
   * @return the limitsHandler
   */
  public LimitsHandler getLimitsHandler() {
    return limitsHandler;
  }

  /**
   * @param limitsHandler the limitsHandler to set
   */
  public void setLimitsHandler(LimitsHandler limitsHandler) {
    this.limitsHandler = limitsHandler;
  }

  /**
   * @return the puttingJobsEquallyToAllThreads
   */
  public boolean isPuttingJobsEquallyToAllThreads() {
    return puttingJobsEquallyToAllThreads;
  }

  /**
   * @param puttingJobsEquallyToAllThreads the puttingJobsEquallyToAllThreads to set
   */
  public void setPuttingJobsEquallyToAllThreads(boolean puttingJobsEquallyToAllThreads) {
    this.puttingJobsEquallyToAllThreads = puttingJobsEquallyToAllThreads;
  }

  /**
   * @return the mainAcceptDispatcher
   */
  public MainAcceptAndDispatchThread getMainAcceptDispatcher() {
    return mainAcceptDispatcher;
  }
  
  public boolean addThread() {
    if (this.scalingMax > 0 && 
        handlingThreads.length >= this.scalingMax) {
      return false;
    }
    
    return this.addThreadDirectly();
  }
  
  private boolean addThreadDirectly() {
    
    int idx = -1;
    for (int i= 0; i < handlingThreads.length; i++) {
      if (handlingThreads[i] == null) {
        idx = i;
        break;
      }
    }
    
    if (idx == -1) {
      if (this.isAutoscalingThreads()) {
        HandlingThread[] newArray = 
            new HandlingThread[handlingThreads.length + 1];

        System.arraycopy(handlingThreads, 0,
                         newArray, 0,
                         handlingThreads.length);

        idx = handlingThreads.length;
        // update reference
        this.setHandlingThreads(newArray);
      } else {
        return false;
      }
    }
    
    handlingThreads[idx] = this.getCachedOrNewThread();
    
    return true;
  }

  public int cleanupThreadsExcess() {
    int threadsThatShouldBe = this.getThreadsAmount();
    HandlingThread[] threads = this.getHandlingThreads();
    
    if (threadsThatShouldBe >= threads.length) {
      return 0;
    }
    
    double jobs = 0;
    double max = 0;
    
    for (HandlingThread thread : threads) {
      if (thread != null) {
        jobs += thread.jobsLeft();
        max += thread.getLimit();
      }
    }
    
    int threadsRequired = (int) ((jobs/max) * threads.length) + 1;
    
    if (threadsRequired >= threads.length) {
      return 0;
    }
    
    if (threadsRequired < threadsThatShouldBe) {
      threadsRequired = threadsThatShouldBe;
    }
    
    int threadsToRemove = threads.length - threadsRequired;
    
    if (threadsToRemove > 0) {
      for (int i = 0; i < threadsToRemove; i++) {
        HandlingThread thread = threads[threads.length - (i + 1)];
        threads[threads.length - (i + 1)] = null;
        if (this.isCachingThreads()) {
          putThreadToCache(thread);
//          thread.wakeup();
        } else {
          thread.setRunning(false);
        }
      }

      HandlingThread[] newThreads = new HandlingThread[threadsRequired];

      for (int i = 0; i < threads.length; i++) {
        if (threads[i] != null) {
          newThreads[i] = threads[i];
        }
      }

      this.setHandlingThreads(newThreads);

      return threadsToRemove;
    }
    
    return 0;
  }
  
  /**
   * @return the autoscalingThreads
   */
  public boolean isAutoscalingThreads() {
    return autoscalingThreads;
  }

  /**
   * @param autoscalingThreads the autoscalingThreads to set
   */
  public void setAutoscalingThreads(boolean autoscalingThreads) {
    this.autoscalingThreads = autoscalingThreads;
  }

  /**
   * @return the noSlotsAvailableTimeout
   */
  public int getNoSlotsAvailableTimeout() {
    return noSlotsAvailableTimeout;
  }

  /**
   * @param noSlotsAvailableTimeout the noSlotsAvailableTimeout to set
   */
  public void setNoSlotsAvailableTimeout(int noSlotsAvailableTimeout) {
    this.noSlotsAvailableTimeout = noSlotsAvailableTimeout;
  }

  /**
   * @return the poolType
   */
  public PoolType getPoolType() {
    return poolType;
  }

  /**
   * @param poolType the poolType to set
   */
  public void setPoolType(PoolType poolType) {
    this.poolType = poolType;
  }

  /**
   * @return the scalingDownTryPeriodMS
   */
  public long getScalingDownTryPeriodMS() {
    return scalingDownTryPeriodMS;
  }

  /**
   * @param scalingDownTryPeriodMS the scalingDownTryPeriodMS to set
   */
  public void setScalingDownTryPeriodMS(long scalingDownTryPeriodMS) {
    this.scalingDownTryPeriodMS = scalingDownTryPeriodMS;
  }

  /**
   * @return the scalingMax
   */
  public int getScalingMax() {
    return scalingMax;
  }

  /**
   * @param scalingMax the scalingMax to set
   */
  public void setScalingMax(int scalingMax) {
    this.scalingMax = scalingMax;
  }

  /**
   * @return the autoScalingDown
   */
  public boolean isAutoScalingDown() {
    return autoScalingDown;
  }

  /**
   * @param autoScalingDown the autoScalingDown to set
   */
  public void setAutoScalingDown(boolean autoScalingDown) {
    this.autoScalingDown = autoScalingDown;
  }

  private HandlingThread getNewThread() {
    int jobsSize = this.getJobsPerThread();
    int bufSize = this.maxGrowningBufferChunkSize;
    int defaultMaxMessage = this.getMaxMessageSize();
    HandlingThread t;

    switch (this.poolType) {
      case POOL:
        t = new HandlingThreadPooled(
            this,
            jobsSize,
            bufSize,
            defaultMaxMessage,
            defaultIdleTime);
        break;
      case QUEUE:
        t = new HandlingThreadQueued(
            this,
            jobsSize,
            bufSize,
            defaultMaxMessage,
            defaultIdleTime);
        break;
      case QUEUE_SHARED:
        t = new HandlingThreadSharedQueue(
            this,
            jobsSize,
            bufSize,
            defaultMaxMessage,
            defaultIdleTime);
        break;
      default:
        throw new RuntimeException(
            "Unknown thread handling type selected: " + this.poolType);
    }

    t.setSinglePassDelay(this.singlePoolPassThreadDelay);
    t.setDelayForNoIO(this.getDelayForNoIOReadsInSuite());

    t.start();
    
    return t;
  }

  private final ArrayDeque<HandlingThread> threadsCache = new ArrayDeque<>();
  
  public HandlingThread getCachedOrNewThread() {
    HandlingThread t = threadsCache.pollFirst();
    if (t != null) {
      return t;
    } else {
      return this.getNewThread();
    }
  }
  
  public void putThreadToCache(HandlingThread t) {
    if (t != null) {
      threadsCache.addLast(t);
    }
  }
  
  public void clearThreadsCache() {
    HandlingThread t;
    while ((t = threadsCache.pollFirst())  != null) {
      t.setRunning(false);
      t.wakeup();
    }
  }

  /**
   * @return the cachingThreads
   */
  public boolean isCachingThreads() {
    return cachingThreads;
  }

  /**
   * @param cachingThreads the cachingThreads to set
   */
  public void setCachingThreads(boolean cachingThreads) {
    this.cachingThreads = cachingThreads;
  }

  /**
   * @return the defaultHeaderSizeLimit
   */
  public int getDefaultHeaderSizeLimit() {
    return defaultHeaderSizeLimit;
  }

  /**
   * @param defaultHeaderSizeLimit the defaultHeaderSizeLimit to set
   */
  public void setDefaultHeaderSizeLimit(int defaultHeaderSizeLimit) {
    this.defaultHeaderSizeLimit = defaultHeaderSizeLimit;
  }

  /**
   * @return the channelBufferSize
   */
  public int getChannelReceiveBufferSize() {
    return channelReceiveBufferSize;
  }

  /**
   * @param channelBufferSize the channelBufferSize to set
   */
  public void setChannelReceiveBufferSize(int channelBufferSize) {
    this.channelReceiveBufferSize = channelBufferSize;
  }

  /**
   * @return the serverChannel
   */
  public ServerSocketChannel getServerChannel() {
    return serverChannel;
  }

  /**
   * @return the serverSocket
   */
  public ServerSocket getServerSocket() {
    return serverSocket;
  }

  /**
   * @return the channelSendBufferSize
   */
  public int getChannelSendBufferSize() {
    return channelSendBufferSize;
  }

  /**
   * @param channelSendBufferSize the channelSendBufferSize to set
   */
  public void setChannelSendBufferSize(int channelSendBufferSize) {
    this.channelSendBufferSize = channelSendBufferSize;
  }

  /**
   * @return the connectionTimePerformancePref
   */
  public int getConnectionTimePerformancePref() {
    return connectionTimePerformancePref;
  }

  /**
   * @param connectionTimePerformancePref the connectionTimePerformancePref to set
   */
  public void setConnectionTimePerformancePref(int connectionTimePerformancePref) {
    this.connectionTimePerformancePref = connectionTimePerformancePref;
  }

  /**
   * @return the latencyPerformancePref
   */
  public int getLatencyPerformancePref() {
    return latencyPerformancePref;
  }

  /**
   * @param latencyPerformancePref the latencyPerformancePref to set
   */
  public void setLatencyPerformancePref(int latencyPerformancePref) {
    this.latencyPerformancePref = latencyPerformancePref;
  }

  /**
   * @return the bandwithPerformancePref
   */
  public int getBandwithPerformancePref() {
    return bandwithPerformancePref;
  }

  /**
   * @param bandwithPerformancePref the bandwithPerformancePref to set
   */
  public void setBandwithPerformancePref(int bandwithPerformancePref) {
    this.bandwithPerformancePref = bandwithPerformancePref;
  }
}
