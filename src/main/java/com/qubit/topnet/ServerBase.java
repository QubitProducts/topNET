/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.topnet;

import static com.qubit.topnet.PoolType.POOL;
import com.qubit.topnet.eventonly.HandlingThread;
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
 * @author piotr
 */
public abstract class ServerBase {

  final static Logger log = Logger.getLogger(ServerBase.class.getName());
  
  private static boolean tellingConnectionClose = false;

  public final static String SERVER_VERSION = "1.5.2";

  private static final int THREAD_JOBS_SIZE;
  private static final int THREADS_POOL_SIZE;
  public static final int MAX_IDLE_TOUT = 15 * 1000; // miliseconds

  static {
    THREADS_POOL_SIZE
        = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    THREAD_JOBS_SIZE = 64;
  }

  /**
   * Runs DataHandler.setGeneralGlobalHandlingHooks(...)
   *
   * @param hooks
   */
  public static void setGeneralGlobalHandlingHooks(
      GeneralGlobalHandlingHooks hooks) {
    DataHandler.setGeneralGlobalHandlingHooks(hooks);
  }
  
//  public static Log log = new Log(Server.class);
  public static final int SCALING_UNLIMITED = 0;

  public int port = -1;
  private long acceptDelay = 0;
  private InetSocketAddress listenAddress = null;
  private String address = null;
  private ServerSocketChannel serverChannel;
  private int jobsPerThread = THREAD_JOBS_SIZE;
  private int threadsAmount = THREADS_POOL_SIZE;
  private int maxGrowningBufferChunkSize = 512 * 1024;
  private int maxMessageSize = 16 * 1024 * 1024;
  private long defaultIdleTime = MAX_IDLE_TOUT;
  private PoolType poolType = POOL;
  private boolean waitingForReadEvents = true;
  private long scalingDownTryPeriodMS = 10 * 1000;
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
  private char[] protocol;
  private Selector channelSelector;

  public final Map<String, Handler> plainPathHandlers = new HashMap<>();
  public final List<Handler> matchingPathHandlers = new ArrayList<>();

  public ServerBase(String address, int port) {
    this.port = port;
    this.address = address;
    this.listenAddress = new InetSocketAddress(address, port);
  }
  
  /**
   * @return the tellingConnectionClose
   */
  public static boolean isTellingConnectionClose() {
    return tellingConnectionClose;
  }

  /**
   * @param aTellingConnectionClose the tellingConnectionClose to set
   */
  public static void setTellingConnectionClose(boolean aTellingConnectionClose) {
    tellingConnectionClose = aTellingConnectionClose;
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

  public static void close(SocketChannel channel) {
    try {
      channel.close();
    } catch (Exception ex) {
      log.log(Level.SEVERE, null, ex);
    }
  }
  
  public static void close(SelectionKey key, SocketChannel channel) {
    try {
      channel.close();
    } catch (Exception ex) {
    } finally {
      key.cancel();
    }
  }
  
  public SocketChannel accept()
      throws IOException {
    SocketChannel channel = this.getServerChannel().accept();

    if (channel != null) {

      channel.configureBlocking(false);

      if (this.getChannelSendBufferSize() > 0) {
        channel.socket().setSendBufferSize(this.getChannelSendBufferSize());
      }

      return channel;
    }

    return null;
  }

  /**
   * @return the jobsPerThread
   */
  public int getJobsPerThreadValue() {
    if (this.getPoolType() == PoolType.QUEUE_SHARED) {
      return getJobsPerThread() * this.getThreadsAmount();
    } else {
      return getJobsPerThread();
    }
  }

  abstract public void removeThread(AbstractHandlingThread thread);

  abstract public boolean hasThreads();
  
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
   * @param maxGrowningBufferChunkSize the maxGrowningBufferChunkSize to set
   */
  public void setMaxGrowningBufferChunkSize(int maxGrowningBufferChunkSize) {
    this.maxGrowningBufferChunkSize = maxGrowningBufferChunkSize;
  }

  /**
   * @return the maxGrowningBufferChunkSize
   */
  public int getMaxGrowningBufferChunkSize() {
    return maxGrowningBufferChunkSize;
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
   * @param puttingJobsEquallyToAllThreads the puttingJobsEquallyToAllThreads to
   * set
   */
  public void setPuttingJobsEquallyToAllThreads(boolean puttingJobsEquallyToAllThreads) {
    this.puttingJobsEquallyToAllThreads = puttingJobsEquallyToAllThreads;
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
   * @param connectionTimePerformancePref the connectionTimePerformancePref to
   * set
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

  public char[] getProtocol() {
    return this.protocol;
  }

  /**
   * @param protocol the protocol to set
   */
  public void setProtocol(char[] protocol) {
    this.setProtocol(protocol);
  }

  public Selector getChannelSelector() {
    return this.channelSelector;
  }

  abstract public AbstractHandlingThread[] getAllHandlingThreads();

  /**
   * @param serverSocket the serverSocket to set
   */
  public void setServerSocket(ServerSocket serverSocket) {
    this.serverSocket = serverSocket;
  }

  /**
   * @return the stoppingNow
   */
  public boolean isStoppingNow() {
    return stoppingNow;
  }

  /**
   * @param stoppingNow the stoppingNow to set
   */
  public void setStoppingNow(boolean stoppingNow) {
    this.stoppingNow = stoppingNow;
  }

  /**
   * @return the started
   */
  public boolean isStarted() {
    return started;
  }

  /**
   * @param started the started to set
   */
  public void setStarted(boolean started) {
    this.started = started;
  }

  /**
   * @param channelSelector the channelSelector to set
   */
  public void setChannelSelector(Selector channelSelector) {
    this.channelSelector = channelSelector;
  }

  /**
   * @param serverChannel the serverChannel to set
   */
  public void setServerChannel(ServerSocketChannel serverChannel) {
    this.serverChannel = serverChannel;
  }
}
