/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Command {

	private static final Logger logger = LoggerFactory.getLogger(Command.class);

	private final File workingDir;
	private final String[] args;
	private Process process;
	private final BoundedStringQueue buffer;
	private int exitCode = -1;
	private long startTime;
	private long endTime;

	public Command(File workingDir, String ... args) {
		this.workingDir = workingDir;
		this.args = args;
		buffer = new BoundedStringQueue(10);
	}
	
	public String getBuffer() {
		return buffer.getBuffer();
	}		

	public int getExitCode() {
		return exitCode;
	}		

	public int getTimeTakenInSeconds() {
		return (int) (endTime - startTime) / 1000;
	}
	
	public int run() {		
		try {
			ProcessBuilder pb = new ProcessBuilder(args);
			Map env = pb.environment();
			env.clear();
			pb.directory(workingDir);
			pb.redirectErrorStream(true);
			startTime = System.currentTimeMillis();
			process = pb.start();
			BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				buffer.add(line);
                logger.debug(line);
			}
			endTime = System.currentTimeMillis();
			logger.debug("last 10 lines of output:\n{}", buffer.getBuffer());
			exitCode = process.waitFor();
			return exitCode;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
