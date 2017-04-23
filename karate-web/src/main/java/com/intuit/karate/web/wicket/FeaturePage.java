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

import com.intuit.karate.web.config.WebSocketLogAppender;
import com.intuit.karate.web.service.KarateService;
import com.intuit.karate.web.service.KarateSession;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.protocol.ws.api.WebSocketBehavior;
import org.apache.wicket.protocol.ws.api.message.ConnectedMessage;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeaturePage extends BasePage {

    private static final Logger logger = LoggerFactory.getLogger(FeaturePage.class);
    
    @SpringBean(required = true)
    private KarateService service;    

    private final LogPanel logPanel;

    public LogPanel getLogPanel() {
        return logPanel;
    }

    public FeaturePage(String sessionId) {
        replace(new FeaturePanel(CONTENT_ID, sessionId));
        replace(new VarsPanel(LEFT_NAV_ID, sessionId));
        logPanel = new LogPanel(STICKY_FOOTER_ID, sessionId);
        add(new WebSocketBehavior() {
            @Override
            protected void onConnect(ConnectedMessage message) {
                KarateSession session = service.getSession(sessionId);
                WebSocketLogAppender appender = session.getAppender();
                logPanel.onConnect(message);
                appender.setTarget(logPanel);
                logger.debug("websocket client connected, session: {}", message.getSessionId());
            }
        });
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        String script = "Wicket.Event.subscribe(\"/websocket/message\", function(jqEvent, message) {\n"
                + "  message = JSON.parse(message);\n"
                + "  if (message.type == 'step') updateStep(message); else updateLog(message);\n"
                + "});\n" 
                + "function updateStep(message){ var btn = jQuery('#' + message.buttonId); btn.addClass('btn-success'); }\n"
                + logPanel.getUpdateScript();
        response.render(JavaScriptHeaderItem.forScript(script, "karate-ws-js"));
    }

}
