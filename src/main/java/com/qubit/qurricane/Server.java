/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import static com.qubit.qurricane.Handler.registerHandlerByPath;
import com.qubit.qurricane.examples.EchoHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_READ;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Server {

  public static void main(String[] args) throws IOException {

    new Server("localhost", 3456).start();
    
    registerHandlerByPath("/hello", new EchoHandler());

  }
  
  private static final int THREAD_JOBS_SIZE = 256;
  private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;
  public static final long MAX_IDLE_TOUT = 5000 * 1000; // miliseconds
  public static final long MAX_MESSAGE_SIZE = 10 * 1024 * 1024; // 10 MB

//  public static Log log = new Log(Server.class);

  private final int port;

  private InetSocketAddress listenAddress;
  private final String address;
  private boolean readPreparatorSet;
  private ServerSocketChannel serverChannel;

  Server(String address, int port) {
    this.port = port;
    this.address = address;

    this.listenAddress = new InetSocketAddress(address, port);
  }

  public void start() throws IOException {

    ResponseStream.RESPONSE_BUF_SIZE = DEFAULT_BUFFER_SIZE;
    
    this.serverChannel = ServerSocketChannel.open();
    serverChannel.configureBlocking(false);

    serverChannel.socket().bind(listenAddress);

    // @todo move to cfg
    
    MainAcceptAndDispatchThread.keepRunning = true;
    
    int threadsAmount = 64;
    MainAcceptAndDispatchThread.setupThreadsList(
            threadsAmount,
            THREAD_JOBS_SIZE,
            DEFAULT_BUFFER_SIZE);
    
    if (!this.readPreparatorSet) {
      this.readPreparatorSet = true;
      
      Selector s1 = Selector.open();
      serverChannel.register(s1, SelectionKey.OP_ACCEPT);
      MainAcceptAndDispatchThread t1 = new MainAcceptAndDispatchThread(s1);
      t1.start();

//      Selector s2 = Selector.open();
//      serverChannel.register(s2, SelectionKey.OP_ACCEPT);
//      MainAcceptAndDispatchThread t2 = new MainAcceptAndDispatchThread(s2);
//      t2.start();
    }
    
    System.out.println("Server starting at " + listenAddress.getHostName() + " at " + port);
  }

  public void stop() throws IOException {
    MainAcceptAndDispatchThread.keepRunning = false;
    
    // wait for all to finish
    while (MainAcceptAndDispatchThread.hasThreads()) {
      try {
        Thread.sleep(5);
      } catch (InterruptedException ex) {}
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


  protected static boolean accept(SelectionKey key, Selector readSelector)
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
      channel.register(readSelector, OP_READ);
      return true;
    }
    return false;
  }

  protected static void close(SelectionKey key) {
    try {
      // this method is used on "bad occurence - to cleanup any stuff left
      // cleaning will be reviewed again
      key.cancel();
      key.channel().close();
    } catch (IOException ex) {
      // metrics???
    }
  }
}
