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


import com.intuit.karate.cucumber.*;
import com.intuit.karate.web.service.KarateService;
import com.intuit.karate.web.service.KarateSession;
import com.intuit.karate.web.wicket.model.FeatureModel;
import com.intuit.karate.web.wicket.model.FeatureSectionModel;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.AjaxEditableLabel;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeaturePanel extends Panel {
    private static final Logger logger = LoggerFactory.getLogger(FeaturePanel.class);

    @SpringBean(required = true)
    private KarateService service;    

    public FeaturePanel(String id, String sessionId) {
        super(id);
        FeatureModel model = new FeatureModel(sessionId);
        setDefaultModel(new CompoundPropertyModel(model));
        add(new Label("feature", new AbstractReadOnlyModel<String>() {
            @Override
            public String getObject() {
                return model.getObject().getEnv().featureName;
            }            
        }));
        add(new AjaxEditableLabel("env", new IModel<String>() {
            @Override
            public String getObject() {
                return model.getObject().getEnv().env;
            }

            @Override
            public void setObject(String object) {
                service.updateSessionEnv(sessionId, object);
            }

            @Override
            public void detach() {
            }
        }));
        add(new BookmarkablePageLink("home", HomePage.class));
        add(new IndicatingAjaxLink("export") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                KarateSession session =  service.getSession(sessionId);
                FeatureWrapper featureWrapper = session.getFeature();
                FeaturePanel.this.setResponsePage(new HomePage(featureWrapper.getText()));
            }
        });
        ListView<FeatureSection> listView = new ListView<FeatureSection>("sections") {
            @Override
            protected void populateItem(ListItem<FeatureSection> li) {
                FeatureSection section = li.getModelObject();
                FeatureSectionModel sectionModel = new FeatureSectionModel(sessionId, section);
                li.add(new FeatureSectionPanel("section", sectionModel));
            }
        };
        add(listView);
    }

}
