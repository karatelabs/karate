/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.netty;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileChangedWatcher {

    private static final Logger logger = LoggerFactory.getLogger(FileChangedWatcher.class);

    private final List<File> files;
    private FeatureServer server;
    private final Integer port;
    private final boolean ssl;
    private final File cert;
    private final File key;

    public FileChangedWatcher(File mock, FeatureServer server, Integer port, boolean ssl, File cert, File key) {
        this(Arrays.asList(mock), server, port, ssl, cert, key);
    }

    public FileChangedWatcher(List<File> mocks, FeatureServer server, Integer port, boolean ssl, File cert, File key) {
        this.files = mocks;
        this.server = server;
        this.port = port;
        this.ssl = ssl;
        this.cert = cert;
        this.key = key;
    }

    public void watch() throws InterruptedException, IOException {
        try {
            final WatchService watchService = FileSystems.getDefault().newWatchService();
            for(File file: files) {
                final Path directoryPath = file.toPath().getParent();

                directoryPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            }

            while (true) {
                final WatchKey wk = watchService.take();
                for (WatchEvent<?> event : wk.pollEvents()) {
                    final Path fileChangedPath = (Path) event.context();

                    if (files.stream().anyMatch((file) -> fileChangedPath.endsWith(file.getName()))) {
                        onModified();
                    }
                }
                wk.reset();
            }
        } catch (Exception e) {
            logger.error("exception when handling change of mock file: {}", e.getMessage());
        }
    }

    public void onModified() {
        if (server != null) {
            server.stop();
            server = FeatureServer.start(files, port, ssl, cert, key, null);
        }
    }

}
