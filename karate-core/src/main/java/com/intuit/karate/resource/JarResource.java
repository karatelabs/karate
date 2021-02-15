/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.resource;

import com.intuit.karate.FileUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;

/**
 *
 * @author pthomas3
 */
public class JarResource implements Resource {

    private final byte[] bytes;
    private final String relativePath;
    private final URI uri;

    public JarResource(byte[] bytes, String relativePath, URI uri) {
        this.bytes = bytes;
        this.relativePath = relativePath;
        this.uri = uri;
    }

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public boolean isClassPath() {
        return true;
    }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public URI getUri() {
        return uri;
    }        

    @Override
    public String getRelativePath() {
        return relativePath;
    }      
    
    @Override
    public Resource resolve(String path) {
        int pos = relativePath.lastIndexOf('/');
        String parentPath = pos == -1 ? "" : relativePath.substring(0, pos);
        return ResourceUtils.getResource(FileUtils.WORKING_DIR, "classpath:" + parentPath + "/" + path);
    }    

    @Override
    public InputStream getStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public boolean equals(Object obj) {
        return relativePath.equals(obj);
    }        

    @Override
    public int hashCode() {
        return relativePath.hashCode();
    }        
    
    @Override
    public String toString() {
        return getPrefixedPath();
    }    

}
