package com.intuit.karate.junit4;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intuit.karate.cucumber.CucumberRunner;

import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.formatter.PluginFactory;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.junit.JUnitOptions;
import cucumber.runtime.junit.JUnitReporter;
import cucumber.runtime.model.CucumberFeature;
import net.masterthought.cucumber.Configuration;
import net.masterthought.cucumber.ReportBuilder;
import net.masterthought.cucumber.Reportable;

/**
 * implementation adapted from cucumber.api.junit.Cucumber
 * 
 * @author pthomas3
 */
public class Karate extends ParentRunner<KarateFeatureRunner> {

	private static final Logger logger = LoggerFactory.getLogger(Karate.class);

	private final JUnitReporter reporter;
	private final List<KarateFeatureRunner> children;

	private final String REPORT_OUTPUT_DIR = "target";
	private final String REPORT_FILE = "test-report.json";
	private String reportTitle = "Karate Test Report";
	private boolean buildReport = false;

	public Karate(Class clazz) throws InitializationError, IOException {
		super(clazz);
		CucumberRunner cr = new CucumberRunner(clazz);
		RuntimeOptions ro = cr.getRuntimeOptions();
		List<String> jos = cr.getRuntimeOptions().getJunitOptions();
		List<String> junitOpts = new ArrayList<String>();
		for (String jo : jos) {
			if (jo.startsWith("report:")) {
				buildReport = true;
				reportTitle = jo.split(":")[1];
				String jsonOuputPlugin = "json:" + REPORT_OUTPUT_DIR + File.separator + REPORT_FILE;
				PluginFactory pluginFactory = new PluginFactory();
				Object plugin = pluginFactory.create(jsonOuputPlugin);
				ro.addPlugin(plugin);
			} else
				junitOpts.add(jo);
		}
		List<CucumberFeature> cucumberFeatures = cr.getFeatures();
		ClassLoader cl = cr.getClassLoader();
		JUnitOptions junitOptions = new JUnitOptions(junitOpts);
		reporter = new JUnitReporter(ro.reporter(cl), ro.formatter(cl), ro.isStrict(), junitOptions);
		children = new ArrayList<>(cucumberFeatures.size());
		for (CucumberFeature feature : cucumberFeatures) {
			Runtime runtime = cr.getRuntime(feature);
			FeatureRunner runner = new FeatureRunner(feature, runtime, reporter);
			children.add(new KarateFeatureRunner(runner, runtime));
		}
	}

	@Override
	public List<KarateFeatureRunner> getChildren() {
		return children;
	}

	@Override
	protected Description describeChild(KarateFeatureRunner child) {
		return child.runner.getDescription();
	}

	@Override
	protected void runChild(KarateFeatureRunner child, RunNotifier notifier) {
		child.runner.run(notifier);
		child.runtime.printSummary();
	}

	@Override
	public void run(RunNotifier notifier) {
		super.run(notifier);
		reporter.done();
		if (buildReport)
			generateReport();
		reporter.close();
	}

	private void generateReport() {
		File reportDir = new File(REPORT_OUTPUT_DIR);
		List<String> reportFiles = new ArrayList<>();
		reportFiles.add(REPORT_OUTPUT_DIR + File.separator + REPORT_FILE);
		Configuration reportConfig = new Configuration(reportDir, reportTitle);
		ReportBuilder reportBuilder = new ReportBuilder(reportFiles, reportConfig);
		Reportable report = reportBuilder.generateReports();
		if (report != null) {
			String logMsg = "\n\n-----------------------------------------------------------------------\n";
			logMsg += " Generated html report at the location: {}\n";
			logMsg += "-----------------------------------------------------------------------\n";
			logger.info(logMsg, reportDir.getAbsolutePath());
		}
	}

}
