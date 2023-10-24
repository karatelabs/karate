package com.intuit.karate.http.cert;

import com.intuit.karate.Logger;
import com.linecorp.armeria.internal.shaded.bouncycastle.asn1.x500.X500Name;
import com.linecorp.armeria.internal.shaded.bouncycastle.cert.X509CertificateHolder;
import com.linecorp.armeria.internal.shaded.bouncycastle.cert.X509v3CertificateBuilder;
import com.linecorp.armeria.internal.shaded.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import com.linecorp.armeria.internal.shaded.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import com.linecorp.armeria.internal.shaded.bouncycastle.operator.ContentSigner;
import com.linecorp.armeria.internal.shaded.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThrowableUtil;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

public final class SelfSignedCertGenerator {
    private static final Logger logger = new Logger();
    private static final Provider PROVIDER = Security.getProvider("SUN");
    private static final String DEFAULT_FQDN = "localhost";
    private static final Date DEFAULT_NOT_BEFORE = new Date(SystemPropertyUtil.getLong("io.netty.selfSignedCertificate.defaultNotBefore", System.currentTimeMillis() - 31536000000L));
    private static final Date DEFAULT_NOT_AFTER = new Date(SystemPropertyUtil.getLong("io.netty.selfSignedCertificate.defaultNotAfter", 253402300799000L));
    private static final String ALGORITHM = "RSA";
    private static final int DEFAULT_KEY_LENGTH_BITS = SystemPropertyUtil.getInt("io.netty.handler.ssl.util.selfSignedKeyStrength", 2048);

    private File certificate;
    private File privateKey;
    private X509Certificate cert;
    private PrivateKey key;

    public SelfSignedCertGenerator() throws CertificateException {
        KeyPair keypair;
        SecureRandom random = ThreadLocalInsecureRandom.current();
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
            keyGen.initialize(DEFAULT_KEY_LENGTH_BITS, random);
            keypair = keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException var24) {
            throw new Error(var24);
        }

        String[] paths;
        try {
            paths = generate(DEFAULT_FQDN, keypair, random, DEFAULT_NOT_BEFORE, DEFAULT_NOT_AFTER);
        } catch (Throwable var23) {
            logger.debug("Failed to generate a self-signed X.509 certificate:", var23);
            CertificateException certificateException = new CertificateException("No provider succeeded to generate a self-signed certificate. See debug log for the root cause.", var23);
            ThrowableUtil.addSuppressed(certificateException, var23);
            throw certificateException;
        }

        this.certificate = new File(paths[0]);
        this.privateKey = new File(paths[1]);
        this.key = keypair.getPrivate();
        FileInputStream certificateInput = null;

        try {
            certificateInput = new FileInputStream(this.certificate);
            this.cert = (X509Certificate) CertificateFactory.getInstance("X509").generateCertificate(certificateInput);
        } catch (Exception var21) {
            throw new CertificateEncodingException(var21);
        } finally {
            if (certificateInput != null) {
                try {
                    certificateInput.close();
                } catch (IOException var25) {
                    logger.warn("Failed to close a file: " + this.certificate, var25);
                }
            }
        }
    }

    public File getCertificate() {
        return certificate;
    }

    public File getPrivateKey() {
        return privateKey;
    }

    private String[] generate(String fqdn, KeyPair keypair, SecureRandom random, Date notBefore, Date notAfter) throws Exception {
        PrivateKey key = keypair.getPrivate();
        X500Name owner = new X500Name("CN=" + fqdn);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(owner, new BigInteger(64, random), notBefore, notAfter, owner, keypair.getPublic());
        ContentSigner signer = (new JcaContentSignerBuilder("SHA256WithRSAEncryption")).build(key);
        X509CertificateHolder certHolder = builder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider(PROVIDER).getCertificate(certHolder);
        cert.verify(keypair.getPublic());
        return newSelfSignedCertificate(fqdn, key, cert);
    }

    private String[] newSelfSignedCertificate(String fqdn, PrivateKey key, X509Certificate cert) throws IOException, CertificateEncodingException {
        ByteBuf wrappedBuf = Unpooled.wrappedBuffer(key.getEncoded());
        ByteBuf encodedBuf;
        String keyText;
        try {
            encodedBuf = Base64.encode(wrappedBuf, true);
            try {
                keyText = "-----BEGIN PRIVATE KEY-----\n" + encodedBuf.toString(CharsetUtil.US_ASCII) + "\n-----END PRIVATE KEY-----\n";
            } finally {
                encodedBuf.release();
            }
        } finally {
            wrappedBuf.release();
        }
        fqdn = fqdn.replaceAll("[^\\w.-]", "x");
        File keyFile = PlatformDependent.createTempFile("keyutil_" + fqdn + '_', ".key", (File)null);
        keyFile.deleteOnExit();
        OutputStream keyOut = new FileOutputStream(keyFile);
        try {
            keyOut.write(keyText.getBytes(CharsetUtil.US_ASCII));
            keyOut.close();
            keyOut = null;
        } finally {
            if (keyOut != null) {
                safeClose(keyFile, keyOut);
                safeDelete(keyFile);
            }
        }
        wrappedBuf = Unpooled.wrappedBuffer(cert.getEncoded());
        String certText;
        try {
            encodedBuf = Base64.encode(wrappedBuf, true);
            try {
                certText = "-----BEGIN CERTIFICATE-----\n" + encodedBuf.toString(CharsetUtil.US_ASCII) + "\n-----END CERTIFICATE-----\n";
            } finally {
                encodedBuf.release();
            }
        } finally {
            wrappedBuf.release();
        }
        File certFile = PlatformDependent.createTempFile("keyutil_" + fqdn + '_', ".crt", (File)null);
        certFile.deleteOnExit();
        OutputStream certOut = new FileOutputStream(certFile);
        try {
            certOut.write(certText.getBytes(CharsetUtil.US_ASCII));
            certOut.close();
            certOut = null;
        } finally {
            if (certOut != null) {
                safeClose(certFile, certOut);
                safeDelete(certFile);
                safeDelete(keyFile);
            }
        }
        return new String[]{certFile.getPath(), keyFile.getPath()};
    }

    private static void safeDelete(File certFile) {
        if (!certFile.delete()) {
            logger.warn("Failed to delete a file: " + certFile);
        }
    }

    private static void safeClose(File keyFile, OutputStream keyOut) {
        try {
            keyOut.close();
        } catch (IOException var3) {
            logger.warn("Failed to close a file: " + keyFile, var3);
        }
    }
}