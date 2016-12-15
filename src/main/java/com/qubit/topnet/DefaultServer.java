/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.topnet;

import com.qubit.topnet.eventonly.EventTypeServer;

/**
 *
 * @author piotr
 */
public class DefaultServer extends EventTypeServer {
  
  public DefaultServer(String address, int port) {
    super(address, port);
  }
  
}
