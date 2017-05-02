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

import com.intuit.karate.web.wicket.model.FeatureFileTreeProvider;
import com.intuit.karate.web.wicket.model.FeatureFileEnv;
import com.intuit.karate.web.service.KarateService;
import com.intuit.karate.web.service.KarateSession;
import com.intuit.karate.web.wicket.model.ProjectFolderTreeNode;
import com.intuit.karate.web.wicket.model.ProjectFolderTreeProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.extensions.markup.html.repeater.tree.DefaultNestedTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.content.Folder;
import org.apache.wicket.extensions.markup.html.repeater.tree.theme.WindowsTheme;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;

public class FeatureBrowsePanel extends Panel {

    @SpringBean(required = true)
    private KarateService service;

    private Behavior theme = new WindowsTheme();
    
    private final DefaultNestedTree<FeatureFileEnv> featureTree;

    public FeatureBrowsePanel(String id, PageParameters params) {
        super(id);
        File root = new File(".").getAbsoluteFile();
        ProjectFolderTreeProvider projectTreeProvider = new ProjectFolderTreeProvider(root);        
        DefaultNestedTree<ProjectFolderTreeNode> projectTree = new DefaultNestedTree<ProjectFolderTreeNode>("projects", projectTreeProvider) {
            @Override
            protected Component newContentComponent(String id, IModel<ProjectFolderTreeNode> node) {
                return new Folder<ProjectFolderTreeNode>(id, this, node) {
                    @Override
                    protected void onClick(AjaxRequestTarget target) {
                        File newRoot = node.getObject().getFile();
                        String basePath = newRoot.getPath() + File.separator;
                        FeatureFileTreeProvider featureTreeProvider = (FeatureFileTreeProvider) featureTree.getProvider();
                        featureTreeProvider.setRoot(newRoot);
                        featureTreeProvider.setSearchPaths(basePath + "src/test/java");
                        featureTree.modelChanged();
                        target.add(featureTree);
                    }                    
                };
            }            
        };
        projectTree.setOutputMarkupId(true);
        add(projectTree);
        projectTree.add(new Behavior() {
            @Override
            public void onComponentTag(Component component, ComponentTag tag) {
                theme.onComponentTag(component, tag);
            }

            @Override
            public void renderHead(Component component, IHeaderResponse response) {
                theme.renderHead(component, response);
            }
        });
        add(new AjaxLink("parent") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                File parent = projectTreeProvider.getRoot().getParentFile();
                projectTreeProvider.setRoot(parent);
                projectTree.modelChanged();
                target.add(projectTree);
            }
        });        
        
        String basePath = root.getPath() + File.separator;
        FeatureFileTreeProvider featureTreeProvider = new FeatureFileTreeProvider(root, basePath + "src/test/java");
        featureTree = new DefaultNestedTree<FeatureFileEnv>("features", featureTreeProvider) {
            @Override
            protected Component newContentComponent(String id, IModel<FeatureFileEnv> node) {
                String name = node.getObject().getFile().getName();
                if (name.toLowerCase().endsWith(".feature")) {
                    return new Folder<FeatureFileEnv>(id, this, node) {
                        @Override
                        protected boolean isClickable() {
                            return true;
                        }

                        @Override
                        protected void onClick(AjaxRequestTarget target) {
                            FeatureFileEnv ffe = node.getObject();
                            KarateSession session = service.createSession("dev", ffe.getFile(), ffe.getSearchPaths());
                            setResponsePage(new FeaturePage(session.getId()));
                        }
                    };
                } else {
                    return super.newContentComponent(id, node);
                }
            }
        };
        featureTree.setOutputMarkupId(true);
        add(featureTree);
        featureTree.add(new Behavior() {
            @Override
            public void onComponentTag(Component component, ComponentTag tag) {
                theme.onComponentTag(component, tag);
            }

            @Override
            public void renderHead(Component component, IHeaderResponse response) {
                theme.renderHead(component, response);
            }
        });
    }

}
