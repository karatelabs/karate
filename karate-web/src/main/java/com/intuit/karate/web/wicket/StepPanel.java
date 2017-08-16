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

import com.intuit.karate.cucumber.FeatureWrapper;
import com.intuit.karate.cucumber.KarateBackend;
import com.intuit.karate.cucumber.StepResult;
import com.intuit.karate.cucumber.StepWrapper;
import com.intuit.karate.web.wicket.model.StepModel;
import com.intuit.karate.web.service.KarateService;
import com.intuit.karate.web.service.KarateSession;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxLink;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class StepPanel extends Panel {

    private static final Logger logger = LoggerFactory.getLogger(StepPanel.class);

    @SpringBean(required = true)
    private KarateService service;

    private final IndicatingAjaxLink runButton;
    private Boolean pass;

    public void setPass(Boolean pass) {
        this.pass = pass;
    }        

    public IndicatingAjaxLink getRunButton() {
        return runButton;
    }

    public StepPanel(String id, StepModel model) {
        super(id);
        setOutputMarkupId(true);
        Form form = new Form("form");
        add(form);
        AttributeModifier priorRows = new AttributeModifier("rows", new AbstractReadOnlyModel() {
            @Override
            public Object getObject() {
                return model.getObject().getPriorTextLineCount();
            }
        });
        IModel<String> priorTextModel = new AbstractReadOnlyModel<String>() {
            @Override
            public String getObject() {
                return model.getObject().getPriorText();
            }
        };
        AttributeModifier priorTextClass = new AttributeModifier("class", new AbstractReadOnlyModel() {
            @Override
            public Object getObject() {
                StepWrapper sw = model.getObject();
                String cls = "form-control";
                if (sw.getPriorTextLineCount() == 1) {
                    cls = cls + " kt-one-line";
                }
                return cls;
            }
        });
        TextArea priorText = new TextArea("priorText", priorTextModel) {
            @Override
            protected void onConfigure() {
                setVisible(model.getObject().isPriorTextPresent());
            }
        };
        form.add(priorText.add(priorRows).add(priorTextClass));
        AttributeModifier rows = new AttributeModifier("rows", new AbstractReadOnlyModel() {
            @Override
            public Object getObject() { // count number of lines
                return model.getObject().getLineCount();
            }
        });
        AjaxFormComponentUpdatingBehavior ub = new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                logger.debug("changed");
            }
        };
        AttributeModifier txtClass = new AttributeModifier("class", new AbstractReadOnlyModel() {
            @Override
            public Object getObject() {
                StepWrapper sw = model.getObject();
                String cls = "form-control";
                int lineCount = sw.getLineCount();
                if (lineCount == 1) {
                    cls = cls + " kt-one-line";
                }
                if (sw.isHttpCall()) {
                    cls = cls + " kt-http-call";
                }
                return cls;
            }
        });
        form.add(new TextArea("text", new IModel<String>() {
            @Override
            public String getObject() {
                return model.getObject().getText();
            }

            @Override
            public void setObject(String text) {
                logger.debug("text value changed: {}", text);
                StepWrapper step = model.getObject();
                KarateSession session = service.getSession(model.getSessionId());
                FeatureWrapper newFeature = session.getFeature().replaceLines(step.getStartLine(), step.getEndLine(), text);
                service.replaceFeature(session, newFeature);
                model.detach(); // force reload
            }

            @Override
            public void detach() {
            }
        }).add(rows).setOutputMarkupId(true).add(ub).add(txtClass));
        AttributeModifier btnClass = new AttributeModifier("class", new AbstractReadOnlyModel() {
            @Override
            public Object getObject() {
                StepWrapper sw = model.getObject();
                if (pass == null) {
                    return "btn btn-sm btn-default";
                } else if (pass) {
                    return "btn btn-sm btn-success";
                } else {
                    return "btn btn-sm btn-danger";
                }
            }
        });
        runButton = new IndicatingAjaxLink("run") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                Component varsPanel = getPage().get(BasePage.LEFT_NAV_ID);
                target.add(varsPanel);
                KarateSession session = service.getSession(model.getSessionId());
                StepWrapper step = model.getObject();
                KarateBackend backend = session.getBackend();
                StepResult result = step.run(backend, null);
                pass = result.isPass();
                target.add(this);
            }
        };
        runButton.add(btnClass).setOutputMarkupId(true);
        form.add(runButton);
    }

}
