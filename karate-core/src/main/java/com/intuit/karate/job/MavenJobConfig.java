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

import com.intuit.karate.StringUtils;
import com.intuit.karate.core.Scenario;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public abstract class MavenJobConfig implements JobConfig {

    private final String host;
    private final int port;
    private final List<String> sysPropKeys = new ArrayList(1);
    private final List<String> envPropKeys = new ArrayList(1);

    public MavenJobConfig(String host, int port) {
        this.host = host;
        this.port = port;
        sysPropKeys.add("karate.env");
    }

    public void addSysPropKey(String key) {
        sysPropKeys.add(key);
    }

    public void addEnvPropKey(String key) {
        envPropKeys.add(key);
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public List<JobCommand> getMainCommands(Scenario scenario) {
        String path = scenario.getFeature().getRelativePath();
        int line = scenario.getLine();
        String temp = "mvn exec:java -Dexec.mainClass=com.intuit.karate.cli.Main -Dexec.classpathScope=test"
                + " -Dexec.args=" + path + ":" + line;
        for (String k : sysPropKeys) {
            String v = StringUtils.trimToEmpty(System.getProperty(k));
            if (!v.isEmpty()) {
                temp = temp + " -D" + k + "=" + v;
            }
        }
        return Collections.singletonList(new JobCommand(temp));
    }

    @Override
    public List<JobCommand> getStartupCommands() {
        return Collections.singletonList(new JobCommand("mvn test-compile"));
    }

    @Override
    public Map<String, String> getEnvironment() {
        Map<String, String> map = new HashMap(envPropKeys.size());
        for (String k : envPropKeys) {
            String v = StringUtils.trimToEmpty(System.getenv(k));
            if (!v.isEmpty()) {
                map.put(k, v);
            }
        }
        return map;
    }

}
