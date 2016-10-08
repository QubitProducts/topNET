package com.qubit.qurricane;

import com.qubit.qurricane.errors.ErrorTypes;
import java.io.InputStream;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public abstract class Handler {

  

  public Handler getInstance() {
    return this;
//    try {
//      return this.getClass().newInstance();
//    } catch (InstantiationException | IllegalAccessException ex) {
//      Logger.getLogger(Handler.class.getName()).log(Level.SEVERE, null, ex);
//    }
//    return null;
  }


  public Handler() {
  }

  //locallly used only
  void runPrepare(Request request, Response response) {
    this.prepare(request, response);
  }

  public void prepare(Request request, Response response) {
  
  }
  
  // request is ready, with full body, unless different stream been passed
  public abstract void process(Request request, Response response) throws Exception;

  public boolean supports(String method) {
    return true;
  }

  public boolean matches(String fullPathIncludingQuery) {
    return false;
  }

  public InputStream getErrorInputStream(ErrorTypes errorOccured) {
    return null;
  }

  protected Handler getErrorHandler() {
    return null;
  }

  /**
   * Returns -2 by default - which means that this handler lets server default
   * value to be used. To set no size limit - set -1. Any 0+ value will cause
   * incoming data size limit to be applied.
   *
   * @return the maxIncomingDataSize
   */
  public int getMaxIncomingDataSize() {
    return -2;
  }

  /**
   * Max idle defines maximum miliseconds amount for peer to not to return any
   * reads.
   *
   * Return -1 to let the server to decide on max idle times.
   *
   * Default value is -1
   *
   * @return the maxIdle
   */
  public int getMaxIdle() {
    return -1;
  }

  
  public void onError(Throwable t) {}
}
