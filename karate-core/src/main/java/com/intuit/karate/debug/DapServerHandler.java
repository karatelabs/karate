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

import com.intuit.karate.Runner;
import com.intuit.karate.RunnerOptions;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.ExecutionHook;
import com.intuit.karate.core.ExecutionHookFactory;
import com.intuit.karate.core.Result;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.Step;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class DapServerHandler extends SimpleChannelInboundHandler<DapMessage> implements ExecutionHookFactory {

    private static final Logger logger = LoggerFactory.getLogger(DapServerHandler.class);

    private final DapServer server;

    private Channel channel;
    private int nextSeq;
    private long nextFrameId;
    private long focusedFrameId;
    private Thread runnerThread;

    private final Map<String, SourceBreakpoints> BREAKPOINTS = new ConcurrentHashMap();
    protected final Map<Long, DebugThread> THREADS = new ConcurrentHashMap();
    protected final Map<Long, ScenarioContext> FRAMES = new ConcurrentHashMap();

    private boolean singleFeature;
    private String launchCommand;
    private String preStep;

    public DapServerHandler(DapServer server) {
        this.server = server;
    }

    private static final String TEST_CLASSES = "/test-classes/";
    private static final String CLASSES_TEST = "/classes/java/test/";

    private static int findPos(String path) {
        int pos = path.indexOf(TEST_CLASSES);
        if (pos != -1) {
            return pos + TEST_CLASSES.length();
        }
        pos = path.indexOf(CLASSES_TEST);
        if (pos != -1) {
            return pos + CLASSES_TEST.length();
        }
        return -1;
    }

    private SourceBreakpoints lookup(String pathEnd) {
        for (Map.Entry<String, SourceBreakpoints> entry : BREAKPOINTS.entrySet()) {
            if (entry.getKey().endsWith(pathEnd)) {
                return entry.getValue();
            }
        }
        return null;
    }

    protected boolean isBreakpoint(Step step, int line) {
        String path = step.getFeature().getPath().toString();
        int pos = findPos(path);
        SourceBreakpoints sb;
        if (pos != -1) {
            sb = lookup(path.substring(pos));
        } else {
            sb = BREAKPOINTS.get(path);
        }
        if (sb == null) {
            return false;
        }
        return sb.isBreakpoint(line);
    }

    private DebugThread thread(DapMessage dm) {
        Number threadId = dm.getThreadId();
        if (threadId == null) {
            return null;
        }
        return THREADS.get(threadId.longValue());
    }

    private List<Map<String, Object>> frames(Number threadId) {
        if (threadId == null) {
            return Collections.EMPTY_LIST;
        }
        DebugThread thread = THREADS.get(threadId.longValue());
        if (thread == null) {
            return Collections.EMPTY_LIST;
        }
        List<Long> frameIds = new ArrayList(thread.stack);
        Collections.reverse(frameIds);
        List<Map<String, Object>> list = new ArrayList(frameIds.size());
        for (Long frameId : frameIds) {
            ScenarioContext context = FRAMES.get(frameId);
            list.add(new StackFrame(frameId, context).toMap());
        }
        return list;
    }

    private List<Map<String, Object>> variables(Number frameId) {
        if (frameId == null) {
            return Collections.EMPTY_LIST;
        }
        focusedFrameId = frameId.longValue();
        ScenarioContext context = FRAMES.get(frameId.longValue());
        if (context == null) {
            return Collections.EMPTY_LIST;
        }
        List<Map<String, Object>> list = new ArrayList();
        context.vars.forEach((k, v) -> {
            if (v != null) {
                Map<String, Object> map = new HashMap();
                map.put("name", k);
                try {
                    map.put("value", v.getAsStringRemovingCyclicReferences());
                } catch (Exception e) {
                    logger.warn("unable to convert to string: {} - {}", k, v);
                    map.put("value", "(unknown)");
                }
                map.put("type", v.getTypeAsShortString());
                map.put("evaluateName", k);
                // if > 0 , this can be used  by client to request more info
                map.put("variablesReference", 0);
                list.add(map);
            }
        });
        return list;
    }

    private DapMessage event(String name) {
        return DapMessage.event(++nextSeq, name);
    }

    private DapMessage response(DapMessage req) {
        return DapMessage.response(++nextSeq, req);
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
                        .body("supportsConfigurationDoneRequest", true)
                        .body("supportsRestartRequest", true)
                        .body("supportsStepBack", true)
                        .body("supportsVariableType", true)
                        .body("supportsClipboardContext", true));
                ctx.write(event("initialized"));
                ctx.write(event("output").body("output", "debug server listening on port: " + server.getPort() + "\n"));
                break;
            case "setBreakpoints":
                SourceBreakpoints sb = new SourceBreakpoints(req.getArguments());
                BREAKPOINTS.put(sb.path, sb);
                logger.trace("source breakpoints: {}", sb);
                ctx.write(response(req).body("breakpoints", sb.breakpoints));
                break;
            case "launch":
                // normally a single feature full path, but can be set with any valid karate.options
                // for e.g. "-t ~@ignore -T 5 classpath:demo.feature"
                launchCommand = StringUtils.trimToNull(req.getArgument("karateOptions", String.class));
                preStep = StringUtils.trimToNull(req.getArgument("debugPreStep", String.class));
                if (preStep != null) {
                    logger.debug("using pre-step: {}", preStep);
                }
                if (launchCommand == null) {
                    launchCommand = req.getArgument("feature", String.class);
                    singleFeature = true;
                }
                start();
                ctx.write(response(req));
                break;
            case "threads":
                List<Map<String, Object>> list = new ArrayList(THREADS.size());
                THREADS.values().forEach(v -> {
                    Map<String, Object> map = new HashMap();
                    map.put("id", v.id);
                    map.put("name", v.name);
                    list.add(map);
                });
                ctx.write(response(req).body("threads", list));
                break;
            case "stackTrace":
                ctx.write(response(req).body("stackFrames", frames(req.getThreadId())));
                break;
            case "configurationDone":
                ctx.write(response(req));
                break;
            case "scopes":
                Number frameId = req.getArgument("frameId", Number.class);
                Map<String, Object> scope = new HashMap();
                scope.put("name", "In Scope");
                scope.put("variablesReference", frameId);
                scope.put("presentationHint", "locals");
                scope.put("expensive", false);
                ctx.write(response(req).body("scopes", Collections.singletonList(scope)));
                break;
            case "variables":
                Number variablesReference = req.getArgument("variablesReference", Number.class);
                ctx.write(response(req).body("variables", variables(variablesReference)));
                break;
            case "next":
                thread(req).next().resume();
                ctx.write(response(req));
                break;
            case "stepBack":
            case "reverseContinue": // since we can't disable this button
                thread(req).stepBack().resume();
                ctx.write(response(req));
                break;
            case "stepIn":
                thread(req).stepIn().resume();
                ctx.write(response(req));
                break;
            case "stepOut":
                thread(req).stepOut().resume();
                ctx.write(response(req));
                break;
            case "continue":
                thread(req)._continue().resume();
                ctx.write(response(req));
                break;
            case "pause":
                ctx.write(response(req));
                thread(req).pause();
                break;
            case "evaluate":
                String expression = req.getArgument("expression", String.class);
                Number evalFrameId = req.getArgument("frameId", Number.class);
                String reqContext = req.getArgument("context", String.class);
                ScenarioContext evalContext = FRAMES.get(evalFrameId.longValue());
                String result;
                if ("clipboard".equals(reqContext)) {
                    try {
                        ScriptValue sv = evalContext.vars.get(expression);
                        result = sv.getAsPrettyString();
                    } catch (Exception e) {
                        result = "[error] " + e.getMessage();
                    }
                } else {
                    evaluatePreStep(evalContext);
                    Result evalResult = evalContext.evalAsStep(expression);
                    if (evalResult.isFailed()) {
                        result = "[error] " + evalResult.getError().getMessage();
                    } else {
                        result = "[done]";
                    }
                }
                ctx.write(response(req)
                        .body("result", result)
                        .body("variablesReference", 0)); // non-zero means can be requested by client                 
                break;
            case "restart":
                ScenarioContext context = FRAMES.get(focusedFrameId);
                if (context != null && context.hotReload()) {
                    output("[debug] hot reload successful");
                } else {
                    output("[debug] hot reload requested, but no steps edited");
                }
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

    protected void evaluatePreStep(ScenarioContext context) {
        if (preStep == null) {
            return;
        }
        Result result = context.evalAsStep(preStep);
        if (result.isFailed()) {
            output("[debug] pre-step failed: " + preStep + " - " + result.getError().getMessage());
        } else {
            output("[debug] pre-step success: " + preStep);
        }
    }

    @Override
    public ExecutionHook create() {
        return new DebugThread(Thread.currentThread(), this);
    }

    private void start() {
        logger.debug("command line: {}", launchCommand);
        RunnerOptions options;
        if (singleFeature) {
            options = new RunnerOptions();
            options.addFeature(launchCommand);
        } else {
            options = RunnerOptions.parseCommandLine(launchCommand);
        }
        if (runnerThread != null) {
            runnerThread.interrupt();
        }
        runnerThread = new Thread(() -> {
            Runner.path(options.getFeatures())
                    .hookFactory(this)
                    .tags(options.getTags())
                    .scenarioName(options.getName())
                    .parallel(options.getThreads());
            // if we reached here, run was successful
            exit();
        });
        runnerThread.start();
    }

    protected void stopEvent(long threadId, String reason, String description) {
        channel.eventLoop().execute(() -> {
            DapMessage message = event("stopped")
                    .body("reason", reason)
                    .body("threadId", threadId);
            if (description != null) {
                message.body("description", description);
            }
            channel.writeAndFlush(message);
        });
    }

    protected void continueEvent(long threadId) {
        channel.eventLoop().execute(() -> {
            DapMessage message = event("continued")
                    .body("threadId", threadId);
            channel.writeAndFlush(message);
        });
    }

    private void exit() {
        channel.eventLoop().execute(()
                -> channel.writeAndFlush(event("exited")
                        .body("exitCode", 0)));
        server.stop();
        System.exit(0);
    }

    protected long nextFrameId() {
        return ++nextFrameId;
    }

    protected void output(String text) {
        channel.eventLoop().execute(()
                -> channel.writeAndFlush(event("output")
                        .body("output", text)));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        channel = ctx.channel();
    }

}
