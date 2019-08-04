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
package com.intuit.karate.driver;

import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import com.intuit.karate.shell.Command;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class DockerTarget implements Target {

    protected static final Logger logger = LoggerFactory.getLogger(Command.class);

    private String name;
    private Function<Integer, String> command;
    private Map<String, Object> options;

    public DockerTarget() {
        this(null);
    }

    public DockerTarget(Map<String, Object> options) {
        this.options = options;
    }

    public void setCommand(Function<Integer, String> command) {
        this.command = command;
    }

    public Function<Integer, String> getCommand() {
        return command;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    @Override
    public Map<String, Object> start() {
        if (command == null) {
            throw new RuntimeException("docker target command (function) not set");
        }
        if (options != null) {
            String dockerImage = (String) options.get("dockerImage");
            if (dockerImage != null) {
                logger.debug("attempting to pull docker image: {}", dockerImage);
                Command.execLine(null, "docker pull " + dockerImage);
            }
        }
        int port = Command.getFreePort();
        name = Command.execLine(null, command.apply(port));
        Map<String, Object> map = new HashMap();
        map.put("port", port);
        if (options != null) {
            map.putAll(options);
        }
        Command.waitForHttp("http://127.0.0.1:" + port + "/json");
        return map;
    }

    @Override
    public Map<String, Object> stop() {
        Command.execLine(null, "docker stop " + name);
        String shortName = name.contains("_") ? name : StringUtils.truncate(name, 12, false);
        String dirName = "karate-chrome_" + shortName;
        String resultsDir = Command.getBuildDir() + File.separator + dirName;
        Command.execLine(null, "docker cp " + name + ":/tmp " + resultsDir);
        String video = resultsDir + File.separator + "karate.mp4";
        File file = new File(video);
        if (!file.exists()) {
            logger.warn("video file missing: {}", file);
            return Collections.EMPTY_MAP;
        }
        // some hacking to respect the way the cucumber html report works
        // AND preserve relative paths TODO
        String newName = "embeddings" + File.separator + shortName + "_" + file.getName();
        String dest = Command.getBuildDir() + File.separator
                + "cucumber-html-reports" + File.separator + newName;
        FileUtils.copy(file, new File(dest));
        return Collections.singletonMap("video", newName);
    }

}
