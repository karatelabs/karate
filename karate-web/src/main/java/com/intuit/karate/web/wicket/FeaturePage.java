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
package com.intuit.karate.web.wicket;

import com.intuit.karate.web.config.LogAppenderTarget;
import com.intuit.karate.web.config.WebSocketLogAppender;
import com.intuit.karate.web.service.KarateService;
import com.intuit.karate.web.service.KarateSession;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import org.apache.wicket.Application;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.protocol.ws.WebSocketSettings;
import org.apache.wicket.protocol.ws.api.IWebSocketConnection;
import org.apache.wicket.protocol.ws.api.WebSocketBehavior;
import org.apache.wicket.protocol.ws.api.message.ConnectedMessage;
import org.apache.wicket.protocol.ws.api.registry.IKey;
import org.apache.wicket.protocol.ws.api.registry.IWebSocketConnectionRegistry;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeaturePage extends BasePage implements LogAppenderTarget {

    private static final Logger logger = LoggerFactory.getLogger(FeaturePage.class);
    
    @SpringBean(required = true)
    private KarateService service;    

    private String webSocketSessionId;
    private IKey webSocketClientKey;    

    public FeaturePage(String sessionId) {
        replace(new Label(HEADER_ID, ""));
        replace(new FeaturePanel(CONTENT_ID, sessionId));
        replace(new VarsPanel(LEFT_NAV_ID, sessionId));
        add(new WebSocketBehavior() {
            @Override
            protected void onConnect(ConnectedMessage message) {
                KarateSession session = service.getSession(sessionId);
                webSocketSessionId = message.getSessionId();
                webSocketClientKey = message.getKey();
                WebSocketLogAppender appender = session.getAppender();
                appender.setTarget(FeaturePage.this);
                logger.debug("websocket client connected, session: {}", message.getSessionId());
            }
        });
    }
    
    @Override
    public void append(String text) {
        Map<String, Object> map = new HashMap(1);
        map.put("text", text);
        String json = JsonPath.parse(map).jsonString();
        pushJsonWebSocketMessage(json);
    }

    public void pushJsonWebSocketMessage(String json) {
        Application application = Application.get();
        WebSocketSettings settings = WebSocketSettings.Holder.get(application);
        IWebSocketConnectionRegistry registry = settings.getConnectionRegistry();
        IWebSocketConnection connection = registry.getConnection(application, webSocketSessionId, webSocketClientKey);
        if (connection == null) {
            logger.warn("websocket client lookup failed for web-socket session: {}", webSocketSessionId);
            return;
        }
        try {
            connection.sendMessage(json);
        } catch (Exception e) {
            logger.error("websocket push failed", e);
        }
    }    

    @Override
    public void renderHead(IHeaderResponse response) {
        String script = "Wicket.Event.subscribe(\"/websocket/message\", function(jqEvent, message) {\n"
                + "  message = JSON.parse(message);\n"
                + "  if (message.type == 'step') updateStep(message); else { Karate.Ajax.DebugWindow.logInfo(message.text); }\n"
                + "});\n" 
                + "function updateStep(message){ var btn = jQuery('#' + message.buttonId); btn.addClass('btn-success'); }";
        response.render(JavaScriptHeaderItem.forScript(script, "karate-ws-js"));
        response.render(JavaScriptHeaderItem.forReference(KarateJsResourceReference.INSTANCE));
    }

}
