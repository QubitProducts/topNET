/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
