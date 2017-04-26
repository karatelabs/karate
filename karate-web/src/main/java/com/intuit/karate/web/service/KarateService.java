/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.web.service;

import com.intuit.karate.ScriptEnv;
import com.intuit.karate.cucumber.CucumberUtils;
import com.intuit.karate.cucumber.FeatureWrapper;
import com.intuit.karate.cucumber.KarateBackend;
import com.intuit.karate.web.config.WebSocketLogAppender;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 *
 * @author pthomas3
 */
@Service
public class KarateService {

    private final Map<String, KarateSession> sessions = new ConcurrentHashMap<>();

    public KarateSession createSession(String envString, File featureFile, String[] searchPaths) {
        WebSocketLogAppender appender = createAppender();
        ScriptEnv env = ScriptEnv.init(envString, featureFile, searchPaths, appender.getLogger());
        FeatureWrapper feature = FeatureWrapper.fromFile(featureFile, env);
        return createSession(feature, appender);
    }
    
    public KarateSession createSession(String envString, String featureText) {
        UUID uuid = UUID.randomUUID();
        String sessionId = uuid.toString();
        WebSocketLogAppender appender = new WebSocketLogAppender(sessionId);
        ScriptEnv env = ScriptEnv.init(envString, new File("."), new String[]{"src/test/java"}, appender.getLogger());
        FeatureWrapper feature = FeatureWrapper.fromString(featureText, env);
        return createSession(feature, appender);
    }    
    
    private WebSocketLogAppender createAppender() {
        UUID uuid = UUID.randomUUID();
        String sessionId = uuid.toString();
        return new WebSocketLogAppender(sessionId);        
    }
    
    private KarateSession createSession(FeatureWrapper feature, WebSocketLogAppender appender) {
        KarateBackend backend = CucumberUtils.getBackend(feature.getEnv(), null, null);        
        KarateSession session = new KarateSession(appender.getSessionId(), feature, backend, appender);
        sessions.put(session.getId(), session);
        return session;        
    }

    public KarateSession replaceFeature(KarateSession old, FeatureWrapper feature) {
        KarateSession session = new KarateSession(old.getId(), feature, old.getBackend(), old.getAppender());
        sessions.put(session.getId(), session);
        return session;
    }

    public KarateSession getSession(String id) {
        KarateSession session = sessions.get(id);
        if (session == null) {
            throw new RuntimeException("session not found: " + id);
        }
        return session;
    }

}
