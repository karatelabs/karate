/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.gatling;

import com.intuit.karate.StringUtils;
import com.intuit.karate.job.JobCommand;
import com.intuit.karate.job.JobContext;
import com.intuit.karate.job.MavenJobConfig;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class GatlingMavenJobConfig extends MavenJobConfig {
    
    private String mainCommand = "mvn gatling:test";
    
    public GatlingMavenJobConfig(int executorCount, String host, int port) {
        super(executorCount, host, port);
    }    

    public void setMainCommand(String mainCommand) {
        this.mainCommand = mainCommand;
    }        

    @Override
    public List<JobCommand> getMainCommands(JobContext chunk) {
        String temp = mainCommand;
        for (String k : sysPropKeys) {
            String v = StringUtils.trimToEmpty(System.getProperty(k));
            if (!v.isEmpty()) {
                temp = temp + " -D" + k + "=" + v;
            }
        }
        return Collections.singletonList(new JobCommand(temp));        
    }        
    
}
