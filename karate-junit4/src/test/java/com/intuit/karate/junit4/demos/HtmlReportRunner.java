package com.intuit.karate.junit4.demos;

import org.junit.runner.RunWith;

import com.intuit.karate.junit4.Karate;

import cucumber.api.CucumberOptions;

@RunWith(Karate.class)
@CucumberOptions(features = "classpath:com/intuit/karate/junit4/demos/karate-html-report.feature", junit = {
		"report:Karate Html Report" })
public class HtmlReportRunner {

}