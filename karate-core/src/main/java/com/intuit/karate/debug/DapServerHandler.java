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
package com.intuit.karate.debug;

import com.intuit.karate.Json;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.ExecutionHook;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.PerfEvent;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.http.HttpRequestBuilder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class DapServerHandler extends SimpleChannelInboundHandler<DapMessage> implements ExecutionHook, LogAppender {

    private static final Logger logger = LoggerFactory.getLogger(DapServerHandler.class);

    private final DapServer server;

    private Channel channel;
    private int nextSeq;
    private final Map<String, SourceBreakpoints> sourceBreakpointsMap = new HashMap();
    private boolean stepMode;

    private String sourcePath;
    private Step step;
    private ScenarioContext stepContext;
    private Thread runnerThread;
    private boolean interrupted;
    private LogAppender appender = LogAppender.NO_OP;

    public DapServerHandler(DapServer server) {
        this.server = server;
    }

    private StackFrame stackFrame() {
        Path path = step.getFeature().getPath();
        return StackFrame.forSource(path.getFileName().toString(), path.toString(), step.getLine());
    }

    private List<Map<String, Object>> variables() {
        List<Map<String, Object>> list = new ArrayList();
        stepContext.vars.forEach((k, v) -> {
            if (v != null) {
                Map<String, Object> map = new HashMap();
                map.put("name", k);
                map.put("value", v.getAsString());
                map.put("type", v.getTypeAsShortString());
                map.put("variablesReference", 0);
                list.add(map);
            }
        });
        return list;
    }

    private boolean isBreakpoint(String path, int line) {
        SourceBreakpoints sb = sourceBreakpointsMap.get(path);
        if (sb == null) {
            return false;
        }
        return sb.isBreakpoint(line);
    }

    private DapMessage event(String name) {
        return DapMessage.event(++nextSeq, name);
    }

    private DapMessage response(DapMessage req) {
        return DapMessage.response(++nextSeq, req);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        channel = ctx.channel();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DapMessage dm) throws Exception {
        switch (dm.type) {
            case REQUEST:
                handleRequest(dm, ctx);
                break;
            default:
                logger.warn("ignoring message: {}", dm);
        }
    }

    private void handleRequest(DapMessage req, ChannelHandlerContext ctx) {
        switch (req.command) {
            case "initialize":
                ctx.write(response(req)
                        .body("supportsConfigurationDoneRequest", true));
                //.body("supportsStepBack", true)
                //.body("supportsGotoTargetsRequest", true)
                //.body("supportsEvaluateForHovers", true)
                //.body("supportsSetVariable", true)
                //.body("supportsRestartRequest", true)
                //.body("supportTerminateDebuggee", true)
                //.body("supportsTerminateRequest", true));
                ctx.write(event("initialized"));
                break;
            case "setBreakpoints":
                SourceBreakpoints sb = new SourceBreakpoints(req.getArguments());
                sourceBreakpointsMap.put(sb.path, sb);
                logger.debug("source breakpoints: {}", sb);
                ctx.write(response(req)
                        .body("breakpoints", sb.breakpoints));
                break;
            case "launch":
                sourcePath = req.getArgument("program", String.class);
                start();
                ctx.write(response(req));
                break;
            case "threads":
                Json threadsJson = new Json("[{ id: 1, name: 'main' }]");
                ctx.write(response(req).body("threads", threadsJson.asList()));
                break;
            case "stackTrace":
                ctx.write(response(req)
                        .body("stackFrames", Collections.singletonList(stackFrame().toMap())));
                break;
            case "configurationDone":
                ctx.write(response(req));
                break;
            case "scopes":
                Json scopesJson = new Json("[{ name: 'var', variablesReference: 1, presentationHint: 'locals', expensive: false }]");
                ctx.write(response(req)
                        .body("scopes", scopesJson.asList()));
                break;
            case "variables":
                ctx.write(response(req).body("variables", variables()));
                break;
            case "next":
                stepMode = true;
                resume();
                ctx.write(response(req));
                break;
            case "continue":
                stepMode = false;
                resume();
                ctx.write(response(req));
                break;
            case "disconnect":
                boolean restart = req.getArgument("restart", Boolean.class);
                if (restart) {
                    start();
                } else {
                    exit();
                }
                ctx.write(response(req));
                break;
            default:
                logger.warn("unknown command: {}", req);
                ctx.write(response(req));
        }
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    private void start() {
        if (runnerThread != null) {
            runnerThread.interrupt();
        }
        runnerThread = new Thread(() -> Runner.path(sourcePath).hook(this).parallel(1));
        runnerThread.start();
    }

    private void pause() {
        synchronized (this) {
            try {
                wait();
            } catch (Exception e) {
                logger.warn("wait interrupted: {}", e.getMessage());
                interrupted = true;
            }
        }
    }

    private void resume() {
        synchronized (this) {
            notify();
        }
    }

    private void stop(String reason) {
        channel.eventLoop().execute(()
                -> channel.writeAndFlush(event("stopped")
                        .body("reason", reason)
                        .body("threadId", 1)));
    }

    private void exit() {
        channel.eventLoop().execute(()
                -> channel.writeAndFlush(event("exited")
                        .body("exitCode", 0)));
        server.stop();
    }

    @Override
    public boolean beforeStep(Step step, ScenarioContext context) {
        if (interrupted) {
            return false;
        }
        this.step = step;
        this.stepContext = context;
        this.appender = context.appender;
        context.logger.setLogAppender(this); // wrap !
        String path = step.getFeature().getPath().toString();
        int line = step.getLine();
        if (stepMode) {
            stop("step");
            pause();
        } else if (isBreakpoint(path, line)) {
            stop("breakpoint");
            pause();
        }
        return true;
    }

    @Override
    public void afterAll(Results results) {
       if (!interrupted) {
           exit();
       }
    }
    
    @Override
    public void beforeAll(Results results) {
        interrupted = false;
    }    

    @Override
    public void afterStep(StepResult result, ScenarioContext context) {
        context.logger.setLogAppender(appender);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public boolean beforeScenario(Scenario scenario, ScenarioContext context) {
        return !interrupted;
    }

    @Override
    public void afterScenario(ScenarioResult result, ScenarioContext context) {

    }

    @Override
    public boolean beforeFeature(Feature feature, ExecutionContext context) {
        return !interrupted;
    }

    @Override
    public void afterFeature(FeatureResult result, ExecutionContext context) {

    }

    @Override
    public String getPerfEventName(HttpRequestBuilder req, ScenarioContext context) {
        return null;
    }

    @Override
    public void reportPerfEvent(PerfEvent event) {

    }
    @Override
    public String collect() {
        return appender.collect();
    }

    @Override
    public void append(String text) {
        channel.eventLoop().execute(()
                -> channel.writeAndFlush(event("output")
                        .body("output", text)));        
        appender.append(text);
    }

    @Override
    public void close() {

    }

}
