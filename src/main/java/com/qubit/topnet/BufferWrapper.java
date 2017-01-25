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

import java.nio.ByteBuffer;

/**
 *
 * @author peter.fronc@qubit.com
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
