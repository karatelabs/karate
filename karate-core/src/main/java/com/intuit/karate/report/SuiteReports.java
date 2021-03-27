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

import com.intuit.karate.Results;
import com.intuit.karate.Suite;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.TagResults;
import com.intuit.karate.core.TimelineResults;

/**
 *
 * @author pthomas3
 */
public interface SuiteReports {

    default Report featureReport(Suite suite, FeatureResult featureResult) {
        return Report.template("karate-feature.html")
                .reportDir(suite.reportDir)
                .reportFileName(featureResult.getFeature().getPackageQualifiedName() + ".html")
                .variable("results", featureResult.toKarateJson())
                .build();
    }

    default Report tagsReport(Suite suite, TagResults tagResults) {
        return Report.template("karate-tags.html")
                .reportDir(suite.reportDir)
                .variable("results", tagResults.toKarateJson())
                .build();
    }

    default Report timelineReport(Suite suite, TimelineResults timelineResults) {
        return Report.template("karate-timeline.html")
                .reportDir(suite.reportDir)
                .variable("results", timelineResults.toKarateJson())
                .build();
    }

    default Report summaryReport(Suite suite, Results results) {
        return Report.template("karate-summary.html")
                .reportDir(suite.reportDir)
                .variable("results", results.toKarateJson())
                .build();
    }

    public static final SuiteReports DEFAULT = new SuiteReports() {
        // defaults
    };

}
