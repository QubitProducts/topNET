package com.qubit.qurricane;

import com.qubit.qurricane.errors.ErrorTypes;
import com.qubit.qurricane.utils.Pair;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public abstract class Handler {

  
  public static final String  HTTP_0_9 = "HTTP/0.9";
  public static final String  HTTP_1_0 = "HTTP/1.0";
  public static final String  HTTP_1_1 = "HTTP/1.1";
  public static final String  HTTP_1_x = "HTTP/1.x";
  
  static final Logger log = Logger.getLogger(Handler.class.getName());
  private Handler next;

  public Handler getInstance() {
    try {
      return this.getClass().newInstance();
    } catch (InstantiationException | IllegalAccessException ex) {
      log.log(Level.SEVERE, null, ex);
    }
    return null;
  }

  protected Handler() {}

  protected void onBeforeOutputStreamIsSet(Request request, Response response) {
    if (this.prepare(request, response)) {
      Handler tmp = this.getNext();
      while(tmp != null) {
        if (!tmp.prepare(request, response)) {
          break;
        } else {
          tmp = tmp.getNext();
        }
      }
    }
  }

  public boolean prepare(Request request, Response response) {
    return true;
  }
  
  protected Pair<Handler, Throwable> 
        doProcess(Request request, Response response) {
    Handler tmp = this;
        
    while(tmp != null) {
      try {
        if (!tmp.process(request, response)) {
          break;
        } else {
          tmp = tmp.getNext();
        }
      } catch (Throwable t) {
        return new Pair<>(tmp, t);
      }
    }
    
    return null;
  }
  
  // request is ready, with full body, unless different stream been passed
  public abstract boolean
         process(Request request, Response response) throws Exception;

  public boolean supports(String method) {
    return true;
  }

  public boolean matches(String fullPath, String path, String params) {
    return true;
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

  /**
   * @return the next
   */
  public Handler getNext() {
    return next;
  }

  /**
   * @param next the next to set
   */
  public void setNext(Handler next) {
    this.next = next;
  }
  
  public void closeConnection (Request request) {
    Server.close(request.getKey());
  }
}
