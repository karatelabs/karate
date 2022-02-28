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
import com.intuit.karate.KarateException;
import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.shell.Command;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author pthomas3
 */
public class DockerTarget implements Target {

    protected static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Command.class);
    private final String imageId;
    private String containerId;
    private Function<Integer, String> command;
    private final Map<String, Object> options;
    private boolean pull = false;
    private boolean karateChrome = false;

    public DockerTarget(String dockerImage) {
        this(Collections.singletonMap("docker", dockerImage));
    }

    public DockerTarget(Map<String, Object> options) {
        this.options = options;
        if (options != null) {
            imageId = (String) options.get("docker");
            Integer vncPort = (Integer) options.get("vncPort");
            String secComp = (String) options.get("secComp");
            Boolean temp = (Boolean) options.get("pull");
            pull = temp == null ? false : temp;
            StringBuilder sb = new StringBuilder();
            sb.append("docker run -d -e KARATE_SOCAT_START=true");
            if (secComp == null) {
                sb.append(" --cap-add=SYS_ADMIN");
            } else {
                sb.append(" --security-opt seccomp=").append(secComp);
            }
            if (vncPort != null) {
                sb.append(" -p ").append(vncPort).append(":5900");
            }
            if (imageId != null) {
                if (imageId.contains("/chrome-headless")) {
                    command = p -> sb.toString() + " -p 9222 " + imageId;
                } else if (imageId.contains("/karate-chrome")) {
                    karateChrome = true;
                    command = p -> sb.toString() + " -p 9222 " + imageId;
                }
            }
        } else {
            imageId = null;
        }
    }

    public void setCommand(Function<Integer, String> command) {
        this.command = command;
    }

    public Function<Integer, String> getCommand() {
        return command;
    }

    @Override
    public Map<String, Object> start(ScenarioRuntime sr) {
        if (command == null) {
            throw new RuntimeException("docker target command (function) not set");
        }
        if (imageId != null && pull) {
            sr.logger.debug("attempting to pull docker image: {}", imageId);
            Command.execLine(null, "docker pull " + imageId);
        }
        containerId = Command.execLine(null, command.apply(null));
        int port = this.getContainerPort(containerId);
        Map<String, Object> map = new HashMap();
        if (options != null) {
            map.putAll(options);
        }

        boolean remoteHost = options != null && options.get("remoteHost") != null && (Boolean) options.get("remoteHost");
        boolean useDockerHost = options != null && options.get("useDockerHost") != null && (Boolean) options.get("useDockerHost");
        String host = "127.0.0.1";
        if (remoteHost) {
            String containerName = Command.execLine(null, "docker inspect -f '{{.Name}}' " + containerId + " | cut -c 2-");
            host = useDockerHost ? "host.docker.internal" : containerName;
        }

        map.put("start", false);
        map.put("host", host);
        map.put("port", port);
        map.put("type", "chrome");
        Command.waitForHttp("http://" + host + ":" + port + "/json");
        return map;
    }


    @Override
    public Map<String, Object> stop(ScenarioRuntime sr) {
        Command.execLine(null, "docker stop " + containerId);
        if (!karateChrome) { // no video
            Command.execLine(null, "docker rm " + containerId);
            return Collections.EMPTY_MAP;
        }
        String shortName = containerId.contains("_") ? containerId : StringUtils.truncate(containerId, 12, false);
        String dirName = "karate-chrome_" + shortName;
        String buildDir = sr.featureRuntime.suite.buildDir;
        String resultsDir = buildDir + File.separator + dirName;
        Command.execLine(null, "docker cp " + containerId + ":/tmp " + resultsDir);
        Command.execLine(null, "docker rm " + containerId);
        String video = resultsDir + File.separator + "karate.mp4";
        File file = new File(video);
        if (!file.exists()) {
            sr.logger.warn("video file missing: {}", file);
            return Collections.EMPTY_MAP;
        }
        File copy = new File(buildDir + File.separator + dirName + ".mp4");
        FileUtils.copy(file, copy);
        return Collections.singletonMap("video", copy.getPath());
    }

    private int getContainerPort(String containerId) {
        try {
            String dockerPort = Command.execLine((File) null, "docker port " + containerId + " 9222/tcp");
            Pattern portPattern = Pattern.compile("(\\d+?\\.){3}\\d:(\\d+)");
            Matcher matcher = portPattern.matcher(dockerPort);
            if (matcher.matches()) {
                try {
                    return Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException e) {
                    stopAndRemoveContainerOnException(containerId);
                    throw new KarateException("Error fetching port from started docker container", e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("command error: {} - {}", e.getMessage());
        }
        stopAndRemoveContainerOnException(containerId);
        throw new KarateException("Error fetching port from started docker container");
    }

    public String getContainerId() {
        return this.containerId;
    }

    private void stopAndRemoveContainerOnException(String containerId) {
        Command.execLine(null, "docker stop " + containerId);
        Command.execLine(null, "docker rm " + containerId);
        LOGGER.info("Stopping and removing the container due to error");
    }

}
