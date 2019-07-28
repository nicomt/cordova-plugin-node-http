package co.nicom.cordova;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class SslUtil {
  final static HostnameVerifier DO_NOT_VERIFY = (hostname, session) -> true;

  protected static byte[] parseDERFromPEM(byte[] pem, String beginDelimiter, String endDelimiter) {
    String data = new String(pem);
    String[] tokens = data.split(beginDelimiter);
    tokens = tokens[1].split(endDelimiter);
    return Base64.decode(tokens[0], Base64.DEFAULT);
  }

  protected static RSAPrivateKey generatePrivateKeyFromDER(byte[] keyBytes)
      throws InvalidKeySpecException, NoSuchAlgorithmException {
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    KeyFactory factory = KeyFactory.getInstance("RSA");
    return (RSAPrivateKey) factory.generatePrivate(spec);
  }

  protected static X509Certificate generateCertificateFromDER(byte[] certBytes) throws CertificateException {
    CertificateFactory factory = CertificateFactory.getInstance("X.509");
    return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
  }

  public static SSLSocketFactory trustAllHostsFactory() throws NoSuchAlgorithmException, KeyManagementException {
    // Create a trust manager that does not validate certificate chains
    TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
      public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return new java.security.cert.X509Certificate[] {};
      }

      public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      }

      public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      }
    } };
    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(null, trustAllCerts, new java.security.SecureRandom());
    return sc.getSocketFactory();
  }

  // Based on https://gist.github.com/rohanag12/07ab7eb22556244e9698
  public static SSLSocketFactory createSecureContext(byte[] caBytes, byte[] certBytes, byte[] keyBytes, String passcode)
      throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException,
      KeyManagementException, InvalidKeySpecException {

    String pass = "changeit";
    if (!passcode.isEmpty())
      pass = passcode;

    TrustManager[] trustManagers = null;
    if (caBytes.length > 0) {

      byte[] caDerBytes = parseDERFromPEM(caBytes, "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
      X509Certificate caCert = generateCertificateFromDER(caDerBytes);

      /**
       * CA certificate is used to authenticate server
       */
      KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      caKeyStore.load(null, null);
      caKeyStore.setCertificateEntry("ca-certificate", caCert);

      TrustManagerFactory trustManagerFactory = TrustManagerFactory
          .getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(caKeyStore);
      trustManagers = trustManagerFactory.getTrustManagers();
    }

    KeyManager[] keyManagers = null;
    if (keyBytes.length > 0) {
      /**
       * Load client certificate
       */
      byte[] certDerBytes = parseDERFromPEM(certBytes, "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
      X509Certificate cert = generateCertificateFromDER(certDerBytes);

      /**
       * Load client private key
       */
      byte[] keyDerBytes = parseDERFromPEM(keyBytes, "-----BEGIN RSA PRIVATE KEY-----",
          "-----END RSA PRIVATE KEY-----");
      RSAPrivateKey key = generatePrivateKeyFromDER(keyDerBytes);

      /**
       * Client key and certificates are sent to server so it can authenticate the
       * client
       */
      KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      clientKeyStore.load(null, null);
      clientKeyStore.setCertificateEntry("certificate", cert);
      clientKeyStore.setKeyEntry("private-key", key, pass.toCharArray(), new Certificate[] { cert });

      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(clientKeyStore, pass.toCharArray());
      keyManagers = keyManagerFactory.getKeyManagers();
    }

    /**
     * Create SSL socket factory
     */
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keyManagers, trustManagers, null);

    /**
     * Return the newly created socket factory object
     */
    return context.getSocketFactory();
  }
}
