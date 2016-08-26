package com.qubit.qurricane;

import com.qubit.qurricane.errors.ErrorTypes;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Handler {
  
  static private final Map<String, Handler> plainPathHandlers;
  static private final List<Handler> matchingHandlersAfterPlainHandlers;
  
  static {
    plainPathHandlers = new HashMap<>();
    matchingHandlersAfterPlainHandlers = new ArrayList<>();
  }
  
  public static void registerHandlerByPath(String path, Handler handler) {
    plainPathHandlers.put(path, handler);
  }
  
  public static void registerHandlerForMatching(Handler handler) {
    matchingHandlersAfterPlainHandlers.add(handler);
  }
    
  public Handler() {
  }
  
  public static Handler getHandlerForPath(String fullPath, String path) {
    Handler handler = plainPathHandlers.get(path);
    
    if (handler == null) {
      for (Handler matchingHandler : matchingHandlersAfterPlainHandlers) {
        if (matchingHandler.matches(fullPath)) {
          return matchingHandler;
        }
      }
    }
    
    return handler;
  }
  
  public void init(Request request, Response response) {
    // moment to put own output stream to request
  }
  
  // request is ready, with full body, unless different stream been passed
  public void process(Request request, Response response) throws Exception {
    
  }

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

}
