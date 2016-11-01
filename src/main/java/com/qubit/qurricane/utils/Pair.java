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

package com.qubit.qurricane.utils;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */

public class Pair<T, V> {
  
  private T left;
  private V right;
  
  public Pair(T t, V v) {
    left = t;
    right = v;
  }

  /**
   * @return the left
   */
  public T getLeft() {
    return left;
  }

  /**
   * @param left the left to set
   */
  public void setLeft(T left) {
    this.left = left;
  }

  /**
   * @return the right
   */
  public V getRight() {
    return right;
  }

  /**
   * @param right the right to set
   */
  public void setRight(V right) {
    this.right = right;
  }
  
  
}
