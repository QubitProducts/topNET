/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.topnet;

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
    
    dataHandler.read();
  
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
