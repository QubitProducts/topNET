/**
 * Author Piotr Fronc
 */
package com.qubit.topnet.plugins.filesserve;

import com.qubit.topnet.BytesStream;
import static com.qubit.topnet.BytesStream.NO_BYTES_LEFT_VAL;
import com.qubit.topnet.Handler;
import com.qubit.topnet.Request;
import com.qubit.topnet.Response;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author piotr
 */
public class FileUploadHandler extends Handler {
  RandomAccessFile fc;

  @Override
  public boolean init(Request request, Response response) {
    try {
      fc = new RandomAccessFile(new File("uploaded_file.out"), "rw");
    } catch (FileNotFoundException ex) {
      Logger.getLogger(FileUploadHandler.class.getName())
          .log(Level.SEVERE, null, ex);
    }
    request.setBytesReadEvent((Request req, Response res, boolean fin) -> {
        BytesStream bs = request.getBytesStream();
        int ch;
        while ((ch = bs.readByte()) != NO_BYTES_LEFT_VAL) {
          try {
            fc.write(ch);
          } catch (IOException ex) {
            Logger.getLogger(FileUploadHandler.class.getName())
                .log(Level.SEVERE, null, ex);
          }
        }
      });
    return true;
  }
  
  

  @Override
  public boolean process(Request request, Response response) throws Exception {
    fc.close();
    
    response.print("FU");
    
    return true;
  }

}
