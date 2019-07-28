package co.nicom.cordova;

import android.util.Base64;
import android.util.SparseArray;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

public class CordovaNodeHttp extends CordovaPlugin {
    private Map<Integer, ConnectionController> connections;
    private Map<String, SSLSocketFactory> secureSocketFactories;
    public CordovaNodeHttp() {
        super();
        this.connections = new ConcurrentHashMap<>();
        this.secureSocketFactories = new ConcurrentHashMap<>();
    }

    private void makeConnection(JSONArray data, CallbackContext callbackContext) throws JSONException, IOException, KeyManagementException, NoSuchAlgorithmException, CertificateException, KeyStoreException, UnrecoverableKeyException, InvalidKeySpecException {
        URL url = new URL(data.getString(0));
        JSONObject opt = data.getJSONObject(1);
        String method = opt.optString("method", "GET");

        HttpURLConnection con;
        if (url.getProtocol().toLowerCase().equals("https")) {
            boolean rejectUnauthorized = opt.optBoolean("rejectUnauthorized", true);
            HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
            if(!rejectUnauthorized) {
                https.setHostnameVerifier(SslUtil.DO_NOT_VERIFY);
                https.setSSLSocketFactory(SslUtil.trustAllHostsFactory());
            } else {
                String ca = opt.optString("ca", "");
                String cert = opt.optString("cert", "");
                String key = opt.optString("key", "");
                String passphrase = opt.optString("passphrase", "");
                String hash = md5(ca+":"+cert+":"+key+":"+passphrase);
                SSLSocketFactory factory;
                if(secureSocketFactories.containsKey(hash)) {
                  factory = secureSocketFactories.get(hash);
                } else {
                  factory = SslUtil.createSecureContext(ca.getBytes(), cert.getBytes(), key.getBytes(), passphrase);
                  secureSocketFactories.put(hash, factory);
                }
                https.setSSLSocketFactory(factory);
            }
            con = https;
        } else {
            con = (HttpURLConnection) url.openConnection();
        }

        con.setRequestMethod(method);
        ConnectionController controller = new ConnectionController(con);
        this.connections.put(controller.conId, controller);

        String host = url.getHost();
        InetAddress remoteInetAddress = InetAddress.getByName(host);
        InetAddress localInetAddress = InetAddress.getLocalHost();
        String remoteAddress = remoteInetAddress.getHostAddress();
        int remotePort = url.getPort();
        String localAddress = localInetAddress.getHostAddress();

        JSONObject packet = new JSONObject();
        JSONObject vSocket = new JSONObject();
        vSocket.put("remoteAddress", remoteAddress);
        vSocket.put("remotePort", remotePort);
        vSocket.put("localAddress", localAddress);

        packet.put("conId", controller.conId);
        packet.put("socket", vSocket);
        packet.put("type", "connection");

        callbackContext.success(packet);
    }

    private static String md5(final String s) {
      final String MD5 = "MD5";
      try {
          // Create MD5 Hash
          MessageDigest digest = java.security.MessageDigest
                  .getInstance(MD5);
          digest.update(s.getBytes());
          byte[] messageDigest = digest.digest();

          // Create Hex String
          StringBuilder hexString = new StringBuilder();
          for (byte aMessageDigest : messageDigest) {
              String h = Integer.toHexString(0xFF & aMessageDigest);
              while (h.length() < 2)
                  h = "0" + h;
              hexString.append(h);
          }
          return hexString.toString();

      } catch (NoSuchAlgorithmException e) {
          e.printStackTrace();
      }
      return "";
  }


    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        try {
            switch (action) {
                case "ping":
                    callbackContext.success("ok");
                    return true;
                case "connect":{
                  cordova.getThreadPool().execute(() -> {
                      try {
                          makeConnection(data, callbackContext);
                      } catch (Exception e) {
                          callbackContext.error(convertStackTraceToString(e));
                      }
                  });
                  return true;
                }
                case "flushConHeaders": {
                  int conId = data.getInt(0);
                  JSONObject headers = data.getJSONObject(1);
                  ConnectionController con = Objects.requireNonNull(this.connections.get(conId));
                  cordova.getThreadPool().execute(() -> {
                    try {
                        con.flushHeaders(headers);
                        callbackContext.success();
                    } catch (Exception e) {
                        callbackContext.error(convertStackTraceToString(e));
                    }
                  });
                  return true;
                }
                case "writeToCon": {
                    int conId = data.getInt(0);
                    byte[] chunk = Base64.decode(data.getString(1), Base64.DEFAULT);
                    ConnectionController con = Objects.requireNonNull(this.connections.get(conId));
                    cordova.getThreadPool().execute(() -> {
                        try {
                            con.out.write(chunk);
                            callbackContext.success();
                        } catch (Exception e) {
                            callbackContext.error(convertStackTraceToString(e));
                        }
                    });
                    return true;
                }
                case "endCon": {
                    int conId = data.getInt(0);
                    Objects.requireNonNull(this.connections.get(conId)).destroyOut();
                    callbackContext.success();
                    return true;
                }
                case "destroyInCon": {
                  int conId = data.getInt(0);
                  Objects.requireNonNull(this.connections.get(conId)).destroyIn();
                  callbackContext.success();
                  return true;
                }
                case "destroyOutCon": {
                    int conId = data.getInt(0);
                    Objects.requireNonNull(this.connections.get(conId)).destroyOut();
                    callbackContext.success();
                    return true;
                }
                case "abortCon": {
                    int conId = data.getInt(0);
                    Objects.requireNonNull(this.connections.get(conId)).abort();
                    callbackContext.success();
                    return true;
                }
                case "readHeadFromCon": {
                    int conId = data.getInt(0);
                    ConnectionController con = Objects.requireNonNull(this.connections.get(conId));
                    cordova.getThreadPool().execute(() -> {
                        try {
                          con.readHead(callbackContext);
                        } catch (Exception e) {
                          callbackContext.error(convertStackTraceToString(e));
                        }
                    });
                    return true;
                }
                case "readFromCon": {
                    int conId = data.getInt(0);
                    int size = data.getInt(1);
                    ConnectionController con = Objects.requireNonNull(this.connections.get(conId));
                    cordova.getThreadPool().execute(() -> {
                        try {
                            boolean done = con.read(size, callbackContext);
                            if (done) {
                                connections.remove(conId);
                            }
                        } catch (Exception e) {
                            callbackContext.error(convertStackTraceToString(e));
                        }
                    });
                    return true;
                }
                default:
                    return false;
            }
        } catch (Exception e) {
            callbackContext.error(convertStackTraceToString(e));
            return true;
        }
    }

    private static String convertStackTraceToString(Throwable throwable)
    {
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw))
        {
            throwable.printStackTrace(pw);
            return sw.toString();
        }
        catch (IOException ioe)
        {
            throw new IllegalStateException(ioe);
        }
    }
}
