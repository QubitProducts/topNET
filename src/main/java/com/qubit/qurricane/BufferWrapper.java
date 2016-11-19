/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.nio.ByteBuffer;

/**
 *
 * @author piotr
 */
public class BufferWrapper {
  
  private ByteBuffer byteBuffer = null;
  private BufferWrapper next = null;
  private BufferWrapper prev = null;
  
  BufferWrapper(ByteBuffer byteBuffer) {
    this.byteBuffer = byteBuffer;
  }

  BufferWrapper() {
  }

  /**
   * @return the byteBuffer
   */
  public ByteBuffer getByteBuffer() {
    return byteBuffer;
  }

  /**
   * @param byteBuffer the byteBuffer to set
   */
  public void setByteBuffer(ByteBuffer byteBuffer) {
    this.byteBuffer = byteBuffer;
  }

  /**
   * @return the next
   */
  public BufferWrapper getNext() {
    return next;
  }

  /**
   * @param next the next to set
   */
  public void setNext(BufferWrapper next) {
    this.next = next;
  }

  /**
   * @return the prev
   */
  public BufferWrapper getPrev() {
    return prev;
  }

  /**
   * @param prev the prev to set
   */
  public void setPrev(BufferWrapper prev) {
    this.prev = prev;
  }
}
