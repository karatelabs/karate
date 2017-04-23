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

import com.intuit.karate.ScriptEnv;
import com.intuit.karate.cucumber.FeatureWrapper;
import com.intuit.karate.web.service.KarateService;
import com.intuit.karate.web.service.KarateSession;
import java.io.File;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HomePanel extends Panel {
    
    private static final Logger logger = LoggerFactory.getLogger(HomePanel.class);
    
    @SpringBean(required = true)
    private KarateService service;    
    
    private String text;

    public HomePanel(String id) {
        super(id);
        setDefaultModel(new CompoundPropertyModel(this));
        Form form = new Form("form") {
            @Override
            protected void onSubmit() {
                logger.debug("text is: {}", text);
                ScriptEnv env = ScriptEnv.init("dev", new File("src/test/java"));
                FeatureWrapper feature = FeatureWrapper.fromString(text, env);
                KarateSession session = service.createSession(feature);
                setResponsePage(new FeaturePage(session.getId()));
            }            
        };
        form.add(new TextArea("text"));
        add(form);
        add(new FeedbackPanel("feedback"));
    }
    
}