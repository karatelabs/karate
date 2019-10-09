package com.intuit.karate.netty;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileChangedWatcher {

  private static final Logger logger = LoggerFactory.getLogger(FileChangedWatcher.class);

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

  public void watch() throws InterruptedException, IOException {

    try {
      final Path directoryPath = file.toPath().getParent();
      final WatchService watchService = FileSystems.getDefault().newWatchService();
      directoryPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
      while (true) {
        final WatchKey wk = watchService.take();
        for (WatchEvent<?> event : wk.pollEvents()) {
          final Path fileChangedPath = (Path) event.context();
          if (fileChangedPath.endsWith(file.getName())) {
            onModified();
          }
        }
        wk.reset();
      }
    } catch (Exception exception) {
      logger.error("exception when handling change of mock file");
    }
  }

  public void onModified() {
    if (server != null) {
      server.stop();
      server = FeatureServer.start(file, port, ssl, cert, key, null);
    }
  }
}
