/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import com.qubit.opentag.log.Log;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Server {

  public static void main(String[] args) throws IOException {

    new Server("localhost", 1234).start();
  }

  public static final int BUF_SIZE = 8 * 128; // miliseconds
  public static final long TOUT = 30 * 1000; // miliseconds
  public static final long MAX_SIZE = 10 * 1024 * 1024; // 10 MB

  public static Log log = new Log(Server.class);

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

    this.serverChannel = ServerSocketChannel.open();
    serverChannel.configureBlocking(false);

    serverChannel.socket().bind(listenAddress);

    // @todo move to cfg
    Selector selector = Selector.open();
    serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    
    for (int i = 0; i < 10; i++) {
      this.addHandler(selector);
    }

    log.info("Server starting at " + listenAddress.getHostName() + " at " + port);
  }

  public void stop() throws IOException {
    MainAcceptAndDispatchThread.keepRunning = false;
    
    // wait for all to finish
    while (!MainAcceptAndDispatchThread.handlingThreads.isEmpty());
    
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


  protected static void accept(SelectionKey key, Selector selector)
          throws IOException {
    // pick socketChannel channel
    ServerSocketChannel serverSocketChannel = 
            (ServerSocketChannel) key.channel();
    // trigger accept
    SocketChannel channel = serverSocketChannel.accept();

    if (channel != null) {
      channel.configureBlocking(false);
      // now register selector for new event type (notice 
      // in loop accept and reading events)
      channel.register(selector, OP_READ);
    }
  }

  protected static void close(SelectionKey key) {
    try {
      // this method is used on "bad occurence - to cleanup any stuff left
      // cleaning will be reviewed again
      key.channel().close();
      key.cancel();
    } catch (IOException ex) {
      // metrics???
    }
  }

  // heart of processing 
  private Thread addHandler(final Selector selector) {
    
    if (!this.readPreparatorSet) {
      this.readPreparatorSet = true;
      MainAcceptAndDispatchThread t = new MainAcceptAndDispatchThread(selector);
      t.start();
    }
      
    // @todo create separate class and encapsulate this
    HandlingThread t = new HandlingThread(selector);

    t.start();

    return t;
  }
  
}
