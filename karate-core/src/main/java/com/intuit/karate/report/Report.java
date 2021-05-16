/*
 * The MIT License
 *
 * Copyright 2021 Intuit Inc.
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
package com.intuit.karate.report;

import com.intuit.karate.FileUtils;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.template.KarateTemplateEngine;
import com.intuit.karate.template.TemplateUtils;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public interface Report {

    JsEngine getJsEngine();

    String getResourceRoot();

    String getTemplate();

    String getReportDir();

    String getReportFileName();
    
    default File render() {
        return render(null);
    }

    default File render(String reportDir) {
        JsEngine je = getJsEngine();
        KarateTemplateEngine engine = TemplateUtils.forResourceRoot(je, getResourceRoot());
        String html = engine.process(getTemplate());
        if (reportDir == null) {
            reportDir = getReportDir();
        }
        ReportUtils.initStaticResources(reportDir);
        File file = new File(reportDir + File.separator + getReportFileName());
        FileUtils.writeToFile(file, html);
        return file;
    }

    public static class Builder {

        private JsEngine je;
        private String resourceRoot = "classpath:com/intuit/karate/report";
        private String template;
        private String reportDir;
        private String reportFileName;
        private final Map<String, Object> variables = new HashMap();

        public Builder resourceRoot(String value) {
            resourceRoot = value;
            return this;
        }

        public Builder template(String value) {
            template = value;
            return this;
        }

        public Builder jsEngine(JsEngine value) {
            je = value;
            return this;
        }

        public Builder variable(String name, Object value) {
            variables.put(name, value);
            return this;
        }

        public Builder variables(Map<String, Object> value) {
            variables.putAll(value);
            return this;
        }

        public Builder reportDir(String value) {
            reportDir = value;
            return this;
        }
        
        public Builder reportFileName(String value) {
            reportFileName = value;
            return this;
        }

        public Report build() {
            if (template == null) {
                throw new RuntimeException("template name is mandatory");
            }
            if (reportDir == null) {
                throw new RuntimeException("report dir is mandatory");
            }
            if (reportFileName == null) {
                reportFileName = template;
            }
            if (je == null) {
                je = JsEngine.local();
            }
            je.putAll(variables);
            return new Report() {

                @Override
                public JsEngine getJsEngine() {
                    return je;
                }

                @Override
                public String getResourceRoot() {
                    return resourceRoot;
                }

                @Override
                public String getTemplate() {
                    return template;
                }

                @Override
                public String getReportDir() {
                    return reportDir;
                }

                @Override
                public String getReportFileName() {
                    return reportFileName;
                }

            };
        }

    }

    public static Builder template(String value) {
        return new Builder().template(value);
    }

}
