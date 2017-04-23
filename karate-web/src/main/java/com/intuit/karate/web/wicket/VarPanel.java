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

import com.intuit.karate.web.wicket.model.VarModel;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;

public class VarPanel extends Panel {         

	public VarPanel(String id, VarModel model, VarsRefreshingView varsView) {
		super(id);
        IModel<String> txtModel = new IModel<String>() {
            @Override
            public String getObject() {
                return model.getObject().getValue().getAsString();
            }            
            @Override
            public void setObject(String object) {
               
            }
            @Override
            public void detach() {}
        };
        add(new TextArea("value", txtModel));
        String title = model.getObject().getName() + " - " + model.getObject().getValue().getType();
        add(new Label("title", title));
        add(new AjaxLink("close") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                varsView.setSelectedName(null);
                Component headerPanel = VarPanel.this;
                headerPanel = headerPanel.replaceWith(new Label(BasePage.STICKY_HEADER_ID, ""));
                target.add(headerPanel);
                Component varsPanel = varsView.getParent();
                target.add(varsPanel);                        
            }
        });
	}

}
