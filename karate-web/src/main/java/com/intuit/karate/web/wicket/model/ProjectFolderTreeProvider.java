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
package com.intuit.karate.web.wicket.model;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Collectors;
import org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

/**
 *
 * @author pthomas3
 */
public class ProjectFolderTreeProvider implements ITreeProvider<ProjectFolderTreeNode> {
    
    private File root;
    
    public ProjectFolderTreeProvider(File root) {
        this.root = root;
    }

    public File getRoot() {
        return root;
    }        

    public void setRoot(File root) {
        this.root = root;
    }        

    @Override
    public Iterator<? extends ProjectFolderTreeNode> getRoots() {
        return Arrays.asList(root.getParentFile().listFiles()).stream()
                .filter(f -> f.isDirectory())
                .map(ProjectFolderTreeNode::new)
                .collect(Collectors.toList()).iterator();
    }

    @Override
    public boolean hasChildren(ProjectFolderTreeNode node) {        
        return node.getFile().isDirectory();
    }

    @Override
    public Iterator<? extends ProjectFolderTreeNode> getChildren(ProjectFolderTreeNode node) {
        return Arrays.asList(node.getFile().listFiles()).stream()
                .filter(f -> f.isDirectory())
                .map(ProjectFolderTreeNode::new).collect(Collectors.toList()).iterator();
    }

    @Override
    public IModel<ProjectFolderTreeNode> model(ProjectFolderTreeNode object) {
        return Model.of(object);
    }

    @Override
    public void detach() {

    }
    
}
