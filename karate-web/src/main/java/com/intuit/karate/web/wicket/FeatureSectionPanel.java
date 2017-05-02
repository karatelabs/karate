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

import com.intuit.karate.JsonUtils;
import com.intuit.karate.cucumber.KarateBackend;
import com.intuit.karate.cucumber.StepResult;
import com.intuit.karate.cucumber.StepWrapper;
import com.intuit.karate.web.wicket.model.FeatureSectionModel;
import com.intuit.karate.web.wicket.model.StepModel;
import com.intuit.karate.web.service.KarateService;
import com.intuit.karate.web.service.KarateSession;
import java.util.ArrayList;
import java.util.List;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 *
 * @author pthomas3
 */
public class FeatureSectionPanel extends Panel {

    @SpringBean(required = true)
    private KarateService service;

    private final List<ListItem<StepWrapper>> listItems = new ArrayList<>();

    public FeatureSectionPanel(String id, FeatureSectionModel model) {
        super(id);
        setDefaultModel(new CompoundPropertyModel(model));
        ListView listView = new ListView<StepWrapper>("scenario.steps") {
            @Override
            protected void populateItem(ListItem<StepWrapper> li) {
                li.setOutputMarkupId(true);
                StepWrapper step = li.getModelObject();
                StepModel stepModel = new StepModel(model.getSessionId(), step);
                li.add(new StepPanel("step", stepModel));
                listItems.add(li);
            }
        };
        add(listView);
        add(new IndicatingAjaxLink("run-all") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                KarateSession session = service.getSession(model.getSessionId());
                KarateBackend backend = session.getBackend();
                FeaturePage featurePage = (FeaturePage) getPage();                
                for (ListItem<StepWrapper> li : listItems) {
                    StepWrapper step = li.getModelObject();
                    StepResult result = step.run(backend);
                    step.setPass(result.isPass());
                    StepPanel stepPanel = (StepPanel) li.get("step");
                    String json = JsonUtils.toJsonString(
                            "{ type: 'step', buttonId: '" + stepPanel.getRunButton().getMarkupId() + "' }");
                    featurePage.pushJsonWebSocketMessage(json);
                }
            }
        });
    }

}
