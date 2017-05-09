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

import static com.qubit.topnet.ServerBase.HTTP_0_9;
import static com.qubit.topnet.errors.ErrorTypes.HTTP_MALFORMED_HEADERS;
import static com.qubit.topnet.errors.ErrorTypes.HTTP_NOT_FOUND;
import com.qubit.topnet.exceptions.OutputStreamAlreadySetException;
import java.io.IOException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class DataHandlerTest {
  
  public DataHandlerTest() {
  }
  
  @BeforeClass
  public static void setUpClass() {
  }
  
  @AfterClass
  public static void tearDownClass() {
  }
  
  @Before
  public void setUp() {
  }
  
  @After
  public void tearDown() {
  }
  
  @Test
  public void testConsumingHeaders() 
      throws IOException,
      OutputStreamAlreadySetException {
    String bodyMsg = "{bodyMessage: \"Hello World!\"}";
    String httpMsg = "POST /echo?param1=1&&param2=2& HTTP/1.1\r\n"
        + "Header-nospace:abcde ef g, hij klmn1p  @#$%#$&*b \r\n"
        + "Header-multiline: abcde ef g, hij \r\n klmn1p  @#$%\r\n #$&*b \r\n"
        + "Header-xmultiline: \tabcde ef g, hij \r\n\t klmn1p  @#$%\r\n\t\t#$&*b \r\n"
        + "Header-space: abcde ef g, hij klmn1p \r\n"
        + "ConTent-lEngtH:" + "   \t " + bodyMsg.length() + " \r\n"
        + "\r\n"
        + bodyMsg;
    
    AcceptOnlyEventsTypeServer server = 
        new AcceptOnlyEventsTypeServer("localhost", 3456);
        
    DummySocketChannel dummy = new DummySocketChannel(null);
    
    dummy.init(httpMsg);
    
    //do not start server, only for config
    DataHandler dataHandler = 
        new DataHandler(server, dummy);
    
    while(dataHandler.read() >= 0);
  
    assertEquals(dataHandler.getRequest()
        .getBodyString(), bodyMsg);
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("content-length"), "" + bodyMsg.length() + " ");
    assertEquals(dataHandler.getContentLength(), bodyMsg.length());
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("header-multiline"),
        "abcde ef g, hij \nklmn1p  @#$%\n#$&*b ");
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("header-xmultiline"),
        "abcde ef g, hij \n klmn1p  @#$%\n\t#$&*b ");
  }
  
  @Test
  public void testFailingConsumingHeaders1() 
      throws IOException,
      OutputStreamAlreadySetException {
    String bodyMsg = "{bodyMessage: \"Hello World!\"}";
    String httpMsg = "POST /echo?param1=1&&param2=2& HTTP/1.1\r\n"
        + "not-failing :abcde ef g, hij klmn1p  @#$%#$&*b \r\n"
        + "Header-multiline: abcde ef g, hij \r\n klmn1p  @#$%\r\n #$&*b \r\n"
        + "Header-xmultiline: \tabcde ef g, hij \r\n\t klmn1p  @#$%\r\n\t\t#$&*b \r\n"
        + "Header-space: abcde ef g, hij klmn1p \r\n"
        + "ConTent-lEngtH:" + "   \t " + bodyMsg.length() + " \r\n"
        + "\r\n"
        + bodyMsg;
    
    AcceptOnlyEventsTypeServer server = 
        new AcceptOnlyEventsTypeServer("localhost", 3456);
        
    DummySocketChannel dummy = new DummySocketChannel(null);
    
    dummy.init(httpMsg);
    
    //do not start server, only for config
    DataHandler dataHandler = 
        new DataHandler(server, dummy);
    
    while(dataHandler.read() >= 0);
    
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("not-failing "), "abcde ef g, hij klmn1p  @#$%#$&*b "); 
    
  }
  
  @Test
  public void testFailingConsumingHeaders2() 
      throws IOException,
      OutputStreamAlreadySetException {
    String bodyMsg = "{bodyMessage: \"Hello World!\"}";
    String httpMsg = "POST /echo?param1=1&&param2=2& HTTP/1.1\r\n"
        + " failing:abcde ef g, hij klmn1p  @#$%#$&*b \r\n"
        + "Header-multiline: abcde ef g, hij \r\n klmn1p  @#$%\r\n #$&*b \r\n"
        + "Header-xmultiline: \tabcde ef g, hij \r\n\t klmn1p  @#$%\r\n\t\t#$&*b \r\n"
        + "Header-space: abcde ef g, hij klmn1p \r\n"
        + "ConTent-lEngtH:" + "   \t " + bodyMsg.length() + " \r\n"
        + "\r\n"
        + bodyMsg;
    
    AcceptOnlyEventsTypeServer server = 
        new AcceptOnlyEventsTypeServer("localhost", 3456);
        
    DummySocketChannel dummy = new DummySocketChannel(null);
    
    dummy.init(httpMsg);
    
    //do not start server, only for config
    DataHandler dataHandler = 
        new DataHandler(server, dummy);
    
    while(dataHandler.read() >= 0);
    
    assertEquals(dataHandler.getRequest().getLowerCaseHeader(" failing"), null);
    assertEquals(dataHandler.getRequest().getLowerCaseHeader("failing"), null);
    assertEquals(dataHandler.getErrorOccured(), HTTP_MALFORMED_HEADERS);
    
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("content-length"), null);
    assertEquals(dataHandler.getContentLength(), 0);
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("header-multiline"), null);
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("header-xmultiline"), null);
    // it reads after fast fail rest of buffer as the body as 
    // in application design
    assertEquals(dataHandler.getRequest()
        .getBodyString(), "Header-multiline: abcde ef g, hij \r\n klmn1p  @#$%\r\n #$&*b \r\n"
        + "Header-xmultiline: \tabcde ef g, hij \r\n\t klmn1p  @#$%\r\n\t\t#$&*b \r\n"
        + "Header-space: abcde ef g, hij klmn1p \r\n"
        + "ConTent-lEngtH:" + "   \t " + bodyMsg.length() + " \r\n"
        + "\r\n"
        + bodyMsg);
  }
  
  @Test
  public void testFailingConsumingHeaders3() 
      throws IOException,
      OutputStreamAlreadySetException {
    String bodyMsg = "{bodyMessage: \"Hello World!\"}";
    String httpMsg = " POST /echo?param1=1&&param2=2& HTTP/1.1\r\n"
        + "failing:abcde ef g, hij klmn1p  @#$%#$&*b \r\n"
        + "Header-multiline: abcde ef g, hij \r\n klmn1p  @#$%\r\n #$&*b \r\n"
        + "Header-xmultiline: \tabcde ef g, hij \r\n\t klmn1p  @#$%\r\n\t\t#$&*b \r\n"
        + "Header-space: abcde ef g, hij klmn1p \r\n"
        + "ConTent-lEngtH:" + "   \t " + bodyMsg.length() + " \r\n"
        + "\r\n"
        + bodyMsg;
    
    AcceptOnlyEventsTypeServer server = 
        new AcceptOnlyEventsTypeServer("localhost", 3456);
        
    DummySocketChannel dummy = new DummySocketChannel(null);
    
    dummy.init(httpMsg);
    
    //do not start server, only for config
    DataHandler dataHandler = 
        new DataHandler(server, dummy);
    
    while(dataHandler.read() >= 0);
    
    assertEquals(dataHandler.getRequest().getLowerCaseHeader("failing"), null);
    assertEquals(dataHandler.getErrorOccured(), HTTP_MALFORMED_HEADERS);
    
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("content-length"), null);
    assertEquals(dataHandler.getContentLength(), 0);
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("header-multiline"), null);
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("header-xmultiline"), null);
  }
  
  
  @Test
  public void testFailingConsumingHeaders4() 
      throws IOException,
      OutputStreamAlreadySetException {
    String bodyMsg = "{bodyMessage: \"Hello World!\"}";
    String httpMsg = "POST /echo?param1=1&&param2=2&\r\n"
        + "Header-multiline: abcde ef g, hij \r\n klmn1p  @#$%\r\n #$&*b \r\n"
        + "Header-xmultiline: \tabcde ef g, hij \r\n\t klmn1p  @#$%\r\n\t\t#$&*b \r\n"
        + "Header-space: abcde ef g, hij klmn1p \r\n"
        + "ConTent-lEngtH:" + "   \t " + bodyMsg.length() + " \r\n"
//        + "\r\n"
        + bodyMsg;
    
    AcceptOnlyEventsTypeServer server = 
        new AcceptOnlyEventsTypeServer("localhost", 3456);
        
    DummySocketChannel dummy = new DummySocketChannel(null);
    
    dummy.init(httpMsg);
    
    //do not start server, only for config
    DataHandler dataHandler = 
        new DataHandler(server, dummy);
    
    while(dataHandler.read() >= 0);
    
    assertEquals(dataHandler.getErrorOccured(), HTTP_NOT_FOUND);
    assertEquals(dataHandler.getRequest().getRequestedHttpProtocol(), HTTP_0_9);
    // https 0.9 will consume rest as body and it wont be MALFORMED HEADER CASE.
    assertEquals(dataHandler.getRequest()
        .getBodyString(), "Header-multiline: abcde ef g, hij \r\n klmn1p  @#$%\r\n #$&*b \r\n"
        + "Header-xmultiline: \tabcde ef g, hij \r\n\t klmn1p  @#$%\r\n\t\t#$&*b \r\n"
        + "Header-space: abcde ef g, hij klmn1p \r\n"
        + "ConTent-lEngtH:" + "   \t " + bodyMsg.length() + " \r\n"
//        + "\r\n"
        + bodyMsg);
    
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("content-length"), null);
    assertEquals(dataHandler.getContentLength(), 0);
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("header-multiline"), null);
    assertEquals(dataHandler.getRequest()
        .getLowerCaseHeader("header-xmultiline"), null);
  }
  
  
  @Test
  @SuppressWarnings("empty-statement")
  public void testFailingConsumingHeaders5() 
      throws IOException,
      OutputStreamAlreadySetException {
    String bodyMsg = "{bodyMessage: \"Hello World!\"}";
    String httpMsg = "POST /echo?param1=1&&param2=2&\r\n"
        + "Header-multiline: abcde ef g, hij \r\n klmn1p  @#$%\r\n #$&*b \r\n"
        + "Header-xmultiline: \tabcde ef g, hij \r\n\t klmn1p  @#$%\r\n\t\t#$&*b \r\n"
        + "Header-space: abcde ef g, hij klmn1p \r\n"
        + "ConTent-lEngtH:" + "   \t " + bodyMsg.length() + " \r\n"
//        + "\r\n"
        + bodyMsg;
    
    AcceptOnlyEventsTypeServer server = 
        new AcceptOnlyEventsTypeServer("localhost", 3456);
    
    server.registerHandlerByPath("/echo", new Handler() {

      @Override
      public boolean process(Request request, Response response) throws Exception {
        response.print("Whatever text."); 
        // error message must override existing string buffer
        response.setErrorResponse(503, "Server Error!");
        return false;
      }
      
      @Override
      public Handler getInstance() {
        return this;
      }
    });
    
    DummySocketChannel dummy = new DummySocketChannel(null);
    
    dummy.init(httpMsg);
    
    //do not start server, only for config
    DataHandler dataHandler = 
        new DataHandler(server, dummy);
    
    while(dataHandler.read() >= 0);
    
    assertEquals(
        dataHandler.getResponse().getStringBuffer().toString(), "Server Error!");
    
    while(dataHandler.write() >= 0);
    
//    // write will trigger buffer preparation 
//    assertEquals(dataHandler.getResponse().getStringBuffer(), "");
    assertEquals(
        dataHandler.getResponse().getStringBuffer().toString(), "Server Error!");
    
    assertEquals(null, dataHandler.getErrorOccured());
    assertEquals(503, dataHandler.getResponse().getHttpCode());
    assertEquals(dataHandler.getRequest().getRequestedHttpProtocol(), HTTP_0_9);
    // https 0.9 will consume rest as body and it wont be MALFORMED HEADER CASE.
    assertEquals("Server Error!", dataHandler.getRequest().getBodyString());
    
    assertEquals("Server Error!", dummy.getWrittenBackMessage());
  }
  
  
  @Test
  public void testLongCacheParser() {
    int len = 1000000;
    int repeat = 10;
    
    String[] testSubject = new String[len];
    
    for (int i = 0; i < len; i++) {
      testSubject[i] = "" + i;
    }
    
    //warmup
    for (int j = 0; j < repeat; j++) {
      for (int i = 0; i < len; i++) {
        Long.parseLong(testSubject[i].trim());
//        Long.parseLong(testSubject[i].substring(1));
      }
    }
    
    
    long start = System.currentTimeMillis();
    
    
    for (int j = 0; j < repeat; j++) {
    for (int i = 0; i < len; i++) {
      try {
        Long.parseLong(testSubject[i], 10);
      } catch (NullPointerException | NumberFormatException ex) {
      }
    }
    }
    
    System.out.println(System.currentTimeMillis() - start);
    start = System.currentTimeMillis();
    
    for (int j = 0; j < repeat; j++) {
    for (int i = 0; i < len; i++) {
      Long.parseLong(testSubject[i].trim());
    }
    }
    
    System.out.println(System.currentTimeMillis() - start);
  }
  
}
