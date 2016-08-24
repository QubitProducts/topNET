package com.qubit.qurricane;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class Handler {

  public Handler() {
    
  }
  
  public void init(Request request, Response response) {
    // moment to put own output stream to request
  }
  
  // request is ready, with full body, unless different stream been passed
  public void process(Request request, Response response) {
    
  }

  public boolean supports(String method) {
    return true;
  }
}
