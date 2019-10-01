package com.intuit.karate.netty;

import java.io.File;

public class FileChangedWatcher {

  private File file;
  private FeatureServer server;
  private Integer port;
  private boolean ssl;
  private File cert;
  private File key;

  public FileChangedWatcher(File mock, FeatureServer server, Integer port, boolean ssl, File cert, File key) {
    this.file = mock;
    this.server = server;
    this.port = port;
    this.ssl = ssl;
    this.cert = cert;
    this.key = key;
  }

  public void watch() throws InterruptedException {

    long currentModifiedDate = file.lastModified();

    while (true) {

      long newModifiedDate = file.lastModified();

      if (newModifiedDate != currentModifiedDate) {
        currentModifiedDate = newModifiedDate;
        onModified();
      }
      Thread.sleep(500);
    }
  }

  public void onModified() {
    if (server != null) {
      server.stop();
      server = FeatureServer.start(file, port, ssl, cert, key, null);
    }
  }
}
