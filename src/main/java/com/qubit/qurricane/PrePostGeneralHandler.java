/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public interface PrePostGeneralHandler {

  public void handleStarted(DataHandler dh);

  public void handleHeadersReady(DataHandler dh);

  public void handleBodyReady(DataHandler dh);

  public void handleBeforeHandlingProcessing(DataHandler dh);
  
  public void handleAfterHandlingProcessed(DataHandler dh);
  
  public void onFinishedAndClosedHandler(DataHandler dh);
}
