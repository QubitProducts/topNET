/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.nio.channels.SelectionKey;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public abstract class HandlingThread extends Thread {

  /**
   *
   * @param key
   * @return
   */
  abstract boolean addJob(DataHandler dataHandler, SelectionKey key);

  abstract boolean canAddJob();

}
