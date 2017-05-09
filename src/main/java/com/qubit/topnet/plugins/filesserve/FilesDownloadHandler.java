package com.qubit.topnet.plugins.filesserve;

import com.qubit.topnet.Handler;
import com.qubit.topnet.Request;
import com.qubit.topnet.Response;
import com.qubit.topnet.exceptions.ResponseBuildingStartedException;
import com.qubit.topnet.utils.Pair;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.activation.MimetypesFileTypeMap;

/**
 * Author Piotr Fronc
 */
public class FilesDownloadHandler extends Handler {
  
  static final Logger log = 
      Logger.getLogger(FilesDownloadHandler.class.getName());
  
  private File directory;
  private String prefix;
  
  private boolean chrootMode = false;
  private boolean noBrowsing = false;
//  private boolean allowSylinks = true;

  static public final int 
      OK = HttpURLConnection.HTTP_OK,
      FORBIDDEN = HttpURLConnection.HTTP_FORBIDDEN,
      NOT_FOUND = HttpURLConnection.HTTP_NOT_FOUND;
  
  final private String NO_MIME = "";
  private final Map<String, String> MIME_CACHE =  new HashMap<>();
  private static Properties MIMES;
  
  static {
    MIMES = new Properties();
    try {
      MIMES.load(FilesDownloadHandler.class.getClassLoader()
          .getResourceAsStream("mime.properties"));
    } catch (IOException ex) {
      log.log(Level.SEVERE, null, ex);
    }
  }
  
  
  public final void setPaths(String pprefix, String path) throws IOException {
    directory = new File(path).getCanonicalFile();
    prefix = pprefix;
    if (!directory.isDirectory()) {
      throw new RuntimeException(
          "FilesBrowserHandler must be initiated with directory! \n Given: "
          + directory.getAbsolutePath());
    }
  }

  public FilesDownloadHandler(String urlPrefix, String path) throws IOException {
    setPaths(urlPrefix, path);
  }

  @Override
  public Handler getInstance() {
    return this;
  }

  @Override
  public boolean matches(String fullPath, String path, String params) {
    return path.startsWith(prefix);
  }

  private Map<String, Pair<File, Integer>> CACHE = new HashMap<>();
  
  @Override
  public boolean process(Request request, Response response)
      throws Exception {

    String path = request.getPath();
    
    path = URLDecoder.decode(path, "UTF-8");
    
    Pair<File, Integer> destCache;

    if (path.startsWith(prefix)) {
      
      destCache = CACHE.get(path);
      
      int code;
      File requestedFile;
      
      if (destCache == null) {
        boolean secure = true;
        requestedFile = new File(
            directory,
            path.substring(prefix.length())).getCanonicalFile();
        
        // security check
        if (isChrootMode()) {
//          if (allowSymlinks) {
//            
//          } else
           if (!(requestedFile.getAbsolutePath() + File.separator)
              .startsWith(directory.getAbsolutePath() + File.separator)) {
            secure = false;
          }
        }
        
        if (secure) {
          code = requestedFile.exists() ? OK : NOT_FOUND;
        } else {
          code = FORBIDDEN;
        }
        
        destCache = new Pair<>(requestedFile, code);
        CACHE.put(path, destCache);
      } else {
        requestedFile = destCache.getLeft();
        code = destCache.getRight();
      }

      // security check
      if (code == FORBIDDEN) {
        response.setErrorResponse(FORBIDDEN, "Forbidden.");
        return true; // go back to chain
      } else if (code == NOT_FOUND) {
        response.setErrorResponse(NOT_FOUND, "Not found.");
        return true; // go back to chain
      }
      
      File[] listing = requestedFile.listFiles();

      if (listing == null) {
        replyWithFile(requestedFile, response);
      } else {
        if (isNoBrowsing()) {
          response.setErrorResponse(FORBIDDEN, "Forbidden.");
          return true; // go back to chain
        } else {
          if (request.getParameters().get("plain") != null) {
            response.print(plainListFiles(listing));
          } else {
            response.print(listFiles(listing));
          }
        }
      }
    } else {
      response.setErrorResponse(NOT_FOUND, "Not found.");
    }

    return false;
  }

  private void setApplicationTypeFromFile(Response response, File dest)
      throws IOException {

    String name = dest.getName();
    String mime = MIME_CACHE.get(name);
    
    if (mime == null) {
      String extension = name.substring(name.lastIndexOf(".") + 1);
      
      mime = MIMES.getProperty(extension.toLowerCase());
      
      if (mime == null) {
        mime = MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(dest);
      }
      
      MIME_CACHE.put(name, mime == null ? NO_MIME : mime);
    }
    
    if (mime == null || mime == NO_MIME) { // == correct
      response.setContentType("application/octet-stream");
      response.addHeader("Content-Disposition",
                         "attachment; filename=" +
                         URLEncoder.encode(dest.getName(), "UTF-8") + ";");
    } else {
      response.setContentType(mime);
    }
  }

  private String plainListFiles(File[] listing) {
    StringBuffer buf = new StringBuffer();

    for (File item : listing) {
      buf.append(item.getName());
      buf.append("\n");
    }

    return buf.toString();
  }
  
  private String listFiles(File[] listing) {
    StringBuffer buf = new StringBuffer("<html>");
        buf.append("\n<head>\n");
        buf.append("\n<style>\n");
        buf.append("</style>\n");
        buf.append("</head>\n");
        buf.append("\n<body>\n<pre>\n");
        buf.append("<a href=\"..\">..</a>\n");

        for (File item : listing) {
          
          boolean isDir = item.isDirectory();
          
          buf.append("<a href=\"./");
          //href address
          buf.append(item.getName());
          if (isDir) {
            buf.append("/");
          }
          
          buf.append("\">");
          
          if (isDir) buf.append("<b>");
          buf.append(item.getName());
          if (isDir) buf.append("/");
          if (isDir) buf.append("</b>");
          
          buf.append("</a>");
          
          buf.append("\n");
        }

        buf.append("\n</pre>\n</body>\n</html>\n");
        return buf.toString();
  }

  private void replyWithFile(File requestedFile, Response response) 
      throws ResponseBuildingStartedException, IOException {
    if (requestedFile.canRead()) {
      response.setContentLength(requestedFile.length());

      setApplicationTypeFromFile(response, requestedFile);
      
      RandomAccessFile aFile = 
          new RandomAccessFile(requestedFile.getAbsolutePath(), "r");
      
      response.setChannelToReadFrom(aFile.getChannel());
    
      // stream is relatively slower
      //   response.setStreamToReadFrom(new BufferedInputStream(
      // new FileInputStream(requestedFile)));
    } else {
      response.setErrorResponse(FORBIDDEN, "Forbidden.");
    }
  }
  
  /**
   * @return the chrootMode
   */
  public boolean isChrootMode() {
    return chrootMode;
  }

  /**
   * @param chrootMode the chrootMode to set
   */
  public void setChrootMode(boolean chrootMode) {
    this.chrootMode = chrootMode;
  }

  /**
   * @return the noBrowsing
   */
  public boolean isNoBrowsing() {
    return noBrowsing;
  }

  /**
   * @param noBrowsing the noBrowsing to set
   */
  public void setNoBrowsing(boolean noBrowsing) {
    this.noBrowsing = noBrowsing;
  }


}
