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
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Collectors;
import org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

/**
 *
 * @author pthomas3
 */
public class FeatureFileTreeProvider implements ITreeProvider<FeatureFileEnv> {
    
    private final File root;
    private final String[] searchPaths;
    
    public FeatureFileTreeProvider(File root, String ... searchPaths) {
        this.root = root;
        this.searchPaths = searchPaths;
    }

    @Override
    public Iterator<? extends FeatureFileEnv> getRoots() {
        FeatureFileEnv ffe = new FeatureFileEnv(root, searchPaths);
        return Collections.singletonList(ffe).iterator();
    }

    @Override
    public boolean hasChildren(FeatureFileEnv node) {
        return node.getFile().isDirectory();
    }   

    @Override
    public Iterator<? extends FeatureFileEnv> getChildren(FeatureFileEnv node) {
        File[] files = node.getFile().listFiles();
        return Arrays.asList(files).stream()
                .filter(f -> !f.isHidden() && (f.isDirectory() || f.getName().toLowerCase().endsWith(".feature")))
                .map(f -> new FeatureFileEnv(f, searchPaths)).collect(Collectors.toList()).iterator();    
    }

    @Override
    public IModel<FeatureFileEnv> model(FeatureFileEnv object) {
        return Model.of(object);
    }

    @Override
    public void detach() {
        
    }

}
