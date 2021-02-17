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
package com.intuit.karate.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.spi.FilterReply;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.RuntimeHook;
import com.intuit.karate.Suite;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author ivangsa
 */
public class VSCodeHook implements RuntimeHook {

    private org.slf4j.Logger log = LoggerFactory.getLogger(this.getClass());

    private final String host;
    private final Integer port;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Queue<Event> queue = new ConcurrentLinkedDeque();
    AsynchronousSocketChannel client;
    Future out;


    enum EventType {
        REQUEST, RESPONSE, FEATURE_START, FEATURE_END, SCENARIO_START, SCENARIO_END
    }

    class Event {
        Long timestamp;
        EventType eventType;
        String thread;

        String currentDir = System.getProperty("user.dir");
        /* root feature name */
        String rootFeature;
        /* root scenario name */
        String rootScenario;

        /* parent hashcode */
        // int parent;
        /* feature name */
        String feature;
        /* scenario name */
        String scenario;
        /* is scenario outline */
        Boolean isOutline;
        Boolean isDinamic;
        /* scenario or feature name */
        String name;
        /* resource filename */
        String resource;
        int line;
        /* caller feature name */
        String caller;
        int callDepth;

        /* http logs info */
        String url;
        String method;
        String status;
        String failureMessage;
        Map<String, String> headers;
        String payload;

        public String getCurrentDir() {
            return currentDir;
        }

        public void setCurrentDir(String currentDir) {
            this.currentDir = currentDir;
        }

        public int getCallDepth() {
            return callDepth;
        }

        public void setCallDepth(int callDepth) {
            this.callDepth = callDepth;
        }

        public String getCaller() {
            return caller;
        }

        public void setCaller(String caller) {
            this.caller = caller;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }

        public EventType getEventType() {
            return eventType;
        }

        public void setEventType(EventType eventType) {
            this.eventType = eventType;
        }

        public String getThread() {
            return thread;
        }

        public void setThread(String thread) {
            this.thread = thread;
        }

        public String getRootFeature() {
            return rootFeature;
        }

        public void setRootFeature(String rootFeature) {
            this.rootFeature = rootFeature;
        }

        public String getRootScenario() {
            return rootScenario;
        }

        public void setRootScenario(String rootScenario) {
            this.rootScenario = rootScenario;
        }

        public String getFeature() {
            return feature;
        }

        public void setFeature(String feature) {
            this.feature = feature;
        }

        public String getScenario() {
            return scenario;
        }

        public void setScenario(String scenario) {
            this.scenario = scenario;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public Boolean getIsOutline() {
            return isOutline;
        }

        public void setIsOutline(Boolean outline) {
            isOutline = outline;
        }

        public Boolean getIsDinamic() {
            return isDinamic;
        }

        public void setIsDinamic(Boolean dinamic) {
            isDinamic = dinamic;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }
    }

    public VSCodeHook() {
        host = System.getProperty("vscode.host");
        String portString = System.getProperty("vscode.port");
        port = portString.matches("\\d+") ? Integer.parseInt(portString) : null;
        log.trace("VSCodeHook {}:{}", host, port);
        if (port != null) {
            interceptKarateLogs();
            try {
                connect();
            } catch (Exception e) {
                log.debug("VSCodeHook error", e);
            }
        }
    }

    private void connect() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        client = AsynchronousSocketChannel.open();
        client.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        Future<Void> future = client.connect(new InetSocketAddress(host != null ? host : "localhost", port));
        future.get(1000, TimeUnit.MILLISECONDS);
    }

    private void interceptKarateLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.intuit.karate");
        Appender appender = new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent eventLog) {
                String message = eventLog.getFormattedMessage();
                if (message.startsWith("request:") || message.startsWith("response time in milliseconds:")) {
                    try {
                        send(createEventFromHttpLogs(message));
                    } catch (Exception e) {
                        log.debug("VSCodeHook error", e);
                    }
                }
            }
        };
        appender.start();
        if (!logger.isDebugEnabled()) {
            // we need to intercept karate debug logs
            // but we filter root appenders by its original level
            ThresholdFilter filter = new ThresholdFilter() {
                @Override
                public FilterReply decide(ILoggingEvent event) {
                    if(event.getLoggerName().startsWith("com.intuit.karate.")) {
                        return super.decide(event);
                    } else {
                        return FilterReply.NEUTRAL;
                    }
                }
            };
            filter.setLevel(logger.getEffectiveLevel().levelStr);
            logger.setLevel(Level.DEBUG);
            logger.iteratorForAppenders().forEachRemaining(a -> {
                a.addFilter(filter);
            });
            Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            rootLogger.iteratorForAppenders().forEachRemaining(a -> {
                a.addFilter(filter);
            });
        }
        logger.addAppender(appender);
    }

    private void send(Event event) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        if (port == null) {
            return;
        }
        if (client == null || client.getRemoteAddress() == null) {
            connect();
        }
        while (out != null && !out.isDone()) { // review if necessary
            log.trace("waiting...");
        }
        log.trace(event.eventType + " " + event.feature + " " + event.status);
        out = client.write(ByteBuffer.wrap(JsonUtils.toJson(event).getBytes(UTF_8)));
        out.get();
    }

    @Override
    public void beforeSuite(Suite suite) {

    }

    @Override
    public void afterSuite(Suite suite) {
    }

    private boolean isSame(Feature f1, Feature f2) {
        if (f1 == f2) {
            return true;
        }
        if (f1 == null || f2 == null) {
            return false;
        }
        return f1.getResource().getPrefixedPath().equals(f2.getResource().getPrefixedPath());
    }

    @Override
    public boolean beforeFeature(FeatureRuntime fr) {
        try {
            if (fr.caller.parentRuntime != null && isSame(fr.feature, fr.caller.parentRuntime.scenario.getFeature())) {
                return true;
            }
            Event event = new Event();
            event.eventType = EventType.FEATURE_START;
            event.thread = Thread.currentThread().getName() + "@" + Thread.currentThread().hashCode();
            event.timestamp = System.currentTimeMillis();
            event.feature = fr.feature.getNameForReport();
            event.rootFeature = fr.rootFeature.feature.getNameForReport();
            event.name = fr.feature.getName();
            event.resource = fr.feature.getResource().getRelativePath();
            event.line = fr.feature.getCallLine();
            if (fr.caller != null && fr.caller.feature != null) {
                // event.parent = fr.caller.hashCode();
                event.caller = fr.caller.feature.getNameForReport();
                event.callDepth = fr.caller.depth;
            }

            send(event);
        } catch (Exception e) {
            log.debug("VSCodeHook error", e);
        }
        return true;
    }

    @Override
    public void afterFeature(FeatureRuntime fr) {
        if (fr.caller.parentRuntime != null && isSame(fr.feature, fr.caller.parentRuntime.scenario.getFeature())) {
            return;
        }
        try {
            Event event = new Event();
            event.eventType = EventType.FEATURE_END;
            event.thread = Thread.currentThread().getName() + "@" + Thread.currentThread().hashCode();
            event.timestamp = System.currentTimeMillis();
            event.feature = fr.feature.getNameForReport();
            event.rootFeature = fr.rootFeature.feature.getNameForReport();
            event.name = fr.feature.getName();
            event.resource = fr.feature.getResource().getRelativePath();
            event.line = fr.feature.getCallLine();
            if (fr.caller != null && fr.caller.feature != null) {
                event.caller = fr.caller.feature.getNameForReport(); // TODO build resource line
                event.callDepth = fr.caller.depth;
            }
            event.status = fr.result.isFailed() ? "KO" : "OK";

            send(event);
        } catch (Exception e) {
            log.debug("VSCodeHook error", e);
        }

    }

    @Override
    public boolean beforeScenario(ScenarioRuntime sr) {
        try {
            Event event = new Event();
            event.eventType = EventType.SCENARIO_START;
            event.thread = Thread.currentThread().getName() + "@" + Thread.currentThread().hashCode();
            event.timestamp = System.currentTimeMillis();
            event.feature = sr.featureRuntime.feature.getNameForReport();
            event.rootFeature = sr.featureRuntime.rootFeature.feature.getNameForReport();
            event.scenario = sr.scenario.getName() + "|" + sr.scenario.getDebugInfo();
            event.resource = sr.featureRuntime.feature.getResource().getRelativePath();
            event.line = sr.scenario.getLine();
            if (sr.scenario.isOutlineExample()) {
                event.isOutline = true;
                event.isDinamic = sr.scenario.isDynamic();
            }
            if (sr.caller != null && sr.caller.feature != null) {
                // event.parent = sr.caller.hashCode();
                event.caller = sr.caller.feature.getNameForReport();
                event.callDepth = sr.caller.depth;
                try {
                    event.payload = sr.caller.arg.getAsString();
                } catch (Exception e) {
                    event.payload = e.getMessage();
                }
            }

            send(event);
        } catch (Exception e) {
            log.debug("VSCodeHook error", e);
        }
        return true;
    }

    @Override
    public void afterScenario(ScenarioRuntime sr) {
        try {
            Event event = new Event();
            event.eventType = EventType.SCENARIO_END;
            event.thread = Thread.currentThread().getName() + "@" + Thread.currentThread().hashCode();
            event.timestamp = System.currentTimeMillis();
            event.feature = sr.featureRuntime.feature.getNameForReport();
            event.rootFeature = sr.featureRuntime.rootFeature.feature.getNameForReport();
            event.scenario = sr.scenario.getName() + "|" + sr.scenario.getDebugInfo();
            event.scenario = sr.scenario.toString();
            event.resource = sr.featureRuntime.feature.getResource().getRelativePath();
            event.line = sr.scenario.getLine();
            if (sr.scenario.isOutlineExample()) {
                event.isOutline = true;
                event.isDinamic = sr.scenario.isDynamic();
            }
            if (sr.caller != null && sr.caller.feature != null) {
                event.caller = sr.caller.feature.getNameForReport();
                event.callDepth = sr.caller.depth;
            }
            event.status = sr.result.isFailed() ? "KO" : "OK";
            event.failureMessage = sr.result.getFailureMessageForDisplay();
            try {
                event.payload = JsonUtils.toJson(sr.result.toKarateJson());
            } catch (Exception e) {
                event.payload = e.getMessage();
            }

            send(event);
        } catch (Exception e) {
            log.debug("VSCodeHook error", e);
        }
    }


    @Override
    public boolean beforeStep(Step step, ScenarioRuntime sr) {
        return true;
    }

    @Override
    public void afterStep(StepResult result, ScenarioRuntime sr) {

    }

    private Event createEventFromHttpLogs(String request) {
        String[] lines = request.split("\n");
        Event event = new Event();
        event.thread = Thread.currentThread().getName() + "@" + Thread.currentThread().hashCode();

        if (lines[0].startsWith("request:")) {
            event.eventType = EventType.REQUEST;
            String[] line1 = lines[1].split(" ");
            event.method = line1[2];
            event.url = line1[3];
        } else {
            event.eventType = EventType.RESPONSE;
            event.status = lines[1].split(" ")[2];
        }

        event.headers = new HashMap<>();
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i];
            if (i == lines.length - 1 && !headersPattern.matcher(line).find()) {
                event.payload = line;
            } else {
                String[] header = line.replaceFirst(headersPattern.pattern(), "").split(": ");
                event.headers.put(header[0], header[1]);
            }
        }

        return event;
    }

    private Pattern headersPattern = Pattern.compile("^\\d [<>] ");
}
