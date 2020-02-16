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
package com.intuit.karate.job;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class MavenChromeJobConfig extends MavenJobConfig {
    
    private int width = 1280;
    private int height = 720;
    
    public MavenChromeJobConfig(int executorCount, String host, int port) {
        super(executorCount, host, port);
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }        

    @Override
    public String getExecutorCommand(String jobId, String jobUrl, int index) {
        return "docker run --rm --cap-add=SYS_ADMIN -e KARATE_JOBURL=" + jobUrl
                                + " -e KARATE_WIDTH=" + width + " -e KARATE_HEIGHT=" + height
                                + " " + dockerImage;
    }        

    @Override
    public List<JobCommand> getPreCommands(JobContext jc) {
        return Collections.singletonList(new JobCommand("supervisorctl start ffmpeg"));
    }

    @Override
    public List<JobCommand> getPostCommands(JobContext jc) {
        List<JobCommand> list = new ArrayList();
        list.add(new JobCommand("supervisorctl stop ffmpeg"));
        list.add(new JobCommand("mv /tmp/karate.mp4 " + jc.getUploadDir()));
        return list;
    }

}
