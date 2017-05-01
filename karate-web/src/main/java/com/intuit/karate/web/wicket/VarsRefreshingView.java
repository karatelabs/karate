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

import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.web.wicket.model.ScriptContextModel;
import com.intuit.karate.web.wicket.model.Var;
import com.intuit.karate.web.wicket.model.VarModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.RefreshingView;
import org.apache.wicket.markup.repeater.util.ModelIteratorAdapter;
import org.apache.wicket.model.IModel;

/**
 *
 * @author pthomas3
 */
public class VarsRefreshingView extends RefreshingView<Var> {
    
    private final ScriptContextModel model;
    private String selectedName;

    public void setSelectedName(String selectedName) {
        this.selectedName = selectedName;
    }        
    
    public VarsRefreshingView(String id, ScriptContextModel model) {
        super(id);
        this.model = model;
    }

    @Override
    protected Iterator<IModel<Var>> getItemModels() {
        model.detach(); // force reload
        ScriptContext context = model.getObject();
        if (context == null) {
            return Collections.emptyIterator();
        }
        ScriptValueMap map = model.getObject().getVars();
        List<Var> list = new ArrayList<>(map.size());
        for (String name : map.keySet()) {
            list.add(new Var(name, map.get(name)));
        }
        return new ModelIteratorAdapter<Var>(list) {
            @Override
            protected IModel<Var> model(Var var) {
                return new VarModel(model.getSessionId(), var.getName());
            }            
        };
    }

    @Override
    protected void populateItem(Item<Var> item) {
        String name = item.getModel().getObject().getName();
        item.add(new AjaxLink("show") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                selectedName = name;
                Component headerPanel = getPage().get(BasePage.STICKY_HEADER_ID);
                VarModel varModel = new VarModel(model.getSessionId(), name);
                VarPanel varPanel = new VarPanel(BasePage.STICKY_HEADER_ID, varModel, VarsRefreshingView.this);                
                headerPanel = headerPanel.replaceWith(varPanel);
                target.add(headerPanel);
                Component varsPanel = getPage().get(BasePage.LEFT_NAV_ID);
                target.add(varsPanel);
            }
        }.add(new Label("name", name)));
        String type = item.getModel().getObject().getValue().getTypeAsShortString();
        item.add(new Label("type", type));
        if (name.equals(selectedName)) {
            item.add(new AttributeModifier("class", "success"));
        }
    }
    
}
