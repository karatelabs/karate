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
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import org.apache.wicket.Application;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.protocol.ws.WebSocketSettings;
import org.apache.wicket.protocol.ws.api.IWebSocketConnection;
import org.apache.wicket.protocol.ws.api.message.ConnectedMessage;
import org.apache.wicket.protocol.ws.api.registry.IKey;
import org.apache.wicket.protocol.ws.api.registry.IWebSocketConnectionRegistry;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogPanel extends Panel implements LogAppenderTarget {

    private static final Logger logger = LoggerFactory.getLogger(LogPanel.class);
    
    @SpringBean(required = true)
    private KarateService service;    

    private String webSocketSessionId;
    private IKey webSocketClientKey;

    public void onConnect(ConnectedMessage message) {
        this.webSocketSessionId = message.getSessionId();
        this.webSocketClientKey = message.getKey();
    }

    public String getUpdateScript() {
        return "function updateLog(message) {\n"
                + "  var textArea = jQuery('#" + textArea.getMarkupId() + "');\n"
                + "  textArea.append(message.text);\n"
                + "};";
    }

    private final TextArea textArea;
    private boolean showing;

    public void setShowing(boolean showing) {
        this.showing = showing;
    }

    public boolean isShowing() {
        return showing;
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
    public void append(String text) {
        Map<String, Object> map = new HashMap(1);
        map.put("text", text);
        String json = JsonPath.parse(map).jsonString();
        pushJsonWebSocketMessage(json);
    }

    public LogPanel(String id, String sessionId) {
        super(id);
        setOutputMarkupId(true);
        add(new AjaxLink("close") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                Component logPanel = LogPanel.this;
                logPanel = logPanel.replaceWith(new Label(BasePage.STICKY_FOOTER_ID, ""));
                showing = false;
                target.add(logPanel);
            }
        });
        add(new AjaxLink("clear") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                service.getSession(sessionId).getAppender().clearBuffer();
                target.add(LogPanel.this);
            }
        });
        IModel<String> model = new AbstractReadOnlyModel<String>() {
            @Override
            public String getObject() {
                return service.getSession(sessionId).getAppender().getBuffer();
            }
        };
        textArea = new TextArea("text", model);
        textArea.setOutputMarkupId(true); // for js get by id
        add(textArea);
    }

}
