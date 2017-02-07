/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.topnet;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class DummySocketChannel extends SocketChannel {
  private byte[] input;
  private ByteBuffer output = ByteBuffer.allocate(64*1024);

  protected DummySocketChannel(SelectorProvider provider) {
    super(provider);
  }
  
  public void init(String message) {
    this.input = message.getBytes();
  }

  int currentAt = 0;
  public int read() {
    if (currentAt < input.length) {
      return input[currentAt++];
    } else {
      return -1;
    }
  }

  @Override
  public SocketChannel bind(SocketAddress local) throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public SocketChannel shutdownInput() throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public SocketChannel shutdownOutput() throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public Socket socket() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public boolean isConnected() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public boolean isConnectionPending() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public boolean connect(SocketAddress remote) throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public boolean finishConnect() throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public SocketAddress getRemoteAddress() throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    if (currentAt == input.length) {
      return -1;
    }
    
    int count = 0;
    for (; currentAt < input.length && dst.hasRemaining(); currentAt++) {
      count++;
      dst.put(input[currentAt]);
    }
    return count;
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    int pos = src.position();
    output.put(src);
    return src.position() - pos;
  }

  public String getWrittenBackMessage() {
    return new String(output.array(),0, output.position());
  }
  
  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public SocketAddress getLocalAddress() throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  protected void implCloseSelectableChannel() throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  protected void implConfigureBlocking(boolean block) throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public <T> T getOption(SocketOption<T> name) throws IOException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public Set<SocketOption<?>> supportedOptions() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }
  
  
  
}
