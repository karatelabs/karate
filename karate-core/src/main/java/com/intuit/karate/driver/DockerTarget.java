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
package com.intuit.karate.driver;

import com.intuit.karate.shell.Command;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class DockerTarget implements Target {

    private String name;
    private Function<Integer, String> command;
    private Map<String, Object> options;
    
    public DockerTarget() {
        this(null);
    }
    
    public DockerTarget(Map<String, Object> options) {
        this.options = options;
    }

    public void setCommand(Function<Integer, String> command) {
        this.command = command;
    }

    public Function<Integer, String> getCommand() {
        return command;
    }        

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public Map<String, Object> getOptions() {
        return options;
    }        

    @Override
    public Map<String, Object> start() {
        if (command == null) {
            throw new RuntimeException("docker target command (function) not set");
        }
        int port = Command.getFreePort();
        name = Command.execLine(null, command.apply(port));
        Map<String, Object> map = new HashMap();
        map.put("port", port);
        if (options != null) {
            map.putAll(options);
        }
        Command.waitForPort("127.0.0.1", port);
        return map;
    }

    @Override
    public void stop() {
        Command.execLine(null, "docker stop " + name);
    }

}
