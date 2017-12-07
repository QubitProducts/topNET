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
package com.qubit.topnet;

import static com.qubit.topnet.PoolType.POOL;
import com.qubit.topnet.errors.ErrorHandlingConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * public static final int HTTP_0_9 = 0; public static final int HTTP_1_0 = 1;
 * public static final int HTTP_1_1 = 2; public static final int HTTP_1_x = 3;
 *
 * @author peter.fronc@qubit.com
 */
public abstract class ServerBase {

  private final static Logger log = Logger.getLogger(ServerBase.class.getName());

  /**
   * Deprecated HTTP/0.9 protocol marker.
   */
  public static final int HTTP_0_9 = 0;
  /**
   * HTTP/1.0 protocol marker.
   */
  public static final int HTTP_1_0 = 1;
  /**
   * HTTP/1.1 protocol marker.
   */
  public static final int HTTP_1_1 = 2;
  /**
   * Flexible HTTP/1.x protocol marker (not all clients understand this).
   */
  public static final int HTTP_1_X = 3;

  private static boolean tellingConnectionClose = false;
  private static final int THREAD_JOBS_SIZE;
  private static final int THREADS_POOL_SIZE;
  
  static {
    THREADS_POOL_SIZE
        = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    THREAD_JOBS_SIZE = 64;
  }

  public static final int MAX_IDLE_TOUT = 15 * 1000; // miliseconds
  public static final int SCALING_UNLIMITED = 0;
  public static final long DEFAULT_MAX_MESSAGE_SIZE = 16 * 1024 * 1024;

  /**
   * Runs DataHandler.setGeneralGlobalHandlingHooks(...)
   *
   * @param hooks
   */
  public static void setGeneralGlobalHandlingHooks(
      GeneralGlobalHandlingHooks hooks) {
    DataHandler.setGeneralGlobalHandlingHooks(hooks);
  }

  public static Charset getCharsetForName(String name) {
    try {
      return Charset.forName(name);
    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      return Charset.defaultCharset();
    }
  }
  
  private Charset headerCharset = getCharsetForName("ISO-8859-1");
  private Charset urlCharset = getCharsetForName("ISO-8859-1");

  private long maxResponseBufferFillSize = 8 * 32 * 1024;
  private int port = -1;
  
  private InetSocketAddress listenAddress = null;
  private String address = null;
  private ServerSocketChannel serverChannel;
  
  private int jobsPerThread = THREAD_JOBS_SIZE;
  private int minimumThreadsAmount = THREADS_POOL_SIZE;
  private int maxFromContentSizeBufferChunkSize = 2 * 32 * 1024;
  
  private long maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
  private long defaultIdleTime = MAX_IDLE_TOUT;
  private long scalingDownTryPeriodMS = 10 * 1000;
  
  private PoolType poolType = POOL;
  
  private boolean waitingForReadEvents = true;
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

  private ServerSocket serverSocket;

  // channel native buffers sizes
  private int channelReceiveBufferSize = -1;
  private int channelSendBufferSize = -1;
  
  //performance native
  private int connectionTimePerformancePref = 0;
  private int latencyPerformancePref = 1;
  private int bandwithPerformancePref = 8;
  
  private long defaultAcceptIdleTime = MAX_IDLE_TOUT * 2;
  private int protocol = -1;
  private Selector channelSelector;

  /**
   * HashMap containing plain path mapping to handlers.
   * Example of a pair: <"/echo", new EchoHandler()>
   */
  public final Map<String, Handler> plainPathHandlers = new HashMap<>();
  /**
   * Ordered list containing handlers used to include handlers by their 
   * {@link Handler#matches(String fullPath, String path, String params)} function response.
   */
  public final List<Handler> matchingHandlers = new ArrayList<>();

  /**
   * Make sure in any server implementation to keep all worker threads
   * references up to date in this list.
   */
  protected final List<AbstractHandlingThread> allRegisteringHandlingThreads
      = new ArrayList<>();
  
  private ErrorHandlingConfig errorHandlingConfig = new ErrorHandlingConfig();

  /**
   * Abstract base server template class.
   * This server class is the core for all settings and basic handlers registry.
   *
   * @param address INET address string
   * @param port local port number to use
   */
  public ServerBase(String address, int port) {
    this.port = port;
    this.address = address;
    this.listenAddress = new InetSocketAddress(address, port);
  }

  /**
   * Property is telling topNET if by default "close-connection" header should be sent 
   * with reply.
   * 
   * Use with care - some load balancers like Google do not handle this option 
   * properly.
   * 
   * Default false.
   * @return the tellingConnectionClose
   */
  public static boolean isTellingConnectionClose() {
    return tellingConnectionClose;
  }

  /**
   * If set to true, server will be by default adding "connection-close" 
   * famous header.
   * 
   * Use with care.
   * 
   * @param aTellingConnectionClose the tellingConnectionClose to set
   */
  public static void setTellingConnectionClose(boolean aTellingConnectionClose) {
    tellingConnectionClose = aTellingConnectionClose;
  }

  /**
   * Returns server port that is used.
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Returns INET socket listening at.
   * @return the listenAddress
   */
  public InetSocketAddress getListenAddress() {
    return listenAddress;
  }

  /**
   * Returns INET address listening at.
   * @return the listenAddress
   */
  public String getAddress() {
    return address;
  }

  /**
   * Returns how many jobs can handling thread have (maximum).
   * If returns -1 (queue type handlers) - no limit is set.
   * @return the jobsPerThread
   */
  public int getJobsPerThread() {
    return jobsPerThread;
  }

  /**
   * Sets how many jobs can handling thread have (maximum).
   * If set to -1 (queue type handlers) - handling thread will have no limits in
   * jobs handling amount.
   * 
   * Each job is the incoming request handling stream from a connection.
   * In HTTP/1.1 mode, one job is re-used for many requests.
   * 
   * @param jobsPerThread the jobsPerThread to set
   */
  public void setJobsPerThread(int jobsPerThread) {
    this.jobsPerThread = jobsPerThread;
  }

  /**
   * Returns MINIMAL amount of handling threads.
   * If autoscaling is disabled - server will keep constant threads amount equal 
   * to this value.
   * @return the minimalThreadsAmount
   */
  public int getMinimumThreadsAmount() {
    return minimumThreadsAmount;
  }

  /**
   * Sets MINIMAL amount of handling threads.
   * If autoscaling is disabled - server will keep constant threads amount equal 
   * to this value.
   * @param threadsAmount the minimalThreadsAmount to set
   */
  public void setMinimumThreadsAmount(int threadsAmount) {
    this.minimumThreadsAmount = threadsAmount;
  }

  /**
   * Returns default maximum message size in bytes. Message size is entire HTTP 
   * message size including headers and body.
   * This value can be adjusted by handler by overriding {@link Handler#getMaxIncomingDataSize()}.
   * 
   * Default value is 16MB.
   * 
   * @return the maxMessageSize
   */
  public long getMaxMessageSize() {
    return maxMessageSize;
  }

  /**
   * Sets default maximum message size in bytes. Message size is entire HTTP 
   * message size including headers and body.
   * This value can be adjusted by handler by overriding {@link Handler#getMaxIncomingDataSize()}.
   * 
   * Default value is 16MB.
   * 
   * @param maxMessageSize the maxMessageSize to set
   */
  public void setMaxMessageSize(long maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  /**
   * Returns idle time after accepting connection and HTTP message reading's 
   * been started.
   * @return the defaultIdleTime
   */
  public long getDefaultIdleTime() {
    return defaultIdleTime;
  }

  /**
   * Sets idle time after accepting connection and HTTP message reading's 
   * been started.
   * @param defaultIdleTime the defaultIdleTime to set
   */
  public void setDefaultIdleTime(long defaultIdleTime) {
    this.defaultIdleTime = defaultIdleTime;
  }

  /**
   * Utility function to close socket channel immediately.
   * @param channel SocketChannel instance.
   */
  public static void close(SocketChannel channel) {
    try {
      channel.close();
    } catch (Exception ex) {
      log.log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Utility function for closing socket channel and selection key immediately 
   * immediately.
   * @param key SelectionKey to cancel
   * @param channel SocketChannel instance.
   */
  public static void close(SelectionKey key, SocketChannel channel) {
    try {
      channel.close();
    } catch (Exception ex) {
    } finally {
      key.cancel();
    }
  }

  /**
   * topNET accepting connection utility.
   * It is used in accept loop.
   * @return
   * @throws IOException 
   */
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
   * Gets real jobs amount per thread used by each thread at runtime.
   * It returns same value as {@link getJobsPerThread()} unless thread pool is 
   * {@link PoolType#QUEUE_SHARED} type.
   * @return the jobsPerThread
   */
  public int getJobsPerThreadValue() {
    if (this.getPoolType() == PoolType.QUEUE_SHARED) {
      return getJobsPerThread() * this.getMinimumThreadsAmount();
    } else {
      return getJobsPerThread();
    }
  }

  /**
   * Function used to remove thread from processing pool. Used internally.
   * @param thread 
   */
  abstract public void removeThread(AbstractHandlingThread thread);

  /**
   * Returns if this server has any processing thread.
   * @return 
   */
  abstract public boolean hasThreads();

  /**
   * Interesting setter configuring how large buffer chunks can grow in 
   * request buffers chain. Larger value will boost large files handling for a price
   * of memory used.
   * 
   * Buffer for messages in topNET is calculated by incoming content length,
   * if content length is larger than value set by this method, buffer will be
   * created with size of this maximum value. Any extra content will be stored 
   * in additional buffers created and added to buffer chain in BytesStream instance.
   * 
   * See also {@link BytesStream#setShrinkingBuffersAfterJob(boolean) }
   * {@link BytesStream#setDefaultBufferChunkSize(int) }
   * @param maxGrowningBufferChunkSize the maxFromContentSizeBufferChunkSize to set
   */
  public void setMaxFromContentSizeBufferChunkSize(int maxGrowningBufferChunkSize) {
    this.maxFromContentSizeBufferChunkSize = maxGrowningBufferChunkSize;
  }

  /**
   * Interesting getter returning how large buffer chunks should be in 
   * request buffers chain.
   * 
   * Buffer for messages in topNET is calculated by incoming content length,
   * if content length is larger than value set by this method, buffer will be
   * created with size of this maximum value. Any extra content will be stored 
   * in additional buffers created and added to buffer chain in BytesStream instance
   * with size of this value.
   * 
   * See also {@link BytesStream#setShrinkingBuffersAfterJob(boolean) }
   * {@link BytesStream#setDefaultBufferChunkSize(int) }
   * @return the maxFromContentSizeBufferChunkSize
   */
  public int getMaxFromContentSizeBufferChunkSize() {
    return maxFromContentSizeBufferChunkSize;
  }

  /**
   * @return the cachingBuffers
   */
  public boolean isCachingBuffers() {
    return cachingBuffers;
  }

  /**
   * If buffers should be REUSED for incoming requests - its recommended to leave
   * default value equal to 1 (unless server has very limited memory).
   * @param cacheBuffers true if buffers should be reused
   */
  public void setCachingBuffers(boolean cacheBuffers) {
    this.cachingBuffers = cacheBuffers;
  }

  /**
   * Applies only for WaitTypeServer where accept loop is designed to wait for
   * read events from selector. If set to false, jobs will be added IMMEDIATELY 
   * after accepting connection where can be immediately be checked by threads pool 
   * for incoming data.
   * 
   * For highly loaded servers this may improve throughput with slight CPU cost.
   * 
   * Default value is true.
   * 
   * @return the waitingForReadEvents
   */
  public boolean isWaitingForReadEvents() {
    return waitingForReadEvents;
  }

  /**
   * Applies only for WaitTypeServer where accept loop is designed to wait for
   * read events from selector. If set to false, jobs will be added IMMEDIATELY 
   * after accepting connection where can be immediately be checked by threads pool 
   * for incoming data.
   * 
   * For highly loaded servers this may improve throughput with slight CPU cost.
   * 
   * Default value is true.
   * 
   * @param waitingForReadEvents false if nuts should be added immediately to grinder.
   */
  public void setWaitingForReadEvents(boolean waitingForReadEvents) {
    this.waitingForReadEvents = waitingForReadEvents;
  }

  /**
   * Use to get global limits handler for handling read timouts and message 
   * size exceeding.
   * @return the limitsHandler
   */
  public LimitsHandler getLimitsHandler() {
    return limitsHandler;
  }

  /**
   * Use to set global limits handler for handling read timouts and message 
   * size exceeding.
   * @param limitsHandler the limitsHandler to set
   */
  public void setLimitsHandler(LimitsHandler limitsHandler) {
    this.limitsHandler = limitsHandler;
  }

  /**
   * If true, accept thread will distribute jobs equally across all threads.
   * If false, one by one threads untill they reach their jobs queue limit.
   * 
   * Default is true.
   * 
   * @return the puttingJobsEquallyToAllThreads
   */
  public boolean isPuttingJobsEquallyToAllThreads() {
    return puttingJobsEquallyToAllThreads;
  }

  /**
   * If true, accept thread will distribute jobs equally across all threads.
   * If false, one by one threads untill they reach their jobs queue limit.
   * 
   * Default is true.
   * 
   * @param puttingJobsEquallyToAllThreads the puttingJobsEquallyToAllThreads to
   * set
   */
  public void setPuttingJobsEquallyToAllThreads(boolean puttingJobsEquallyToAllThreads) {
    this.puttingJobsEquallyToAllThreads = puttingJobsEquallyToAllThreads;
  }

  /**
   * If server will add automatically threads when they run out of jobs space.
   * 
   * topNET can work fine with fixed threads amount, this option makes server
   * more elastic. Sometimes one will want to limit processed at time jobs amount,
   * limiting jobs amount per thread is one of ways achieving it.
   * 
   * Default is true.
   * 
   * @return the autoscalingThreads
   */
  public boolean isAutoscalingThreads() {
    return autoscalingThreads;
  }

  /**
   * If server will add automatically threads when they run out of jobs space.
   * 
   * topNET can work fine with fixed threads amount, this option makes server
   * more elastic. Sometimes one will want to limit processed at time jobs amount,
   * limiting jobs amount per thread is one of ways achieving it.
   * 
   * Default is true.
   * 
   * @param autoscalingThreads false if threads amount should stay fixed at minimum server value.
   */
  public void setAutoscalingThreads(boolean autoscalingThreads) {
    this.autoscalingThreads = autoscalingThreads;
  }

  /**
   * Tiemout in milisecond value for triggering extra threads being added by 
   * autoscaller, if for given period of time there is no slots available for 
   * a job - new thread will be added (if autoscaling is on of course).
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

  /**
   * Returns server default response protocol.
   * Default value is {@link HTTP_1_0}
   * @return 
   */
  public int getProtocol() {
    return this.protocol;
  }

  /**
   * Sets this channel response protocol, default value is {@link HTTP_1_0}
   * @param protocol the protocol to set
   */
  public void setProtocol(int protocol) {
    this.protocol = protocol;
  }

  /**
   * Returns this server's channel selector.
   * @return 
   */
  public Selector getChannelSelector() {
    return this.channelSelector;
  }

  abstract public AbstractHandlingThread[] getAllHandlingThreads();

  /**
   * @param serverSocket the serverSocket to set
   */
  protected void setServerSocket(ServerSocket serverSocket) {
    this.serverSocket = serverSocket;
  }

  /**
   * If server is now stopping.
   * @return the stoppingNow
   */
  public boolean isStoppingNow() {
    return stoppingNow;
  }

  /**
   * @param stoppingNow the stoppingNow to set
   */
  protected void setStoppingNow(boolean stoppingNow) {
    this.stoppingNow = stoppingNow;
  }

  /**
   * If server is started.
   * @return the started
   */
  public boolean isStarted() {
    return started;
  }

  /**
   * @param started the started to set
   */
  protected void setStarted(boolean started) {
    this.started = started;
  }

  /**
   * Setter for channel selector.
   * @param channelSelector the channelSelector to set.
   */
  protected void setChannelSelector(Selector channelSelector) {
    this.channelSelector = channelSelector;
  }

  /**
   * @param serverChannel the serverChannel to set.
   */
  protected void setServerChannel(ServerSocketChannel serverChannel) {
    this.serverChannel = serverChannel;
  }

  /**
   * Set this property to decide how long connection can stay iddle after accept
   * operation but without any RW operation performed (DDoS).
   * @return the defaultAcceptIdleTime
   */
  public long getDefaultAcceptIdleTime() {
    return defaultAcceptIdleTime;
  }

  /**
   * Set this property to decide how long connection can stay iddle after accept
   * operation but without any RW operation performed (DDoS).
   * @param defaultAcceptIdleTime the defaultAcceptIdleTime to set
   */
  public void setDefaultAcceptIdleTime(long defaultAcceptIdleTime) {
    this.defaultAcceptIdleTime = defaultAcceptIdleTime;
  }

  /**
   * Header charset is used for decoding headers strings - it's default value
   * is ISO-8859-1 and do not change it unless you know what you do.
   * @return the headerCharset
   */
  public Charset getHeaderCharset() {
    return headerCharset;
  }

  /**
   * Header charset is used for decoding headers strings - it's default value
   * is ISO-8859-1 and do not change it unless you know what you do.
   * @param headerCharset the headerCharset to set
   */
  public void setHeaderCharset(Charset headerCharset) {
    this.headerCharset = headerCharset;
  }

  /**
   * URL charset is used to decode URL query parameters string.
   * @return the urlCharset
   */
  public Charset getUrlCharset() {
    return urlCharset;
  }

  /**
   * URL charset is used to decode URL query parameters string.
   * @param urlCharset the urlCharset to set
   */
  public void setUrlCharset(Charset urlCharset) {
    this.urlCharset = urlCharset;
  }

  /**
   * Registers a handler to a path, a path can have one handler only registered.
   * Registering other handler with same path will cause discarding previous
   * handler.
   *
   * This method of registration does not use wild card characters like "*". To
   * use advanced matching - use {@link registerPathMatchingHandler()} method.
   *
   * Handlers matched with match function have priority over plain path
   * handlers. If there is handler registered to "*" then it will be put before
   * plain handler match.
   *
   * @param path a string describing path, example: "/echo"
   * @param handler a {@link com.qubit.topnet.Handler} instance.
   */
  public void registerHandlerByPath(String path, Handler handler) {
    plainPathHandlers.put(path, handler.getInstance());
  }

  /**
   * Advanced handler matching registration method. Handler instance registered
   * is added to `matchingPathHandlers` list as last match (order matters and is
   * used when searching for matching handlers for request). Once handler is
   * added, handler {@link com.qubit.topnet.Handler#match()} method will be used
   * to determine if the handler should be included in the handling chain for
   * request.
   *
   * Using this method on a handler already added to the list will cause moving
   * handler as last in queue.
   *
   * Note that every handler can changfe at runtime its handling chain see {
   *
   * @see com.qubit.topnet.Handler#setNext(Handler next)} for more details.
   *
   * @param handler
   */
  public void registerMatchingHandler(Handler handler) {
    for (Iterator<Handler> it = matchingHandlers.iterator();it.hasNext();) {
      Handler matchingPathHandler = it.next();
      if (handler == matchingPathHandler || 
          matchingPathHandler.getClass().equals(handler.getClass())) {
        it.remove();
      }
    }
    matchingHandlers.add(handler.getInstance());
  }

  /**
   * Function unregisters handler registered with {@link registerHandlerByPath(String path, Handler handler)}
   * @param path path string used to register handler.
   */
  public void unregisterHandlerByPath(String path) {
    plainPathHandlers.remove(path);
  }

  /**
   * Function will register handler type from processing chain.
   * Function will unregister handler from any location, simple path and 
   * matching types inclusive.
   * Unregistering is processed by comparing class types.
   * @param handler handler instance
   */
  public void unregisterHandler(Handler handler) {
    unregisterMatchingHandler(handler);

    for (Iterator<Map.Entry<String, Handler>> it
        = plainPathHandlers.entrySet().iterator();
        it.hasNext();) {
      Map.Entry<String, Handler> cur = it.next();
      if (handler == cur || cur.getValue().getClass().equals(handler.getClass())) {
        it.remove();
      }
    }
  }

  /**
   * Function will unregister matching type handler, if handler was registered
   * with {@link registerHandlerByPath(String path, Handler handler)} then use
   * {@link unregisterHandlerByPath(String path)} or {@link unregisterHandler(Handler handler)}
   * to unregister handler from any location.
   * @param handler handler instance to be unregistered
   */
  public void unregisterMatchingHandler(Handler handler) {
    matchingHandlers.remove(handler);
  }

  /**
   * Function finds handler for the path. This function is used by topNET engine
   * in order to find handlers chain for request.
   *
   * It seeks all matching handlers first in advanced stack
   * (matchingPathHandlers) and then collects matches from plain handlers list
   * (plainPathHandlers).
   *
   * This function is typically used by topNET engine.
   * 
   * @param fullPath full path woth parameters (before )"?", "?", and after "?"
   * @param path url path part without parameters (before "?")
   * @param params URL parameters part without path (after "?")
   * @return handler starting handling chain
   */
  public Handler getHandlerForPath(String fullPath, String path, String params) {
    Handler handler = null;

    for (Handler matchingHandler : matchingHandlers) {
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
   * @return the errorHandlingConfig
   */
  public ErrorHandlingConfig getErrorHandlingConfig() {
    return errorHandlingConfig;
  }

  /**
   * @param errorHandlingConfig the errorHandlingConfig to set
   */
  public void setErrorHandlingConfig(ErrorHandlingConfig errorHandlingConfig) {
    this.errorHandlingConfig = errorHandlingConfig;
  }
  
  /**
   * Important setting indicating how large will be response write out buffer.
   * In response to request server will try to fill fully but not more than this
   * maximum (maximum + chunk size exactly).
   *
   * @param maxResponseBufferFillSize the maxResponseBufferFillSize to set
   */
  public void setMaxResponseBufferFillSize(long maxResponseBufferFillSize) {
    this.maxResponseBufferFillSize = maxResponseBufferFillSize;
  }

  /**
   * Important setting indicating how large will be response write out buffer.
   * In response to request server will try to fill fully but not more than this
   * maximum (maximum + chunk size exactly).
   *
   * @return the maxBufferFillSize
   */
  public long getMaxResponseBufferFillSize() {
    return maxResponseBufferFillSize;
  }

}
