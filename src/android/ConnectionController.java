package co.nicom.cordova;

import android.util.Base64;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


class ConnectionController {
    private static int idCounter = 1;
    private static List<String> readOnlyMethods = Arrays.asList("GET", "DELETE");
    final int conId;
    HttpURLConnection con;
    OutputStream out;
    InputStream in;
    int maxChunkSize;
    boolean shouldRead;
    boolean headersSent;
    CallbackContext readHeadCallback;
    boolean waitForRequest;

    ConnectionController(HttpURLConnection con) {
        this.conId = idCounter++;
        this.con = con;
        this.headersSent = false;
    }

    void destroyIn() throws IOException {
        if(in != null) in.close();
    }

    void destroyOut() throws JSONException, IOException {
        if(out != null){
            out.close();
            if(in == null) {
                initInStream();
                if(readHeadCallback != null) {
                    sendHeaderPacket(readHeadCallback);
                } else {
                    waitForRequest = false;
                }
            }
        } 
    }

    void abort() throws JSONException, IOException {
        destroyOut();
        destroyIn();
    }

    void initInStream() throws IOException {
        try {
            this.in = con.getInputStream();
        } catch(FileNotFoundException e) {
            this.in = con.getErrorStream();
        }
    }
    void initStreams() throws IOException {
        if(!readOnlyMethods.contains(con.getRequestMethod())) {
            this.out = con.getOutputStream();
            this.waitForRequest = true;
        } else {
            this.waitForRequest = false;
            initInStream();
        }
    }

    boolean isClosed() {
        try {
          if(in != null) {
            in.available();
            return false;
          } else {
            return true;
          }
        }
        catch (IOException e) {
           return "closed".equals(e.getMessage());
        }
    }

    void flushHeaders(JSONObject headers) throws JSONException, IOException {
        if(!headersSent) {
            if(headers != null) {
                for (int i = 0; i < headers.length(); i++) {
                    String key = headers.names().getString(i);
                    String value = headers.getString(key);
                    con.setRequestProperty(key, value);
                }
            }
            this.initStreams();
            headersSent = true;
        }
    }

    private void sendHeaderPacket(CallbackContext callback) throws JSONException, IOException {
        JSONObject packet = new JSONObject();
        packet.put("conId", conId);
        packet.put("statusCode", con.getResponseCode());
        packet.put("httpMessage", con.getResponseMessage());
        JSONObject headers = new JSONObject();
        Map<String, List<String>> map = con.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            if(entry.getKey() != null) {
                headers.put(entry.getKey(), entry.getValue());
            }
        }
        packet.put("headers", headers);
        packet.put("type", "head");
        PluginResult result = new PluginResult(PluginResult.Status.OK, packet);
        callback.sendPluginResult(result);
    }

    private void sendChunkPacket(byte[] buffer, int len, CallbackContext callback) throws JSONException {
        JSONObject packet = new JSONObject();
        packet.put("conId", conId);
        packet.put("type", "chunk");
        if(len > 0) {
          packet.put("data", Base64.encodeToString(buffer, 0, len, Base64.DEFAULT));
        }

        PluginResult result = new PluginResult(PluginResult.Status.OK, packet);
        callback.sendPluginResult(result);
    }

    private void sendEndPacket(byte[] buffer, int len, CallbackContext callback) throws JSONException {
        JSONObject packet = new JSONObject();
        packet.put("conId", conId);
        packet.put("type", "end");
        if(len > 0) {
          packet.put("data", Base64.encodeToString(buffer, 0, len, Base64.DEFAULT));
        }
        PluginResult result = new PluginResult(PluginResult.Status.OK, packet);
        callback.sendPluginResult(result);
    }

    public void readHead(CallbackContext callback) throws JSONException, IOException {
        if(!waitForRequest)
            sendHeaderPacket(callback);
        else
            readHeadCallback = callback;
    }

    public boolean read(int maxChunkSize, CallbackContext callback) throws IOException, JSONException {
        int len = 0;
        int count = 0;
        boolean done = false;
        byte[] buffer = new byte[maxChunkSize];
        while (len <= maxChunkSize * 0.8 && !done && count < 6 ) {
          int res = in.read(buffer, len, maxChunkSize - len);
          if(res == -1) done = true;
          else len += res;
          count++;
        }

        if(done) {
            sendEndPacket(buffer, len, callback);
            return true;
        }
        else {
            sendChunkPacket(buffer, len, callback);
            return false;
        }
    }
}
