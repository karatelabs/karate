# Karate
## Web-Services Testing Made `Simple.`
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.intuit.karate/karate-core/badge.svg)](https://mvnrepository.com/artifact/com.intuit.karate/karate-core) [![Build Status](https://travis-ci.org/intuit/karate.svg?branch=master)](https://travis-ci.org/intuit/karate) [![GitHub release](https://img.shields.io/github/release/intuit/karate.svg)](https://github.com/intuit/karate/releases) [![Support Slack](https://img.shields.io/badge/support-slack-red.svg)](https://karate-dsl.slack.com/shared_invite/MTU5Nzk3NzEyMTYyLTE0OTA0OTcyMzktNDkyOTg0MmMyYQ) [![Twitter Follow](https://img.shields.io/twitter/follow/KarateDSL.svg?style=social&label=Follow)](https://twitter.com/KarateDSL)

Karate enables you to script a sequence of calls to any kind of web-service and assert that the responses are as expected.  It makes it really easy to build complex request payloads, traverse data within the responses, and chain data from responses into the next request. Karate's payload validation engine can perform a 'smart compare' of two JSON or XML documents without being affected by white-space or the order in which data-elements actually appear, and you can opt to ignore fields that you choose.

Since Karate is built on top of [Cucumber-JVM](https://github.com/cucumber/cucumber-jvm), you can run tests and generate reports like any standard Java project. But instead of Java - you write tests in a language designed to make dealing with HTTP, JSON or XML - **simple**.

## Hello World
```cucumber
Feature: karate 'hello world' example
Scenario: create and retrieve a cat

Given url 'http://myhost.com/v1/cats'
And request { name: 'Billie' }
When method post
Then status 201
And match response == { id: '#notnull', name: 'Billie' }

Given path response.id
When method get
Then status 200
```
It is worth pointing out that JSON is a 'first class citizen' of the syntax such that you can express payload and expected data without having to use double-quotes and without having to enclose JSON field names in quotes.  There is no need to 'escape' characters like you would have had to in Java or other programming languages.

And you don't need to create Java objects (or POJO-s) for any of the payloads that you need to work with.

# Index 

:white_small_square: | :white_small_square: | :white_small_square: | :white_small_square: | :white_small_square:  
----- | ---- | ---- | --- | ---
**Getting Started** | [Maven / Quickstart](#maven) | [Folder Structure](#folder-structure) | [Naming Conventions](#naming-conventions) | [JUnit](#running-with-junit) / [TestNG](#running-with-testng)
.... | [Cucumber Options](#cucumber-options) | [Command Line](#command-line) | [Logging](#logging) | [Configuration](#configuration)
.... | [Environment Switching](#switching-the-environment) | [Test Reports](#test-reports) | [Parallel Execution](#parallel-execution) | [Script Structure](#script-structure)
**Variables & Expressions** | [`def`](#def) | [`assert`](#assert) / [`print`](#print) | [`table`](#table) | [`text`](#text) / [`yaml`](#yaml)
**Data Types** | [JSON](#json) / [XML](#xml) | [JavaScript Functions](#javascript-functions) | [Reading Files](#reading-files) | [Type / String Conversion](#type-conversion)
**Primary HTTP Keywords** | [`url`](#url) | [`path`](#path) | [`request`](#request) | [`method`](#method) 
.... | [`status`](#status) | [`soap action`](#soap) | [`configure`](#configure)
**Secondary HTTP Keywords** | [`param`](#param) / [`params`](#params) | [`header`](#header) / [`headers`](#headers) | [`cookie`](#cookie) / [`cookies`](#cookies) | [`form field`](#form-field) / [`form fields`](#form-fields)
.... | [`multipart field`](#multipart-field) | [`multipart entity`](#multipart-entity)
**Get, Set, Remove, Match** | [`get`](#get) / [`set`](#set) / [`remove`](#remove) | [`match ==`](#match) | [`contains`](#match-contains) / [`only`](#match-contains-only) / [`!contains`](#not-contains) | [`match each`](#match-each)
**Special Variables** | [`response`](#response) | [`responseHeaders`](#responseheaders) | [`responseCookies`](#responsecookies) | [`responseStatus`](#responsestatus) / [`responseTime`](#responsetime)
 **Code Re-Use** | [`call`](#call) / [`callonce`](#callonce)| [Calling `*.feature` files](#calling-other-feature-files) | [Calling JS Functions](#calling-javascript-functions) | [Calling Java](#calling-java)
 **Misc / Examples** | [Embedded Expressions](#embedded-expressions) | [GraphQL RegEx Example](#graphql--regex-replacement-example) | [XML and XPath](#xpath-functions) | [Cucumber Tags](#cucumber-tags)
.... | [Data Driven Tests](#data-driven-tests) | [Auth](#calling-other-feature-files) / [Headers](#http-basic-authentication-example) | [Ignore / Validate](#ignore-or-validate) | [Examples and Demos](karate-demo)
.... | [Java API](#java-api) | [Schema Validation](#schema-validation) | [Karate vs REST-assured](#comparison-with-rest-assured) | [Cucumber vs Karate](#cucumber-vs-karate)

# Features
* Java knowledge is not required and even non-programmers can write tests.
* Scripts are plain-text files and require no compilation step or IDE
* Based on the popular Cucumber / Gherkin standard, and [IDE support](#running-in-eclipse-or-intellij) and syntax-coloring options exist
* Syntax 'natively' supports JSON and XML - including [JsonPath](#set) and [XPath](#xpath-functions) expressions
* Eliminate the need for 'POJO's or 'helper code' to represent payloads and HTTP end-points, and [dramatically reduce the lines of code](https://twitter.com/KarateDSL/status/873035687817117696) needed for a test
* Tests are super-readable - as scenario data can be expressed in-line, in human-friendly [JSON](#json), [XML](#xml) or Cucumber [Scenario Outline](#the-cucumber-way) tables
* Express expected results as readable, well-formed JSON or XML, and [assert in a single step](#match) that the entire response payload (no matter how complex or deeply nested) - is as expected
* Payload assertion failures clearly report which data element (and path) is not as expected, for easy troubleshooting of even large payloads
* Simpler and more [powerful alternative](https://twitter.com/KarateDSL/status/878984854012022784) to JSON-schema for [validating payload structure](#schema-validation) and data-formats that even supports cross-field / domain validation logic
* Scripts can [call other scripts](#calling-other-feature-files) - which means that you can easily re-use and maintain authentication and 'set up' flows efficiently, across multiple tests
* Embedded JavaScript engine that allows you to build a library of [re-usable functions](#calling-javascript-functions) that suit your specific environment or organization
* Re-use of payload-data and user-defined functions across tests is [so easy](#reading-files) - that it becomes a natural habit for the test-developer
* Built-in support for [switching configuration](#switching-the-environment) across different environments (e.g. dev, QA, pre-prod)
* Support for [data-driven tests](#data-driven-tests) and being able to [tag or group](#cucumber-tags) tests is built-in, no need to rely on TestNG or JUnit
* Standard Java / Maven project structure, and [seamless integration](#command-line) into CI / CD pipelines - with both JUnit and TestNG being supported
* Support for multi-threaded [parallel execution](#parallel-execution), which is a huge time-saver, especially for HTTP integration tests
* Built-in [test-reports](#test-reports) powered by Cucumber-JVM with the option of using third-party (open-source) maven-plugins for even [better-looking reports](karate-demo#example-report)
* Easily invoke JDK classes, Java libraries, or re-use custom Java code if needed, for [ultimate extensibility](#calling-java)
* Simple plug-in system for [authentication](#http-basic-authentication-example) and HTTP [header management](#configure-headers) that will handle any complex, real-world scenario
* Future-proof 'pluggable' HTTP client abstraction supports both Apache and Jersey so that you can [choose](#maven) what works best in your project, and not be blocked by library or dependency conflicts
* Comprehensive support for different flavors of HTTP calls:
  * [SOAP](#soap-action) / XML requests
  * HTTPS / [SSL](#configure) - without needing certificates, key-stores or trust-stores
  * HTTP [proxy server](#configure) support
  * URL-encoded [HTML-form](#form-field) data
  * [Multi-part](#multipart-field) file-upload - including 'multipart/mixed' and 'multipart/related'
  * Browser-like [cookie](#cookie) handling
  * Full control over HTTP [headers](#header), [path](#path) and query [parameters](#param)
  * Intelligent defaults

## Real World Examples

A set of real-life examples can be found here: [Karate Demos](karate-demo)

## Comparison with REST-assured
For teams familiar with or currently using [REST-assured](http://rest-assured.io), this detailed comparison can help you evaluate Karate: [Karate vs REST-assured](http://tinyurl.com/karatera)

## References
* [Karate a Rest Test Tool – Basic API Testing](https://www.joecolantonio.com/2017/03/23/rest-test-tool-karate-api-testing/) - blog post and video tutorial by [Joe Colantonio](https://twitter.com/jcolantonio)
* [Writing BDD-Style Webservice Tests with Karate and Java](http://www.hascode.com/2017/04/behavior-driven-development-writing-webservice-tests-with-java-and-karate/) - blog post and step-by-step tutorial by [Micha Kops](https://twitter.com/hascode)
* [10 API testing tools to try in 2017](https://assertible.com/blog/10-api-testing-tools-to-try-in-2017) - blog post by [Christopher Reichert](https://twitter.com/creichert07) of [Assertible](https://twitter.com/AssertibleApp)
* [Karate for Complex Web-Service API Testing](https://www.slideshare.net/intuit_india/karate-for-complex-webservice-api-testing-by-peter-thomas) - slide-deck by [Peter Thomas](https://twitter.com/ptrthomas)

# Getting Started
Karate requires [Java](http://www.oracle.com/technetwork/java/javase/downloads/index.html) 8 and [Maven](http://maven.apache.org) to be installed.

## Maven

Karate is designed so that you can choose between the [Apache](https://hc.apache.org/index.html) or [Jersey](https://jersey.java.net) HTTP client implementations.

So you need two `<dependencies>`:

```xml
<dependency>
    <groupId>com.intuit.karate</groupId>
    <artifactId>karate-apache</artifactId>
    <version>0.5.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.intuit.karate</groupId>
    <artifactId>karate-junit4</artifactId>
    <version>0.5.0</version>
    <scope>test</scope>
</dependency>
```
And if you run into class-loading conflicts, for example if an older version of the Apache libraries are being used within your project - then use `karate-jersey` instead of `karate-apache`.

### TestNG instead of JUnit
If you want to use [TestNG](http://testng.org), use the artifactId [`karate-testng`](https://mvnrepository.com/artifact/com.intuit.karate/karate-testng). If you are starting a project from scratch, we strongly recommend that you use JUnit. Do note that [dynamic tables](#data-driven-features), [data-driven](#data-driven-tests) testing and [tag-groups](#cucumber-tags) are built-in to Karate, so that you don't need to depend on things like the TestNG [`@DataProvider`](http://testng.org/doc/documentation-main.html#parameters-dataproviders) anymore.

Use the TestNG test-runner only when you are trying to add Karate tests side-by-side with an existing set of TestNG test-classes, possibly as a migration strategy.

### Quickstart
It may be easier for you to use the Karate Maven archetype to create a skeleton project with one command. You can then skip the next few sections, as the `pom.xml`, recommended directory structure and starter files would be created for you.

You can replace the values of 'com.mycompany' and 'myproject' as per your needs.

```
mvn archetype:generate \
-DarchetypeGroupId=com.intuit.karate \
-DarchetypeArtifactId=karate-archetype \
-DarchetypeVersion=0.5.0 \
-DgroupId=com.mycompany \
-DartifactId=myproject
```

This will create a folder called 'myproject' (or whatever you set the name to).

You can refer to this [nice blog post and video](https://www.joecolantonio.com/2017/03/23/rest-test-tool-karate-api-testing/) by Joe Colantonio which provides step by step instructions on how to get started using Eclipse. Also make sure you install the [Cucumber-Eclipse plugin](https://cucumber.io/cucumber-eclipse/) !

Another blog post which is a good step-by-step reference is [this one by Micha Kops](http://www.hascode.com/2017/04/behavior-driven-development-writing-webservice-tests-with-java-and-karate/) - especially if you use the 'default' maven folder structure instead of the one recommended below.

## Folder Structure
A Karate test script has the file extension `.feature` which is the standard followed by Cucumber.  You are free to organize your files using regular Java package conventions.

The Maven tradition is to have non-Java source files in a separate `src/test/resources` folder structure - but we recommend that you keep them side-by-side with your `*.java` files. When you have a large and complex project, you will end up with a few data files (e.g. `*.js`, `*.json`, `*.txt`) as well and it is much more convenient to see the `*.java` and `*.feature` files and all related artifacts in the same place.

This can be easily achieved with the following tweak to your maven `<build>` section.
```xml
<build>
    <testResources>
        <testResource>
            <directory>src/test/java</directory>
            <excludes>
                <exclude>**/*.java</exclude>
            </excludes>
        </testResource>
    </testResources>        
    <plugins>
    ...
    </plugins>
</build>
```

This is very common in the world of Maven users and keep in mind that these are tests and not production code.  

With the above in place, you don't have to keep switching between your `src/test/java` and `src/test/resources` folders, you can have all your test-code and artifacts under 
`src/test/java` and everything will work as expected.

Once you get used to this, you may even start wondering why projects need a `src/test/resources` folder at all !

## Naming Conventions

Since these are tests and not production Java code, you don't need to be bound by the `com.mycompany.foo.bar` convention and the un-necessary explosion of sub-folders that ensues. 
We suggest that you have a folder hierarchy only one or two levels deep - where the folder names clearly identify which 'resource', 'entity' or API is the web-service under test.

For example:
```
src/test/java
    |
    +-- karate-config.js
    +-- logback-test.xml
    +-- some-reusable.feature
    +-- some-classpath-function.js
    +-- some-classpath-payload.json
    |
    \-- animals
        |
        +-- AnimalsTest.java
        |
        +-- cats
        |   |
        |   +-- cats-post.feature
        |   +-- cats-get.feature
        |   +-- cat.json
        |   \-- CatsRunner.java
        |
        \-- dogs
            |
            +-- dog-crud.feature
            +-- dog.json
            +-- some-helper-function.js
            \-- DogsRunner.java
```
Assuming you use JUnit, there are some good reasons for the recommended (best practice) naming convention and choice of file-placement shown above:
* Not using the `*Test.java` convention for the JUnit classes (e.g. `CatsRunner.java`) in the `cats` and `dogs` folder ensures that these tests will **not** be picked up when invoking `mvn test` (for the whole project) from the [command line](#command-line). But you can still invoke these tests from the IDE, which is convenient when in development mode.
* `AnimalsTest.java` (the only file that follows the `*Test.java` naming convention) acts as the 'test suite' for the entire project. By default, Karate will load all `*.feature` files from sub-directories as well. But since `some-reusable.feature` is _above_ `AnimalsTest.java` in the folder hierarchy, it will **not** be picked-up. Which is exactly what we want, because `some-reusable.feature` is designed to be [called](#calling-other-feature-files) only from one of the other test scripts (perhaps with some parameters being passed). You can also use [tags](#cucumber-tags) to skip files.
* `some-classpath-function.js` and `some-classpath-payload.js` are in the 'root' of the Java 'classpath' which means they can be easily [read](#reading-files) (and re-used) from any test-script by using the `classpath:` prefix, for e.g: `read('classpath:some-classpath-function.js')`. Relative paths will also work.

For details on what actually goes into a script or `*.feature` file, refer to the
[syntax guide](#syntax-guide).

## Running in Eclipse or IntelliJ
If you use the open-source [Eclipse Java IDE](http://www.eclipse.org), you should consider installing the free [Cucumber-Eclipse plugin](https://cucumber.io/cucumber-eclipse/). It provides syntax coloring, and the best part is that you can 'right-click' and run Karate test scripts without needing to write a single line of Java code.

If you use [IntelliJ](https://www.jetbrains.com/idea/), Cucumber support is [built-in](https://www.jetbrains.com/idea/help/cucumber.html) and you can even select a single [`Scenario`](#script-structure) within a `Feature` to run at a time.

## Running With JUnit
To run a script `*.feature` file from your Java IDE, you just need the following empty test-class in the same package. The name of the class doesn't matter, and it will automatically run any `*.feature` file in the same package. This comes in useful because depending on how you organize your files and folders - you can have multiple feature files executed by a single JUnit test-class.

```java
package animals.cats;

import com.intuit.karate.junit4.Karate;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
public class CatsRunner {
	
}
```
Refer to your IDE documentation for how to run a JUnit class.  Typically right-clicking on the file in the
project browser or even within the editor view would bring up the "Run as JUnit Test" menu option.

> Karate will traverse sub-directories and look for `*.feature` files. For example if you have the JUnit class in the `com.mycompany` package, `*.feature` files in `com.mycompany.foo` and `com.mycompany.bar` will also be run. This is one reason why you may want to prefer a 'flat' directory structure as [explained above](#naming-conventions).

## Running With TestNG
You extend a class from the [`karate-testng`](#maven) Maven artifact like so. All other behavior
is the same as if using JUnit.

```java
package animals.cats;

import com.intuit.karate.testng.KarateRunner;

public class CatsRunner extends KarateRunner {
    
}
```

## Cucumber Options
You normally don't need to - but if you want to run only a specific feature file from a JUnit test even if there are multiple `*.feature` files in the same folder (or sub-folders), you could use the [`@CucumberOptions`](https://cucumber.io/docs/reference/jvm#configuration) annotation.

```java
package animals.cats;

import com.intuit.karate.junit4.Karate;
import cucumber.api.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@CucumberOptions(features = "classpath:animals/cats/cats-post.feature")
public class CatsPostRunner {
	
}
```
The `features` parameter in the annotation can take an array, so if you wanted to associate
multiple feature files with a JUnit test, you could do this:
```java
@CucumberOptions(features = {
    "classpath:animals/cats/cats-post.feature",
    "classpath:animals/cats/cats-get.feature"})
```

> For TestNG: The `@CucumberOptions` annotation can be used the same way.

## Command Line
Once you have a JUnit 'runner' class in place, it will be possible to run tests from the command-line as well.  Refer to the [Cucumber documentation](https://cucumber.io/docs/reference/jvm) for more, including how to enable other report output formats such as HTML. For example, if you wanted to generate a report in the Cucumber HTML format:

```
mvn test -Dcucumber.options="--plugin html:target/cucumber-html"
```

Note that the `mvn test` command only runs test classes that follow the `*Test.java` [naming convention](#naming-conventions) by default.

A problem you may run into is that the report is generated for every JUnit class with the `@RunWith(Karate.class)` annotation. So if you have multiple JUnit classes involved in a test-run, you will end up with only the report for the last class as it would have over-written everything else. There are a couple of solutions, one is to use [JUnit suites](https://github.com/junit-team/junit4/wiki/Aggregating-tests-in-suites) - but the simplest should be to have a JUnit class (with the Karate annotation) at a level 'above' (in terms of folder hierarchy) all the main `*.feature` files in your project. So if you take the previous [folder structure example](#naming-conventions):

```
mvn test -Dcucumber.options="--plugin junit:target/cucumber-junit.xml --tags ~@ignore" -Dtest=AnimalsTest
```
Here, `AnimalsTest` is the name of the Java class we designated to run all your tests. And yes, Cucumber has a neat way to [tag your tests](#cucumber-tags) and the above example demonstrates how to run all tests _except_ the ones tagged `@ignore`.

The reporting and tag options can be specified in the test-class via the `@CucumberOptions` annotation, in which case you don't need to pass the `-Dcucumber.options` on the command-line:

```java
@CucumberOptions(plugin = {"pretty", "html:target/cucumber"}, tags = {"~@ignore"})
```

## Test Reports
You can 'lock down' the fact that you only want to execute the single JUnit class that functions as a test-suite - by using the following [maven-surefire-plugin configuration](http://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>2.10</version>
    <configuration>
        <includes>
            <include>animals/AnimalsTest.java</include>
        </includes>
        <systemProperties>
            <cucumber.options>--plugin junit:target/cucumber-junit.xml</cucumber.options>
        </systemProperties>            
    </configuration>
</plugin> 
```

Note how the `cucumber.options` can be specified using the `<systemProperties>` configuration. Options here would over-ride corresponding options specified if a `@CucumberOptions` annotation is present (on `AnimalsTest.java`). So for the above example, any `plugin` options present on the annotation would not take effect, but anything else (for example `tags`) would continue to work.

With the above in place, you don't have to use `-Dtest=AnimalsTest` on the command-line any more. You may need to point your CI to the location of the JUnit XML report (e.g. `target/cucumber-junit.xml`) so that test-reports are generated correctly.

The [Karate Demo](karate-demo) has a working example of this set-up.  Also refer to the section on [switching the environment](#switching-the-environment) for more ways of running tests via Maven using the command-line.

The big drawback of the 'Cucumber-native' approach is that you cannot run tests in parallel. But you have the option of choosing other report formats, for e.g. `html`. The recommended approach for Karate reporting in a Continuous Integration set-up is described in the next section which focuses on emitting the [JUnit XML](https://wiki.jenkins-ci.org/display/JENKINS/JUnit+Plugin) format that most CI tools can consume. The [Cucumber JSON format](https://relishapp.com/cucumber/cucumber/docs/formatters/json-output-formatter) is also emitted, which gives you plenty of options for generating pretty reports using third-party maven plugins.

## Parallel Execution
Karate can run tests in parallel, and dramatically cut down execution time. This is a 'core' feature and does not depend on JUnit, TestNG or even Maven.

```java
import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import cucumber.api.CucumberOptions;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

@CucumberOptions(tags = {"~@ignore"})
public class TestParallel {
    
    @Test
    public void testParallel() {
        KarateStats stats = CucumberRunner.parallel(getClass(), 5, "target/surefire-reports");
        assertTrue("scenarios failed", stats.getFailCount() == 0);
    }
    
}
```
Things to note:
* You don't use a JUnit runner, and you write a plain vanilla JUnit test (it could very well be TestNG or plain old Java) using the `CucumberRunner.parallel()` static method in `karate-core`.
* You can use the returned `KarateStats` to check if any scenarios failed.
* The first argument can be any class that marks the 'root package' in which `*.feature` files will be looked for, and sub-directories will be also scanned. As shown above you would typically refer to the enclosing test-class itself.
* The second argument is the number of threads to use.
* [JUnit XML](https://wiki.jenkins-ci.org/display/JENKINS/JUnit+Plugin) reports will be generated in the path you specify as the third parameter, and you can easily configure your CI to look for these files after a build (for e.g. in `**/*.xml` or `**/surefire-reports/*.xml`). This argument is optional and will default to `target/surefire-reports`.
* [Cucumber JSON reports](https://relishapp.com/cucumber/cucumber/docs/formatters/json-output-formatter) will be generated side-by-side with the JUnit XML reports and with the same name, except that the extension will be `.json` instead of `.xml`.
* No other reports will be generated. If you specify a `plugin` option via the `@CucumberOptions` annotation, or the command-line, or the 'maven-surefire-plugin' `<systemProperties>` - it will be ignored.
* But all other options passed to `@CucumberOptions` would work as expected, provided you point the `CucumberRunner` to the annotated class as the first argument. Note that in this example, any `*.feature` file tagged as `@ignore` will be skipped.
* For convenience, some stats are logged to the console when execution completes, which should look something like this:

```
======================================================
elapsed time: 1.778000 | test time: 7.895000
thread count:  5 | parallel efficiency: 0.888076
scenarios: 12 | failed:  0 | skipped:  0
======================================================
```

This is the preferred way of automating the execution of all Karate tests in a project, mainly because the other 'native' Cucumber reports (e.g. HTML) are not thread-safe. In other words, please rely on the `CucumberRunner.parallel()` JUnit XML and Cucumber JSON output for CI and test result reporting, and if you see any problems, or if your CI tool does not support the JUnit XML format, please submit a defect report.

The [Karate Demo](karate-demo) has a working example of this set-up. It also [explains how](karate-demo#example-report) a third-party library can be easily used to generate some very nice-looking reports. For example, here below is an actual report generated by the [cucumber-reporting](https://github.com/damianszczepanik/cucumber-reporting) open-source library.

![Karate and Maven Cucumber Reporting](karate-demo/src/test/resources/karate-maven-cucumber-reporting.png)

The demo also features [code-coverage using Jacoco](karate-demo#code-coverage-using-jacoco).

## Logging
> This is optional, and Karate will work without the logging config in place, but the default
console logging may be too verbose for your needs.

Karate uses [LOGBack](http://logback.qos.ch) which looks for a file called logback-test.xml 
on the classpath.  If you use the Maven `<test-resources>` tweak described earlier (recommended), 
keep this file in `src/test/java`, or else it should go into `src/test/resources`.  

Here is a sample `logback-test.xml` for you to get started.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
 
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
  
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>target/karate.log</file>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>    
   
    <logger name="com.intuit" level="DEBUG"/>
   
    <root level="info">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE" />
    </root>
  
</configuration>
```
You can change the `com.intuit` logger level to `INFO` to reduce the amount of logging. When the level is `DEBUG` the entire request and response payloads are logged.

# Configuration
> You can skip this section and jump straight to the [Syntax Guide](#syntax-guide) if you are in a hurry to get started with Karate. Things will work even if the `karate-config.js` file is not present.

The only 'rule' is that on start-up Karate expects a file called `karate-config.js` to exist on the classpath and contain a JavaScript function.  Karate will invoke this function and from that point onwards, you are free to set config properties in a variety of ways.  One possible method is shown below, based on reading a Java system property.

```javascript    
function() {   
  var env = karate.env; // get java system property 'karate.env'
  karate.log('karate.env system property was:', env);
  if (!env) {
    env = 'dev'; // a custom 'intelligent' default
  }
  var config = { // base config
    env: env,
    appId: 'my.app.id',
    appSecret: 'my.secret',
    someUrlBase: 'https://some-host.com/v1/auth/',
    anotherUrlBase: 'https://another-host.com/v1/'
  };
  if (env == 'stage') {
    // over-ride only those that need to be
    config.someUrlBase = 'https://stage-host/v1/auth';
  } else if (env == 'e2e') {
    config.someUrlBase = 'https://e2e-host/v1/auth';
  }
  // don't waste time waiting for a connection or if servers don't respond within 5 seconds
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 5000);
  return config;
}
```
The function is expected to return a JSON object and all keys and values in that JSON object will be made available as script variables.  And that's all there is to Karate configuration.

> The [`karate`](#the-karate-object) object has a few helper methods described in detail later in this document where the [`call`](#calling-javascript-functions) keyword is explained.  Here above, you see `karate.log()`, `karate.env` and `karate.configure()` being used.

This decision to use JavaScript for config is influenced by years of experience with the set-up of complicated test-suites and fighting with [Maven profiles](http://maven.apache.org/guides/introduction/introduction-to-profiles.html), [Maven resource-filtering](https://maven.apache.org/plugins/maven-resources-plugin/examples/filter.html) and the XML-soup that somehow gets summoned by the [Maven AntRun plugin](http://maven.apache.org/plugins/maven-antrun-plugin/usage.html).

Karate's approach frees you from Maven, is far more expressive, allows you to eyeball all environments in one place, and is still a plain-text file.  If you want, you could even create nested chunks of JSON that 'name-space' your config variables.

This approach is indeed slightly more complicated than traditional `*.properties` files - but you _need_ this complexity. Keep in mind that these are tests (not production code) and this config is going to be maintained more by the dev or QE team instead of the 'ops' or operations team.

And there is no more worrying about Maven profiles and whether the 'right' `*.properties` file has been copied to the proper place.

## Switching the Environment
There is only one thing you need to do to switch the environment - which is to set a Java system property.

The recipe for doing this when running Maven from the command line is:
```
mvn test -DargLine="-Dkarate.env=e2e"
```
You can refer to the documentation of the
[Maven Surefire Plugin](http://maven.apache.org/plugins-archives/maven-surefire-plugin-2.12.4/examples/system-properties.html)
for alternate ways of achieving this, but the `argLine` approach is the simplest and should be more than sufficient for your Continuous Integration or test-automation needs.

Here's a reminder that running any [single JUnit test via Maven](https://maven.apache.org/surefire/maven-surefire-plugin/examples/single-test.html) can be done by:
```
mvn test -Dtest=CatsRunner
```
Where `CatsRunner` is the JUnit class name (in any package) you wish to run.

Karate is flexible, you can easily over-write config variables within each individual test-script - which is very convenient when in dev-mode or rapid-prototyping.

Just for illustrative purposes, you could 'hard-code' the `karate.env` for a specific JUnit test like this. Since CI test-automation would typically use a [designated 'top-level suite' test-runner](#test-reports), you can actually have these individual test-runners lying around without any ill-effects. They are obviously useful for dev-mode troubleshooting. To ensure that they don't get run by CI by mistake - just *don't* use the `*Test.java` naming convention.

```java
package animals.cats;

import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
public class CatsRunner {   
    
    @BeforeClass
    public static void before() {
        System.setProperty("karate.env", "pre-prod");
    }

}
```

# Syntax Guide
## Script Structure
Karate scripts are technically in '[Gherkin](https://github.com/cucumber/cucumber/wiki/Gherkin)' format - but all you need to grok as someone who needs to test web-services are the three sections: `Feature`, `Background` and `Scenario`. There can be multiple Scenario-s in a `*.feature` file.  

Lines that start with a `#` are comments.
```cucumber
Feature: brief description of what is being tested
    more lines of description if needed.

Background:
# steps here are expecuted before each Scenario in this file

Scenario: brief description of this scenario
# steps for this scenario

Scenario: a different scenario
# steps for this other scenario
```
### Given-When-Then
The business of web-services testing requires access to low-level aspects such as HTTP headers, URL-paths, query-parameters, complex JSON or XML payloads and response-codes. And Karate gives you control over these aspects with the small set of keywords focused on HTTP such as [`url`](#url), [`path`](#path), [`param`](#param), etc.

Karate does not attempt to have tests be in "natural language" like how Cucumber tests are [traditionally expected to be](https://cucumber.io/docs/reference#gherkin). That said, the syntax is very concise, and the convention of every step having to start with either `Given`, `And`, `When` or `Then`, makes things very readable. You end up with a decent approximation of BDD even though web-services by nature are "headless", without a UI, and not really human-friendly.

#### Cucumber vs Karate
If you are familiar with Cucumber (JVM), you may be wondering if you need to write [step-definitions](https://cucumber.io/docs/reference/jvm#step-definitions). The answer is **no**.

Karate's approach is that all the step-definitions you need in order to work with HTTP, JSON and XML have been already implemented. And since you can easily extend Karate [using JavaScript](#call), there is no need to compile Java code any more.

The following table summmarizes some key differences between Cucumber and Karate.

:white_small_square: | Cucumber | Karate
-------------------- | -------- | ------
**Step Definitions Built-In** | **No**. You need to keep implementing them as your functionality grows. [This can get very tedious](https://angiejones.tech/rest-assured-with-cucumber-using-bdd-for-web-services-automation#comment-40). | :white_check_mark: **Yes**. No extra Java code needed.
**Single Layer of Code To Maintain** | **No**. There are 2 Layers. The [Gherkin](https://cucumber.io/docs/reference#gherkin) spec or `*.feature` files make up one layer, and you will also have the corresponding Java step-definitions. | :white_check_mark: **Yes**. Only 1 layer of Karate-script (based on Gherkin).
**Readable Specification** | **Yes**. Cucumber will read like natural language _if_ you implement the step-definitions right. | :x: **No**. Although Karate is simple, and a [true DSL](https://ayende.com/blog/2984/dsl-vs-fluent-interface-compare-contrast), it is ultimately a mini-programming language. But it is perfect for testing web-services at the level of HTTP requests and responses.
**Re-Use Feature Files** | **No**. Cucumber does not support being able to call (and thus re-use) other `*.feature` files from a test-script. | :white_check_mark: [**Yes**](#calling-other-feature-files).
**Dynamic Data-Driven Testing** | **No**. Cucumber's [`Scenario Outline`](#the-cucumber-way) expects the `Examples` to contain a fixed set of rows. | :white_check_mark: **Yes**. Karate's support for calling other `*.feature` files allows you to use a [JSON array as the data-source](#data-driven-features).
**Parallel Execution** | **No**. There are some challenges (especially with reporting) and you can find [various](https://opencredo.com/test-automation-concepts-parallel-test-execution/) [threads](http://stackoverflow.com/questions/41034116/how-to-execute-cucumber-feature-file-parallel) and [third-party](https://github.com/DisneyStudios/cucumber-slices-maven-plugin) [projects](https://github.com/temyers/cucumber-jvm-parallel-plugin) on the [internet](https://github.com/trivago/cucable-plugin) that attempt to close this gap. | :white_check_mark: [**Yes**](#parallel-execution).
**Run 'Set-Up' Routines Only Once** | **No**. Cucumber has a limitation where `Background` steps are re-run for every `Scenario` and worse - even for every `Examples` row within a `Scenario Outline`. This has been a [highly-requested open issue](https://github.com/cucumber/cucumber-jvm/issues/515) for a *long* time. | :white_check_mark: [**Yes**](#callonce).

One nice thing about the design of the underlying Cucumber framework is that script-steps are treated the same no matter whether they start with the keyword `Given`, `And`, `When` or `Then`.  What this means is that you are free to use whatever makes sense for you.  You could even have all the steps start with `When` and Karate won't care.

In fact Cucumber supports the [catch-all symbol '`*`'](https://www.relishapp.com/cucumber/cucumber/docs/gherkin/using-star-notation-instead-of-given-when-then) - instead of forcing you to use `Given`, `When` or `Then`. This is perfect for those cases where it really doesn't make sense - for example the [`Background`](#script-structure) section or when you use the [`def`](#def) or [`set`](#set) syntax. When eyeballing a test-script, think of the `*` as a 'bullet-point'.

You can read more about the Given-When-Then convention at the [Cucumber reference documentation](https://cucumber.io/docs/reference).

Since Karate is based on Cucumber, you can also employ [data-driven](#data-driven-tests) techniques such as expressing data-tables in test scripts.

Another good thing that Karate inherits is the nice IDE support for Cucumber that [IntelliJ](https://www.jetbrains.com/idea/help/cucumber.html) and [Eclipse](https://cucumber.io/cucumber-eclipse/) have. So you can do things like right-click and run a `*.feature` file (or scenario) without needing to use a JUnit runner.

For a detailed discussion on BDD and how Karate relates to Cucumber, please refer to this blog-post: [Yes, Karate is not *true* BDD](https://medium.com/@ptrthomas/yes-karate-is-not-true-bdd-698bf4a9be39).

With the formalities out of the way, let's dive straight into the syntax.

# Setting and Using Variables
## `def`
### Set a named variable
```cucumber
# assigning a string value:
Given def myVar = 'world'

# using a variable
Then print myVar

# assigning a number (you can use '*' instead of Given / When / Then)
* def myNum = 5
```
Note that `def` will over-write any variable that was using the same name earlier. Keep in mind that the start-up [configuration routine](#configuration) could have already initialized some variables before the script even started.

## `assert`
### Assert if an expression evaluates to `true`
Once defined, you can refer to a variable by name. Expressions are evaluated using the embedded JavaScript engine. The assert keyword can be used to assert that an expression returns a boolean value.

```cucumber
Given def color = 'red '
And def num = 5
Then assert color + num == 'red 5'
```
Everything to the right of the `assert` keyword will be evaluated as a single expression.

Something worth mentioning here is that you would hardly need to use `assert` in your test scripts. Instead you would typically use the [`match`](#match) keyword, that is designed for performing powerful assertions against JSON and XML response payloads.

## `print`
### Log to the console
You can use `print` to log variables to the console in the middle of a script. All of the text to the right of the `print` keyword will be evaluated as a single expression (somewhat like [`assert`](#assert)).
```cucumber
* print 'the value of a is ' + a
```

# 'Native' data types
Native data types mean that you can insert them into a script without having to worry about enclosing them in strings and then having to 'escape' double-quotes all over the place. They seamlessly fit 'in-line' within your test script.

## JSON
Note that the parser is 'lenient' so that you don't have to enclose all keys in double-quotes.
```cucumber
* def cat = { name: 'Billie', scores: [2, 5] }
* assert cat.scores[1] == 5
```
When asserting for expected values in JSON or XML you are probably better off using [`match`](#match) instead of [`assert`](#assert).
```cucumber
* def cats = [{ name: 'Billie' }, { name: 'Bob' }]
* match cats[1] == { name: 'Bob' }
```

Karate's native support for JSON means that you can assign parts of a JSON instance into another variable, which is useful when dealing with complex [`response`](#response) payloads.
```cucumber
* def first = cats[0]
* match first == { name: 'Billie' }
```

For manipulating or updating JSON (or XML) using path expressions, refer to the [`set`](#set) keyword.

## XML
```cucumber
Given def cat = <cat><name>Billie</name><scores><score>2</score><score>5</score></scores></cat>
# sadly, xpath list indexes start from 1
Then match cat/cat/scores/score[2] == '5'
# but karate allows you to traverse xml like json !!
Then match cat.cat.scores.score[1] == 5
```

### Embedded Expressions
Karate has a very useful JSON 'templating' approach. Variables can be referred to within JSON, for example:

```cucumber
When def user = { name: 'john', age: 21 }
And def lang = 'en'
Then def session = { name: '#(user.name)', locale: '#(lang)', sessionUser: '#(user)'  }
```
So the rule is - if a string value within a JSON (or XML) object declaration is enclosed between `#(` and `)` - it will be evaluated as a JavaScript expression. And any variables which are alive in the context can be used in this expression.

This comes in useful in some cases - and avoids needing to use the [`set`](#set) keyword, [JavaScript functions](#javascript-functions) or [JsonPath](https://github.com/jayway/JsonPath#path-examples) expressions to manipulate JSON. So you get the best of both worlds: the elegance of JSON to express complex nested data - while at 
the same time being able to dynamically plug values (that could even be other JSON object-trees) into a JSON 'template'.

The [GraphQL / RegEx Replacement example](#graphql--regex-replacement-example) also demonstrates the usage of 'embedded expressions', look for: `'#(query)'`. And there are more examples in the [Karate Demos](karate-demo).

### Multi-Line Expressions
The keywords [`def`](#def), [`set`](#set), [`match`](#match) and [`request`](#request) take multi-line input as the last argument. This is useful when you want to express a one-off lengthy snippet of text in-line, without having to split it out into a separate [file](#reading-files). Here are some examples:
```cucumber
# instead of:
* def cat = <cat><name>Billie</name><scores><score>2</score><score>5</score></scores></cat>

# this is more readable:
* def cat = 
"""
<cat>
    <name>Billie</name>
    <scores>
        <score>2</score>
        <score>5</score>
    </scores>
</cat>
"""
# example of a request payload in-line
Given request 
""" 
<?xml version='1.0' encoding='UTF-8'?>
<S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
<S:Body>
<ns2:QueryUsageBalance xmlns:ns2="http://www.mycompany.com/usage/V1">
    <ns2:UsageBalance>
        <ns2:LicenseId>12341234</ns2:LicenseId>
    </ns2:UsageBalance>
</ns2:QueryUsageBalance>
</S:Body>
</S:Envelope>
"""

# example of a payload assertion in-line
Then match response ==
"""
{ id: { domain: "DOM", type: "entityId", value: "#ignore" },
  created: { on: "#ignore" }, 
  lastUpdated: { on: "#ignore" },
  entityState: "ACTIVE"
}
"""
```

## `table`
### A simple way to create JSON
Now that we have seen how JSON is a 'native' data type that Karate understands, there is a very nice way to create JSON using Cucumber's support for expressing [data-tables](http://www.thinkcode.se/blog/2014/06/30/cucumber-data-tables).

```cucumber
* table cats =
    | name | age |
    | Bob  | 2   |
    | Wild | 4   |
    | Nyan | 3   |

* match cats == [{name: 'Bob', age: 2}, {name: 'Wild', age: 4}, {name: 'Nyan', age: 3}]
```

The [`match`](#match) keyword is explained later, but it should be clear right away how convenient the `table` keyword is. JSON can be combined with the ability to [call other `*.feature` files](#data-driven-features) to achieve dynamic data-driven testing in Karate.

## `text`
### Don't parse, treat as raw text
Not something you would commonly use, but in some cases you need to disable Karate's default behavior of attempting to parse anything that looks like JSON (or XML) when using [multi-line expressions](#multi-line-expressions). This is especially relevant when manipulating [GraphQL](http://graphql.org) queries - because although they look suspiciously like JSON, they are not, and tend to confuse Karate's internals. The other advantage is that 'white space' such as 'line-feeds' (which are significant in GraphQL) would be handled correctly, and retained. And as shown in the example below, having text 'in-line' is useful especially when you use the `Scenario Outline:` and `Examples:` for [data-driven tests](#data-driven-tests).

```cucumber
Scenario Outline:
# note the 'text' keyword instead of 'def'
* text query =
"""
mutation { 
  someEntity(input: { 
    clientMutationId: "1" 
    someChild: { 
      name: "<name>"
    } 
  }) {
    clientMutationId
    someChild {
      id 
      name 
    } 
  } 
}
"""
Given path 'graphql'
And request { query: '#(query)' }
And header Accept = 'application/json'
When method post
Then status 200
And def child = response.data.someEntity.someChild
And match child == { id: '#notnull', name: '<name>' }

Examples:
| name  |
| John  |
| Smith | 
```

Note that if you did not need to inject [`Examples:`](#data-driven-tests) into 'placeholders' enclosed within `<` and `>`, [reading from a file](#reading-files) with the extension `*.txt` may have been sufficient.

## `yaml`
### Import YAML as JSON
For those who may prefer [YAML](http://yaml.org) as a simpler way to represent data, Karate allows you to read YAML content 'in-line' or even from a [file](#reading-files) - and it will be auto-converted to JSON.

```cucumber
# reading yaml 'in-line', note the 'yaml' keyword instead of 'def'
* yaml foo =
"""
name: John
input:
  id: 1
  subType: 
    name: Smith
    deleted: false
"""
# the data is now JSON, so you can do JSON-things with it
* match foo ==
"""
{
  name: 'John',
  input: { 
    id: 1,
    subType: { name: 'Smith', deleted: false }    
  }
}
"""

# yaml from a file (the extension matters), and the data-type of 'bar' would be JSON
* def bar = read('data.yaml')
```

## JavaScript Functions
JavaScript Functions are also 'native'. And yes, functions can take arguments.  
Standard JavaScript syntax rules apply.

> [ES6 arrow functions](https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Functions/Arrow_functions) 
are **not** supported.

```cucumber
* def greeter = function(name){ return 'hello ' + name }
* assert greeter('Bob') == 'hello Bob'
```
### Java Interop
For more complex functions you are better off using the [multi-line](#multi-line-expressions) 'doc-string' approach. This example actually calls into existing Java code, and being able to do this opens up a whole lot of possibilities. The JavaScript interpreter will try to convert types across Java and JavaScript as smartly as possible. For e.g. JSON objects become Java Map-s, JSON arrays become Java List-s, and Java Bean properties are accessible (and update-able) using dot notation e.g. '`object.name`'

```cucumber
* def dateStringToLong =
"""
function(s) {
  var SimpleDateFormat = Java.type("java.text.SimpleDateFormat");
  var sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
  return sdf.parse(s).time; // '.getTime()' would also have worked instead of '.time'
} 
"""
* assert dateStringToLong("2016-12-24T03:39:21.081+0000") == 1482550761081
```

> More examples of Java interop and how to invoke custom code can be found in the section on [Calling Java](#calling-java).

Any JavaScript function in Karate has a variable called [`karate`](#the-karate-object) injected into the runtime, which provides some utility functions, for e.g. logging.

The `call` keyword provides an [alternate way of calling JavaScript functions](#calling-javascript-functions) that have only one argument. The argument can be provided after the function name, without parantheses, which makes things slightly more readable (and less cluttered) especially when the solitary argument is JSON.

```cucumber
* def timeLong = call dateStringToLong '2016-12-24T03:39:21.081+0000'
* assert timeLong == 1482550761081

# a better example, with a JSON argument
* def greeter = function(name){ return 'Hello ' + name.first + ' ' + name.last + '!' }
* def greeting = call greeter { first: 'John', last: 'Smith' }
```

## Reading Files

Reading files is achieved using the `read` keyword. By default, the file is expected to be in the same folder (package) as the `*.feature` file. But you can prefix the name with `classpath:` in which case the 'root' folder would be `src/test/java` (assuming you are using the [recommended folder structure](#folder-structure)).

Prefer `classpath:` when a file is expected to be heavily re-used all across your project.  And yes, relative paths will work.

```cucumber
# json
* def someJson = read('some-json.json')
* def moreJson = read('classpath:more-json.json')

# xml
* def someXml = read('../common/my-xml.xml')

# import yaml (will be converted to json)
* def jsonFromYaml = read('some-data.yaml')

# string
* def someString = read('classpath:messages.txt')

# javascript (will be evaluated)
* def someValue = read('some-js-code.js')

# if the js file evaluates to a function, it can be re-used later using the 'call' keyword
* def someFunction = read('classpath:some-reusable-code.js')
* def someCallResult = call someFunction

# the following short-cut is also allowed
* def someCallResult = call read('some-js-code.js')
```

You can also [re-use other `*.feature`](#calling-other-feature-files) files from test-scripts:

```cucumber
# perfect for all those common authentication or 'set up' flows
* def result = call read('classpath:some-reusable-steps.feature')
```

If a file does not end in '.json', '.xml', '.js' or '.txt' - it is treated as a stream which is typically what you would need for [`multipart`](#multipart-field) file uploads.
```cucumber
* def someStream = read('some-pdf.pdf')
```

Since it is internally implemented as a JavaScript function, you can mix calls to `read()` freely wherever JavaScript expressions are allowed:

```cucumber
* def someBigString = read('first.txt') + read('second.txt')
```

And a very common need would be to use a file as the [`request`](#request) body.

```cucumber
Given request read('some-big-payload.json')
```

Take a look at the [Karate Demos](karate-demo) for real-life examples of how you can use files for matching HTTP responses.

## Type Conversion
Internally, Karate will auto-convert JSON (and even XML) to Java `Map` objects. And JSON arrays would become Java `List`-s. But you will never need to worry about this internal data-representation most of the time.

In some rare cases, for e.g. if you acquired a string from some external source, or if you generated JSON (or XML) by concatenating text, you may want to convert a string to JSON and vice-versa. You can even perform a conversion from XML to JSON if you want.

One example of when you may want to convert JSON (or XML) to a string is when you are passing a payload to custom code via [Java interop](#calling-java). Do note that when passing JSON, the default `Map` and `List` representations should suffice for most needs ([see example](karate-demo/src/test/java/demo/java/cats-java.feature)), and using them would avoid un-necessary string-conversion.

So you have the following type markers you can use instead of [`def`](#def):
* `string` - convert XML, JSON or any other data-type to a string
* `json` - convert XML, a map-like or list-like object, or a string into JSON
* `xml` - convert JSON, a map-like object, or a string into XML
* `xmlstring` - specifically for converting XML into a string

These are best explained in this example file: [`type-conv.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/type-conv.feature)

# Core Keywords
They are `url`, `path`, `request`, `method` and `status`.

These are essential HTTP operations, they focus on setting one (non-keyed) value at a time and don't involve any '=' signs in the syntax.
## `url`
```cucumber
Given url 'https://myhost.com/v1/cats'
```
A URL remains constant until you use the `url` keyword again, so this is a good place to set-up the 'non-changing' parts of your REST URL-s.

A URL can take expressions, so the approach below is legal.  And yes, variables can come from global [config](#configuration).
```cucumber
Given url 'https://' + e2eHostName + '/v1/api'
```
## `path`
REST-style path parameters.  Can be expressions that will be evaluated.  Comma delimited values are supported which can be more convenient, and takes care of URL-encoding and appending '/' where needed.
```cucumber
Given path 'documents/' + documentId + '/download'

# this is equivalent to the above
Given path 'documents', documentId, 'download'

# or you can do the same on multiple lines if you wish
Given path 'documents'
And path documentId
And path 'download'
```
Note that the `path` 'resets' after any HTTP request is made but not the `url`. The [Hello World](#hello-world) is a great example of 'REST-ful' use of the `url` when the test focuses on a single REST 'resource'. Look at how the `path` did not need to be specified for the second HTTP `get` call since `/cats` is part of the `url`.

## `request`
In-line JSON:
```cucumber
Given request { name: 'Billie', type: 'LOL' }
```
In-line XML:
```cucumber
And request <cat><name>Billie</name><type>Ceiling</type></cat>
```
From a [file](#reading-files) in the same package.  Use the `classpath:` prefix to load from the 
classpath instead.
```cucumber
Given request read('my-json.json')
```
You could always use a variable:
```cucumber
And request myVariable
```
In most cases you won't need to set the `Content-Type` [`header`](#header) as Karate will automatically do the right thing depending on the data-type of the `request`.

Defining the `request` is mandatory if you are using an HTTP `method` that expects a body such as `post`. If you really need to have an empty body, you can use an empty string as shown below, and you can force the right `Content-Type` header by using the [`header`](#header) keyword.
```cucumber
Given request ''
And header Content-Type = 'text/html'
```

## `method`
The HTTP verb - `get`, `post`, `put`, `delete`, `patch`, `options`, `head`, `connect`, `trace`.

Lower-case is fine.
```cucumber
When method post
```
It is worth internalizing that during test-execution, it is upon the `method` keyword that the actual HTTP request is issued.  Which suggests that the step should be in the `When`
form, for e.g.: `When method post`. And steps that follow should logically be in the `Then` form.

For example:
```cucumber
When method get
# the step that immediately follows the above would typically be:
Then status 200
```
## `status`
This is a shortcut to assert the HTTP response code.
```cucumber
Then status 200
```
And this assertion will cause the test to fail if the HTTP response code is something else.

See also [`responseStatus`](#responsestatus).

# Keywords that set key-value pairs
They are `param`, `header`, `cookie`, `form field` and `multipart field`. 

The syntax will include a '=' sign between the key and the value.  The key should not be within quotes.

> To make dynamic data-driven testing easier, the following keywords also exist: [`params`](#params), [`headers`](#headers), [`cookies`](#cookies-json) and [`form fields`](#form-fields). They use JSON to build the relevant parts of the HTTP request.

## `param` 
Setting query-string parameters:
```cucumber
Given param someKey = 'hello'
And param anotherKey = someVariable

# multi-value params are also supported
* param myParam = 'foo', 'bar'
```

You can also use JSON to set multiple query-parameters in one-line using [`params`](#params) and this is especially useful for dynamic data-driven testing.

## `header`

You can use functions or expressions:
```cucumber
Given header Authorization = myAuthFunction()
And header transaction-id = 'test-' + myIdString
```

It is worth repeating that in most cases you won't need to set the `Content-Type` header as Karate will automatically do the right thing depending on the data-type of the [`request`](#request).

Because of how easy it is to set HTTP headers, Karate does not provide any special keywords for things like 
the [`Accept`](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept) header. You simply do 
something like this:

```cucumber
Given path 'some/path'
And request { some: 'data' }
And header Accept = 'application/json'
When method post
Then status 200
```

A common need is to send the same header(s) for _every_ request, and [`configure headers`](#configure-headers) (with JSON) is how you can set this up once for all subsequent requests. And if you do this within a `Background:` section, it would apply to all `Scenario:` sections within the `*.feature` file.

```cucumber
* configure headers = { Accept: 'application/xml' }
```

If you need headers to be dynamically generated for each HTTP request, use a JavaScript function with [`configure headers`](#configure-headers) instead of JSON.

Multi-value headers (though rarely used in the wild) are also supported:
```cucumber
* header myHeader = 'foo', 'bar'
```

Also look at the [`headers`](#headers) keyword which uses JSON and makes some kinds of dynamic data-driven testing easier.

## `cookie`
Setting a cookie:
```cucumber
Given cookie foo = 'bar'
```

You also have the option of setting multiple cookies in one-step using the [`cookies`](#cookies) keyword.

Note that any cookies returned in the HTTP response would be automatically set for any future requests. Refer to the documentation of the built-in variable [`responseCookies`](#responsecookies) for more details.

## `form field` 
HTML form fields would be URL-encoded when the HTTP request is submitted (by the [`method`](#method) step). You would typically use these to simulate a user sign-in and then grab a security token from the [`response`](#response). For example:

```cucumber
Given path 'login'
And form field username = 'john'
And form field password = 'secret'
When method post
Then status 200
And def authToken = response.token
```

Multi-values are supported the way you would expect (e.g. for simulating check-boxes and multi-selects):
```cucumber
* form field selected = 'apple', 'orange'
```

You can also dynamically set multiple fields in one step using the [`form fields`](#form-fields) keyword.

## `multipart field`
Use this for building multipart named (form) field requests.

 ```cucumber
Given multipart field file = read('test.pdf')
And multipart field fileName = 'some-name.pdf'
When method post
Then status 200
```

When 'multipart' content is involved, the `Content-Type` header defaults to `multipart/form-data`.
You can over-ride it by using the [`header`](#header) keyword before the `method` step.  Look at
[`multipart entity`](#multipart-entity) for an example.

## `multipart entity`

> This is technically not in the key-value form: `multipart field name = 'foo'`, but logically
belongs here in the documentation.

Use this for multipart content items that don't have field-names.  Here below is an example that 
also demonstrates using the [`multipart/related`](https://tools.ietf.org/html/rfc2387) content-type.

```cucumber
Given path '/v2/documents'
And multipart entity read('foo.json')
And multipart field image = read('bar.jpg')
And header Content-Type = 'multipart/related'
When method post 
Then status 201
```

# Keywords that set multiple key-value pairs in one step

`params`, `headers`, `cookies` and `form fields` take a single JSON argument (which can be in-line or a variable reference), and this enables certain types of dynamic data-driven testing. Here is a good example in the demos: [`dynamic-params.feature`](https://github.com/intuit/karate/blob/master/karate-demo/src/test/java/demo/search/dynamic-params.feature)

## `params`
```cucumber
* params { searchBy: 'client', active: true, someList: [1, 2, 3] }
```

See also [`param`](#param).

## `headers`
```cucumber
* def someData = { Authorization: 'sometoken', tx_id: '1234', extraTokens: ['abc', 'def'] }
* headers someData
```

See also [`header`](#header).

## `cookies`
```cucumber
* cookies { someKey: 'someValue', foo: 'bar' }
```

See also [`cookie`](#cookie).

## `form fields`
```cucumber
* def credentials = { username: '#(user.name)', password: 'secret', projects: ['one', 'two'] }
* form fields credentials
```

See also [`form field`](#form-field).

# SOAP
Since a SOAP request needs special handling, this is the only case where the
[`method`](#method) step is not used to actually fire the request to the server.

## `soap action`
The name of the SOAP action specified is used as the 'SOAPAction' header.  Here is an example
which also demonstrates how you could assert for expected values in the response XML.
```cucumber
Given request read('soap-request.xml')
When soap action 'QueryUsageBalance'
Then status 200
And match response /Envelope/Body/QueryUsageBalanceResponse/Result/Error/Code == 'DAT_USAGE_1003'
And match response /Envelope/Body/QueryUsageBalanceResponse == read('expected-response.xml')
```

Here is a working example of calling a SOAP service from the Karate project test-suite: [`soap.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/soap.feature)

# Managing Headers, SSL, Timeouts and HTTP Proxy
## `configure`
You can adjust configuration settings for the HTTP client used by Karate using this keyword. The syntax is similar to [`def`](#def) but instead of a named variable, you update configuration. Here are the configuration keys supported:

 Key | Type | Description
------ | ---- | ---------
`headers` | JavaScript Function | see [`configure headers`](#configure-headers)
`headers` | JSON | see [`configure headers`](#configure-headers)
`logPrettyRequest` | boolean | pretty print the request payload JSON or XML with indenting
`logPrettyResponse` | boolean | pretty print the response payload JSON or XML with indenting
`ssl` | boolean | Enable HTTPS calls without needing to configure a trusted certificate or key-store.
`ssl` | string | Like above, but force the SSL algorithm to one of [these values](http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#SSLContext). (The above form internally defaults to `TLS` if simply set to `true`).
`connectTimeout` | integer | Set the connect timeout (milliseconds). The default is 30000 (30 seconds).
`readTimeout` | integer | Set the read timeout (milliseconds). The default is 30000 (30 seconds).
`proxy` | string | Set the URI of the HTTP proxy to use.
`proxy` | JSON | For a proxy that requires authentication, set the `uri`, `username` and `password`. (See example below).


Examples:
```cucumber
# pretty print the response payload
* configure logPrettyResponse = true

# enable ssl (and no certificate is required)
* configure ssl = true

# enable ssl and force the algorithm to TLSv1.2
* configure ssl = 'TLSv1.2'

# time-out if the response is not received within 10 seconds (after the connection is established)
* configure readTimeout = 10000

# set the uri of the http proxy server to use
* configure proxy = 'http://my.proxy.host:8080'

# proxy which needs authentication
* configure proxy = { uri: 'http://my.proxy.host:8080', username: 'john', password: 'secret' }
```

And if you need to set some of these 'globally' you can easily do so using [the `karate` object](#the-karate-object) in [`karate-config.js`](#configuration).

# Preparing, Manipulating and Matching Data
Now it should be clear how Karate makes it easy to express JSON or XML. If you read from a file, the advantage is that multiple scripts can re-use the same data.

Once you have a JSON or XML object, Karate provides multiple ways to manipulate, extract or transform data. And you can easily assert that the data is as expected by comparing it with another JSON or XML object.

## `match`
### Payload Assertions / Smart Comparison
The `match` operation is smart because white-space does not matter, and the order of keys (or data elements) does not matter. Karate is even able to [ignore fields you choose](#ignore-or-validate) - which is very useful when you want to handle server-side dynamically generated fields such as UUID-s, time-stamps, security-tokens and the like.

The match syntax involves a double-equals sign '==' to represent a comparison (and not an assignment '=').

Since `match` and `set` go well together, they are both introduced in the examples in the section below.

## `set`
### Manipulating Data
Game, `set` and `match` - Karate !

Setting values on JSON documents is simple using the `set` keyword and [JsonPath expressions](https://github.com/jayway/JsonPath#path-examples).

```cucumber
* def myJson = { foo: 'bar' }
* set myJson.foo = 'world'
* match myJson == { foo: 'world' }

# add new keys.  you can use pure JsonPath expressions (notice how this is different from the above)
* set myJson $.hey = 'ho'
* match myJson == { foo: 'world', hey: 'ho' }

# and even append to json arrays (or create them automatically)
* set myJson.zee[0] = 5
* match myJson == { foo: 'world', hey: 'ho', zee: [5] }

# nested json ? no problem
* set myJson.cat = { name: 'Billie' }
* match myJson == { foo: 'world', hey: 'ho', zee: [5], cat: { name: 'Billie' } }

# and for match - the order of keys does not matter
* match myJson == { cat: { name: 'Billie' }, hey: 'ho', foo: 'world', zee: [5] }

# you can ignore fields marked with '#ignore'
* match myJson == { cat: '#ignore', hey: 'ho', foo: 'world', zee: [5] }
```

XML and XPath works just like you'd expect.
```cucumber
* def cat = <cat><name>Billie</name></cat>
* set cat /cat/name = 'Jean'
* match cat / == <cat><name>Jean</name></cat>

# you can even set whole fragments of xml
* def xml = <foo><bar>baz</bar></foo>
* set xml/foo/bar = <hello>world</hello>
* match xml == <foo><bar><hello>world</hello></bar></foo>
```

Refer to the section on [XPath Functions](#xpath-functions) for examples of advanced XPath usage.

## `remove`
This is like the opposite of [`set`](#set) if you need to remove keys or data elements from JSON or XML instances. You can even remove JSON array elements by index.
```cucumber
* def json = { foo: 'world', hey: 'ho', zee: [1, 2, 3] }
* remove json.hey
* match json == { foo: 'world', zee: [1, 2, 3] }
* remove json $.zee[1]
* match json == { foo: 'world', zee: [1, 3] }
```

`remove` works for XML elements as well:
```cucumber
* def xml = <foo><bar><hello>world</hello></bar></foo>
* remove xml/foo/bar/hello
* match xml == <foo><bar/></foo>
* remove xml /foo/bar
* match xml == <foo/>
```

## Ignore or Validate
When expressing expected results (in JSON or XML) you can mark some fields to be ignored when the match (comparison) is performed.  You can even use a regular-expression so that instead of checking for equality, Karate will just validate that the actual value conforms to the expected pattern.

This means that even when you have dynamic server-side generated values such as UUID-s and time-stamps appearing in the response, you can still assert that the full-payload matched in one step.

```cucumber
* def cat = { name: 'Billie', type: 'LOL', id: 'a9f7a56b-8d5c-455c-9d13-808461d17b91' }
* match cat == { name: '#ignore', type: '#regex [A-Z]{3}', id: '#uuid' }
# this will fail
# * match cat == { name: '#ignore', type: '#regex .{2}', id: '#uuid' }	
```
The supported markers are the following:

Marker | Description
------ | -----------
#ignore | Skip comparison for this field
#null | Expects actual value to be null
#notnull | Expects actual value to be not-null
#array | Expects actual value to be a JSON array
#object | Expects actual value to be a JSON object
#boolean | Expects actual value to be a boolean `true` or `false`
#number | Expects actual value to be a number
#string | Expects actual value to be a string
#uuid | Expects actual (string) value to conform to the UUID format
#regex STR | Expects actual (string) value to match the regular-expression 'STR' (see examples above)
#? EXPR | Expects the JavaScript expression 'EXPR' to evaluate to true, see [self-validation expressions](#self-validation-expressions) below
#[NUM] EXPR | Advanced array validation, see [schema validation](#schema-validation)
#(EXPR) | For completeness, [embedded expressions](#embedded-expressions) belong in this list as well

### Optional Fields
If two cross-hatch `#` symbols are used as the prefix (for example: `##number`), it means that the key is optional or that the value can be null.
```cucumber
* def foo = { bar: 'baz' }
* match foo == { bar: '#string', ban: '##string' }
```

### 'Self' Validation Expressions
The special 'predicate' marker in the last row of the table above is an interesting one.  It is best explained via examples.

Observe how the value of the field being validated (or 'self') is injected into the 'underscore' expression variable: '`_`'
```cucumber
* def date = { month: 3 }
* match date == { month: '#? _ > 0 && _ < 13' }
```

What is even more interesting is that expressions can refer to variables:
```cucumber
* def date = { month: 3 }
* def min = 1
* def max = 12
* match date == { month: '#? _ >= min && _ <= max' }
```

And functions work as well ! You can imagine how you could evolve a nice set of utilities that validate all your domain objects.
```cucumber
* def date = { month: 3 }
* def isValidMonth = function(m) { return m >= 0 && m <= 12 }
* match date == { month: '#? isValidMonth(_)' }
```

Especially since strings can be easily coerced to numbers (and vice-versa) in Javascript, you can combine built-in validators with the self-valildation 'predicate' form like this: `'#number? _ > 0'`
```cucumber
# given this invalid input (string instead of number)
* def date = { month: '3' }
# this will pass
* match date == { month: '#? _ > 0' }
# but this 'combined form' will fail, which is what we want
# * match date == { month: '#number? _ > 0' }

```

You can actually refer to any JsonPath on the document via `$` and perform cross-field or conditional validations ! This example uses [`contains`](#match-contains) and the [`#?`](#self-validation-expressions) 'predicate' syntax - and situations where this comes in useful will be apparent when we discuss [`match each`](#match-each).

```cucumber
Given def temperature = { celsius: 100, fahrenheit: 212 }
Then match temperature == { celsius: '#number', fahrenheit: '#? _ == $.celsius * 1.8 + 32' }
# when validation logic is an 'equality' check, an embedded expression works better
Then match temperature contains { fahrenheit: '#($.celsius * 1.8 + 32)' }
```

### `match` for Text and Streams

```cucumber
# when the response is plain-text
Then match response == 'Health Check OK'

# when the response is a file (stream)
Then match response == read('test.pdf')

# incidentally, match and assert behave exactly the same way for strings
* def hello = 'Hello World!'
* match hello == 'Hello World!'
* assert hello == 'Hello World!'
```

Checking if a string is contained within another string is a very common need and [`match` (name) `contains`](#match-contains) works just like you'd expect:
```cucumber
* def hello = 'Hello World!'
* match hello contains 'World'
```

### `match header`
Since asserting against header values in the response is a common task - `match header` has a special meaning.  It short-cuts to the pre-defined variable [`responseHeaders`](#responseheaders) and reduces some complexity - because strictly, HTTP headers are a 'multi-valued map' or a 'map of lists' - the Java-speak equivalent being `Map<String, List<String>>`.
```cucumber
# so after a http request
Then match header Content-Type == 'application/json'
# 'contains' works as well
Then match header Content-Type contains 'application'
```  
Note the extra convenience where you don't have to enclose the LHS key in quotes.

You can always directly access the variable called [`responseHeaders`](#responseheaders) if you wanted to do more checks, but you typically won't need to.

## Matching Sub-Sets of JSON Keys and Arrays
### `match contains`
#### JSON Keys
In some cases where the response JSON is wildly dynamic, you may want to only check for the existence of some keys. And `match` (name) `contains` is how you can do so:
```cucumber
* def foo = { bar: 1, baz: 'hello', ban: 'world' }

* match foo contains { bar: 1 }
* match foo contains { baz: 'hello' }
* match foo contains { bar:1, baz: 'hello' }
# this will fail
# * match foo == { bar:1, baz: 'hello' }
```

### (not) `!contains`
It is sometimes useful to be able to check if a key-value-pair does **not** exist. This is possible by prefixing `contains` with a `!` (with no space in between).

```cucumber
* def foo = { bar: 1, baz: 'hello', ban: 'world' }
* match foo !contains { bar: 2 }
* match foo !contains { huh: '#notnull' }
```

The `!` (not) operator is especially useful for `contains` and JSON arrays.

```cucumber
* def foo = [1, 2, 3]
* match foo !contains 4
* match foo !contains [5, 6]
```

#### JSON Arrays

This is a good time to deep-dive into JsonPath, which is perfect for slicing and dicing JSON into manageable chunks. It is worth taking a few minutes to go through the documentation and examples here: [JsonPath Examples](https://github.com/jayway/JsonPath#path-examples).

Here are some example assertions performed while scraping a list of child elements out of the JSON below. Observe how you can `match` the result of a JsonPath expression with your expected data.

```cucumber
Given def cat = 
"""
{
  name: 'Billie',
  kittens: [
    { id: 23, name: 'Bob' },
    { id: 42, name: 'Wild' }
  ]
}
"""
# normal 'equality' match. note the wildcard '*' in the JsonPath (returns an array)
Then match cat.kittens[*].id == [23, 42]

# when inspecting a json array, 'contains' just checks if the expected items exist
# and the size and order of the actual array does not matter
Then match cat.kittens[*].id contains 23
Then match cat.kittens[*].id contains [42]
Then match cat.kittens[*].id contains [23, 42]
Then match cat.kittens[*].id contains [42, 23]

# and yes, you can assert against nested objects within JSON arrays !
Then match cat.kittens contains [{ id: 42, name: 'Wild' }, { id: 23, name: 'Bob' }]

# ... and even ignore fields at the same time !
Then match cat.kittens contains { id: 42, name: '#string' }
```

It is worth mentioning that to do the equivalent of the last line in Java, you would typically have to traverse 2 Java Objects, one of which is within a list, and you would have to check for nulls as well.

When you use Karate, all your data assertions can be done in pure JSON and without needing a thick forest of companion Java objects. And when you [`read`](#read) your JSON objects from (re-usable) files, even complex response payload assertions can be accomplished in just a single line of Karate-script.

Refer to this [case study](https://twitter.com/KarateDSL/status/873035687817117696) for how dramatic the reduction of lines of code can be.

#### `match contains only`
For those cases where you need to assert that **all** array elements are present but in **any order**
you can do this:

```cucumber
* def data = { foo: [1, 2, 3] }
* match data.foo contains 1
* match data.foo contains [2]
* match data.foo contains [3, 2]
* match data.foo contains only [3, 2, 1]
* match data.foo contains only [2, 3, 1]
# this will fail
# * match data.foo contains only [2, 3]
```

## Validate every element in a JSON array
### `match each`
The `match` keyword can be made to iterate over all elements in a JSON array using the `each` modifier. Here's how it works:
```cucumber
* def data = { foo: [{ bar: 1, baz: 'a' }, { bar: 2, baz: 'b' }, { bar: 3, baz: 'c' }]}

* match each data.foo == { bar: '#number', baz: '#string' }

# and you can use 'contains' the way you'd expect
* match each data.foo contains { bar: '#number' }
* match each data.foo contains { bar: '#? _ != 4' }

# some more examples of validation macros
* match each data.foo contains { baz: "#? _ != 'z'" }
* def isAbc = function(x) { return x == 'a' || x == 'b' || x == 'c' }
* match each data.foo contains { baz: '#? isAbc(_)' }
``` 

Here is a contrived example that uses `match each`, [`contains`](#match-contains) and the [`#?`](#self-validation-expressions) 'predicate' marker to validate that the value of `totalPrice` is always equal to the `roomPrice` of the first item in the `roomInformation` array.

```cucumber
Given def json =
"""
{
  "hotels": [
    { "roomInformation": [{ "roomPrice": 618.4 }], "totalPrice": 618.4  },
    { "roomInformation": [{ "roomPrice": 679.79}], "totalPrice": 679.79 }
  ]
}
"""
Then match each json.hotels contains { totalPrice: '#? _ == $.roomInformation[0].roomPrice' }
# when validation logic is an 'equality' check, an embedded expression works better
Then match each json.hotels contains { totalPrice: '#($.roomInformation[0].roomPrice)' }
```

There is a shortcut for `match each` explained in the next section that can be quite useful, especially for 'in-line' schema-like validations.

## Schema Validation
Karate provides a far more simpler and more powerful way than [JSON-schema](http://json-schema.org) to validate the stucture of a given payload. You can even mix domain and conditional validations and perform all assertions in a single step.

But first, a special short-cut for array validation needs to be introduced:

```cucumber
* def foo = ['bar', 'baz']

# should be an array
* match foo == '#[]'

# should be an array of size 2
* match foo == '#[2]'

# should be an array of strings with size 2
* match foo == '#[2] #string'

# should be an array of strings each of length 3
* match foo == '#[] #string? _.length == 3'

# should be null or an array of strings
* match foo == '##[] #string'
```

This 'in-line' short-cut for validating JSON arrays is similar to how [`match each`](#match-each) works. So now, complex payloads (that include arrays) can easily be by validated in one step by combining [validation markers](#ignore-or-validate) like so:

```cucumber
* def oddSchema = { price: '#string', status: '#? _ < 3', ck: '##number', name: '#regex[0-9X]' }
* def isValidTime = read('time-validator.js')
When method get
Then match response ==
"""
{ 
  id: '#regex[0-9]+',
  count: '#number',
  odd: '#(oddSchema)',
  data: { 
    countryId: '#number', 
    countryName: '#string', 
    leagueName: '##string', 
    status: '#number? _ >= 0', 
    sportName: '#string',
    time: '#? isValidTime(_)'
  },
  odds: '#[] oddSchema'  
}
"""
```

Especially note the re-use of the `oddSchema` both as an [embedded-expression](#embedded-expressions) and as an array validation (on the last line).

And you can perform conditional / cross-field validations and even business-logic validations at the same time.

```cucumber
# optional (can be null) and if present should be an array of size greater than zero
* match $.odds == '##[_ > 0]'

# should be an array of size equal to $.count
* match $.odds == '#[$.count]'

# use a predicate function to validate each array element
* def isValidOdd = function(o){ return o.name.length == 1 }
* match $.odds == '#[]? isValidOdd(_)'
```

Refer to this for the complete example: [`schema-like.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/schema-like.feature)

And there is another example in the [karate-demos](#karate-demo): [`schema.feature`](karate-demo/src/test/java/demo/schema/schema.feature) where you can compare Karate's approach with an actual JSON-schema example. You can also find a nice visual comparison and explanation [here](https://twitter.com/KarateDSL/status/878984854012022784).

## `get`
By now, it should be clear that [JsonPath]((https://github.com/jayway/JsonPath#path-examples)) can be very useful for extracting JSON 'trees' out of a given object. The `get` keyword allows you to save the results of a JsonPath expression for later use - which is especially useful for dynamic [data-driven testing](#data-driven-features). For example:

```cucumber
* def cat = 
"""
{
  name: 'Billie',
  kittens: [
    { id: 23, name: 'Bob' },
    { id: 42, name: 'Wild' }
  ]
}
"""
* def kitnums = get cat.kittens[*].id
* match kitnums == [23, 42]
* def kitnames = get cat $.kittens[*].name
* match kitnames == ['Bob', 'Wild']
```

### XPath Functions
When handling XML, you sometimes need to call [XPath functions](https://docs.oracle.com/javase/tutorial/jaxp/xslt/xpath.html), for example to get the count of a node-set. XPath functions are not supported directly within [`match`](#match) statements. But by using the `get` keyword, you should be able to achieve any assertion involving XPath functions in two steps.

The last line below also shows how 'normal' (uncomplicated) XPath can be used to do a `match` in a single step.

```cucumber
* def myXml =
"""
<records>
  <record index="1">a</record>
  <record index="2">b</record>
  <record index="3" foo="bar">c</record>
</records>
"""
* def count = get myXml count(/records//record)
* assert count == 3

# you can actually do this 'JSON-style' in one step !
* assert myXml.records.record.length == 3

# some 'standard' xpath examples
* def second = get myXml //record[@index=2]
* assert second == 'b'

* match myXml //record[@foo='bar'] == 'c'
```

### Advanced XPath

Some XPath expressions return a list of nodes (instead of a single node). But since you can express a list of data-elements as a JSON array - even these XPath expressions can be used in `match` statements.

```cucumber
* def teachers = 
"""
<teachers>
  <teacher department="science">
    <subject>math</subject>
    <subject>physics</subject>
  </teacher>
  <teacher department="arts">
    <subject>political education</subject>
    <subject>english</subject>
  </teacher>
</teachers>
"""
* match teachers //teacher[@department='science']/subject == ['math', 'physics']
```

You can refer to this file (which is part of the Karate test-suite) for more XML examples: [`xml-and-xpath.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/xml-and-xpath.feature)


# Special Variables
These are 'built-in' variables, there are only a few and all of them give you access to the HTTP response.

## `response`
After every HTTP call this variable is set with the response body, and is available until the next HTTP request over-writes it. You can easily assign the whole `response` (or just parts of it using Json-Path or XPath) to a variable, and use it in later steps.

The response is automatically available as a JSON, XML or String object depending on what the response contents are.

As a short-cut, when running JsonPath expressions - '$' represents the `response`.  This has the advantage that you can use pure [JsonPath](https://github.com/jayway/JsonPath#path-examples) and be more concise.  For example:

```cucumber
# the three lines below are equivalent
Then match response $ == { name: 'Billie' }
Then match response == { name: 'Billie' }
Then match $ == { name: 'Billie' }

# the three lines below are equivalent
Then match response.name == 'Billie'
Then match response $.name == 'Billie'
Then match $.name == 'Billie'

```
And similarly for XML and XPath, '/' represents the `response`
```cucumber
# the four lines below are equivalent
Then match response / == <cat><name>Billie</name></cat>
Then match response/ == <cat><name>Billie</name></cat>
Then match response == <cat><name>Billie</name></cat>
Then match / == <cat><name>Billie</name></cat> 

# the three lines below are equivalent
Then match response /cat/name == 'Billie'
Then match response/cat/name == 'Billie'
Then match /cat/name == 'Billie'
```

## `responseCookies`
The `responseCookies` variable is set upon any HTTP response and is a map-like (or JSON-like) object. It can be easily inspected or used in expressions.
```cucumber
* assert responseCookies['my.key'].value == 'someValue'

# karate's unified data handling means that even 'match' works
* match responseCookies contains { time: '#notull' }

# ... which means that checking if a cookie does NOT exist is easy
* match responseCookies !contains { blah: '#notnull' }

# save a response cookie for later use
* def time = responseCookies.time.value
```
As a convenience, cookies from the previous response are collected and passed as-is as part of the next HTTP request.  This is what is normally expected and simulates a web-browser - which makes it easy to script things like HTML-form based authentication into test-flows.

Of course you can manipulate [`cookies`](#cookies) or each individual [`cookie`](#cookie) and even over-write them to `null` if you wish - at any point within a test script.

Each item within `responseCookies` is itself a 'map-like' object. Typically you would examine the `value` property as in the example above, but `domain` and `path` are also available.

## `responseHeaders`
See also [`match header`](#match-header) which is what you would normally need.

But if you need to use values in the response headers - they will be in a variable named `responseHeaders`. Note that it is a 'map of lists' so you will need to do things like this:
```cucumber
* def contentType = responseHeaders['Content-Type'][0]
```
And just as in the [`responseCookies`](#responsecookies) example above, you can use [`match`](#match) to run complex validations on the `responseHeaders`.

## `responseStatus`
You would normally only need to use the [`status`](#status) keyword.  But if you really need to use the HTTP response code in an expression or save it for later, you can get it as an integer:
```cucumber
* def uploadStatusCode = responseStatus
```
## `responseTime`
The response time (in milliseconds) for every HTTP request would be available in a variable called `responseTime`. You can use this to assert that the response was returned within the expected time like so:
```cucumber
When method post
Then status 201
And assert responseTime < 1000
```

# HTTP Header Manipulation
## `configure headers`
Custom header manipulation for every HTTP request is something that Karate makes very easy and pluggable. For every HTTP request made from Karate, the internal flow is as follows:
* did we [`configure`](#configure) the value of `headers` ?
* if so, is the configured value a JavaScript function ?
  * if so, a [`call`](#call) is made to that function.
  * did the function invocation return a map-like (or JSON) object ?
    * all the key-value pairs are added to the HTTP headers.
* or is the configured value a JSON object ?
  * all the key-value pairs are added to the HTTP headers.

This makes setting up of complex authentication schemes for your test-flows really easy. It typically ends up being a one-liner that appears in the `Background` section at the start of your test-scripts.  You can re-use the function you create across your whole project.
 
Here is an example JavaScript function that uses some variables in the context (which have been possibly set as the result of a sign-in) to build the `Authorization` header.

> In the example below, note the use of the [`karate`](#the-karate-object) object for getting the value of a dynamic variable. This is preferred because it takes care of situations such as if the value is 'undefined' in JavaScript.

```javascript
function() {
  var out = { // hard-coded here, but you can dynamically generate these values if needed
    txid_header: '1e2bd51d-a865-4d37-9ac9-c345dc59119b',
    ip_header: '123.45.67.89',    
  };
  var authString = '';
  var authToken = karate.get('authToken'); // use the 'karate' helper to do a 'safe' get of a 'dynamic' variable
  if (authToken) { // and if 'authToken' is not null ... 
    authString = ',auth_type=MyAuthScheme'
        + ',auth_key=' + authToken.key
        + ',auth_user=' + authToken.userId
        + ',auth_project=' + authToken.projectId;
  }
  // the 'appId' variable here is expected to have been set via config / init and will never change
  out.Authorization = 'My_Auth app_id=' + appId + authString;
  return out;
}
```
Assuming the above code is in a file called `my-headers.js`, the next section on [calling other feature files](#calling-other-feature-files) shows how it looks like in action at the beginning of a test script.

Notice how once the `authToken` variable is initialized, it is used by the above function to generate headers for every HTTP call made as part of the test flow.

If a few steps in your flow need to temporarily change (or completely bypass) the currently-set header-manipulation scheme, just update the `headers` configuration value or set it to `null` in the middle of a script.

The [karate-demo](karate-demo) has an example showing various ways to `configure` or set headers: [`headers.feature`](karate-demo/src/test/java/demo/headers/headers.feature) 

# Code Reuse / Common Routines

## `call`

In any complex testing endeavor, you would find yourself needing 'common' code that needs to be re-used across multiple test scripts. A typical need would be to perform a 'sign in', or create a fresh user as a pre-requisite for the scenarios being tested.

There are two types of code that can be `call`-ed. `*.feature` files and [JavaScript functions](#calling-javascript-functions).

## Calling other `*.feature` files
When you have a sequence of HTTP calls that need to be repeated for multiple test scripts, Karate allows you to treat a `*.feature` file as a re-usable unit. You can also pass parameters into the `*.feature` file being called, and extract variables out of the invocation result.

Here is an example of how to call another feature file, using the [`read`](#reading-files) function:

```cucumber
Feature: some feature

Background:
* configure headers = read('classpath:my-headers.js')
* def signIn = call read('classpath:my-signin.feature') { username: 'john', password: 'secret' }
* def authToken = signIn.authToken

Scenario: some scenario
# main test steps
```

The contents of `my-signin.feature` are shown below. A few points to note:
* Karate passes all context 'as-is' into the feature file being invoked. This means that all your [config variables](#configuration) and [`configure` settings](#configure) would be available to use, for example `loginUrlBase` in the example below.
* You can add (or over-ride) variables by passing a call 'argument' as shown above. Only one JSON argument is allowed, but this does not limit you in any way as you can use any complex JSON structure. You can even initialize the JSON in a separate step and pass it by name, especially if it is complex. Observe how using JSON for parameter-passing makes things super-readable.
* **All** variables that were defined (using [`def`](#def)) in the 'called' script would be returned as 'keys' within a JSON-like object. Note that this includes ['built-in' variables](#special-variables), which means that things like the last value of [`response`](#response) would also be returned. In the example above you can see that the JSON 'envelope' returned - is assigned to the variable named `signIn`. And then getting hold of any data that was generated by the 'called' script is as simple as accessing it by name, for example `signIn.authToken` as shown above. This design has the following advantages:
  * 'called' Karate scripts don't need to use any special keywords to 'return' data and can behave like 'normal' Karate tests in 'stand-alone' mode if needed
  * the data 'return' mechanism is 'safe', there is no danger of the 'called' script over-writing any variables in the 'calling' (or parent) script
  * the need to explicitly 'unpack' variables by name from the returned 'envelope' keeps things readable and maintainable in the 'caller' script

```cucumber
Feature: here are the contents of 'my-signin.feature'

Scenario:

Given url loginUrlBase
And request { userId: '#(username)', userPass: '#(password)' }
When method post
Then status 200
And def authToken = response

# second HTTP call, to get a list of 'projects'
Given path 'users', authToken.userId, 'projects'
When method get
Then status 200
# logic to 'choose' first project
And set authToken.projectId = response.projects[0].projectId;
```

The above example actually makes two HTTP requests - the first is a standard 'sign-in' POST and then (for illustrative purposes) another HTTP call (a GET) is made for retrieving a list of projects for the signed-in user, the first one is 'chosen' and added to the returned 'auth token' JSON object.

So you get the picture, any kind of complicated 'sign-in' flow can be scripted and re-used.

Do look at the documentation and example for [`configure headers`](#configure-headers) also as it goes hand-in-hand with `call`. In the above example, the end-result of the `call` to `my-signin.feature` resulted in the `authToken` variable being initialized. Take a look at how the [`configure headers`](#configure-headers) example uses the `authToken` variable.

### Data-Driven Features

If the argument passed to the [call of a `*.feature` file](#calling-other-feature-files) is a JSON array, something interesting happens. The feature is invoked for each item in the array. Each array element is expected to be a JSON object, and for each object - the behavior will be as described above.

But this time, the return value from the `call` step will be a JSON array of the same size as the input array. And each element of the returned array will be the 'envelope' of variables that resulted from each iteration where the `*.feature` got invoked.

Here is an example that combines the [`table`](#table) keyword with calling a `*.feature`. Observe how the [`get`](#get) keyword is used to 'distill' the result array of variable 'envelopes' into an array consisting only of `response` payloads.

```cucumber
* table kittens = 
    | name     | age |
    | Bob      | 2   |
    | Wild     | 1   |
    | Nyan     | 3   |

* def result = call read('cat-create.feature') kittens
* def created = get result[*].response
* match each created == { id: '#number', name: '#string', age: '#number' }
* match created[*].name contains only ['Bob', 'Wild', 'Nyan']
```

And here is how `cat-create.feature` could look like:

```cucumber
@ignore
Feature:

Scenario:

Given url someUrlFromConfig
And path 'cats'
And request { name: '#(name)', age: '#(age)' }
When method post
Then status 200
```

If you replace the `table` with perhaps a JavaScript function call that gets some JSON data from some data-source, you can imagine how you could go about dynamic data-driven testing.

Although it is just a few lines of code, take time to study the above example carefully. It is a great example of how to effectively use the unique combination of Cucumber and JsonPath that Karate provides. Also look at the [demo examples](karate-demo).

## Calling JavaScript Functions

Examples of [defining and using JavaScript functions](#javascript-functions) appear in earlier sections of this document. Being able to define and re-use JavaScript functions is a powerful capability of Karate. For example, you can:
* call re-usable functions that take complex data as an argument and return complex data that can be stored in a variable
* call and interoperate with Java code if needed
* share and re-use test utilities or 'helper' functionality across your organization

In real-life scripts, you would typically also use this capability of Karate to [`configure headers`](#configure-headers) where the specified JavaScript function uses the variables that result from a [sign in](#calling-other-feature-files) to manipulate headers for all subsequent HTTP requests.

And it is worth mentioning that the Karate [configuration 'bootstrap'](#configuration) routine is itself a JavaScript function.

### The `karate` object
A JavaScript function at runtime has access to a utility object in a variable named: `karate`.  This provides the following methods:

* `karate.set(name, value)` - sets the value of a variable (immediately), which may be needed in case any other routines (such as the [configured headers](#configure-headers)) depend on that variable
* `karate.set(name, path, value)` - this is only needed when dealing with XML and when you need to conditionally build elements. This is best explained via [an example](https://github.com/intuit/karate/blob/master/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/xml-and-xpath.feature#L78), and it behaves the same way as the [`set`](#set) keyword.
* `karate.get(name)` - get the value of a variable by name (or JsonPath expression), if not found - this returns `null` which is easier to handle in JavaScript (than `undefined`).
* `karate.log(... args)` - log to the same logger (and log file) being used by the parent process
* `karate.env` - gets the value (read-only) of the environment property 'karate.env', and this is typically used for bootstrapping [configuration](#configuration)
* `karate.properties[key]` - get the value of any Java system-property by name, useful for [advanced custom configuration](#dynamic-port-numbers)
* `karate.configure(key, value)` - does the same thing as the [`configure`](#configure) keyword, and a very useful example is to do `karate.configure('connectTimeout', 5000);` in [`karate-config.js`](#configuration) - which has the 'global' effect of not wasting time if a connection cannot be established within 5 seconds
* `karate.call(fileName, [arg])` - invoke a [`*.feature` file](#calling-other-feature-files) or a [JavaScript function](#calling-javascript-functions) the same way that [`call`](#call) works (with an optional solitary argument)

### Rules for Passing Data to the JavaScript Function
Only one argument is allowed. But this does not limit you in any way, because similar to how you can [call `*.feature files`](#calling-other-feature-files), you can pass a whole JSON object as the argument. In the case of the `call` of a JavaScript function, you can also pass a JSON array or a primitive (string, number, boolean) as the solitary argument, and the function implementation is expected to handle whatever is passed.

### Return types
Naturally, only one value can be returned.  But again, you can return a JSON object.
There are two things that can happen to the returned value.

Either - it can be assigned to a variable like so.
```cucumber
* def returnValue = call myFunction
```
Or - if a `call` is made without an assignment, and if the function returns a map-like
object, it will add each key-value pair returned as a new variable into the execution context.
```cucumber
# while this looks innocent ...
# ... behind the scenes, it could be creating (or over-writing) a bunch of variables !
* call someFunction
```
While this sounds dangerous and should be used with care (and limits readability), the reason
this feature exists is to quickly set (or over-write) a bunch of config variables when needed.
In fact, this is the mechanism used when [`karate-config.js`](#configuration) is processed on start-up.

You can invoke a function in a [re-usable file](#reading-files) using this short-cut.
```cucumber
* call read('my-function.js')
```
### HTTP Basic Authentication Example
This should make it clear why Karate does not provide 'out of the box' support for any particular HTTP authentication scheme.
Things are designed so that you can plug-in what you need, without needing to compile Java code. You get to choose how to
manage your environment-specific configuration values such as user-names and passwords.

First the JavaScript file, `basic-auth.js`:
```javascript
function(creds) {
  var temp = creds.username + ':' + creds.password;
  var Base64 = Java.type("java.util.Base64");
  var encoded = Base64.getEncoder().encodeToString(temp.bytes);
  return 'Basic ' + encoded;
}
```
And here's how it works in a test-script using the [`header`](#header) keyword.

```cucumber
* header Authorization = call read('basic-auth.js') { username: 'john', password: 'secret' }
```

You can set this up for all subsequent requests or dynamically generate headers for each HTTP request if you [`configure headers`](#configure-headers).

### Calling Java
There are examples of calling JVM classes in the section on [Java Interop](#java-interop) and in the [file-upload demo](karate-demo).

Calling any Java code is that easy.  Given this custom, user-defined Java class:
```java
package com.mycompany;

import java.util.HashMap;
import java.util.Map;

public class JavaDemo {    
    
    public Map<String, Object> doWork(String fromJs) {
        Map<String, Object> map = new HashMap<>();
        map.put("someKey", "hello " + fromJs);
        return map;
    }

    public static String doWorkStatic(String fromJs) {
        return "hello " + fromJs;
    }   

}
```
This is how it can be called from a test-script, and yes, even static methods can be invoked:
```cucumber
* def doWork =
"""
function(arg) {
  var JavaDemo = Java.type('com.mycompany.JavaDemo');
  var jd = new JavaDemo();
  return jd.doWork(arg);  
}
"""
# in this case the solitary 'call' argument is of type string
* def result = call doWork 'world'
* match result == { someKey: 'hello world' }

# using a static method - observe how java interop is truly seamless !
* def JavaDemo = Java.type('com.mycompany.JavaDemo')
* def result = JavaDemo.doWorkStatic('world')
* assert result == 'hello world'
```

Note that JSON gets auto-converted to `Map` (or `List`) when making the cross-over to Java. Refer to the [`cats-java.feature`](karate-demo/src/test/java/demo/java/cats-java.feature) demo for an example.

## `callonce`
Cucumber has a limitation where `Background` steps are re-run for every `Scenario`. And if you have a`Scenario Outline`, this happens for *every* row in the `Examples`. This is a problem especially for expensive, time-consuming HTTP calls, and this has been an [open issue for a long time](https://github.com/cucumber/cucumber-jvm/issues/515). 

Karate's `callonce` keyword behaves exactly like [`call`](#call) but is guaranteed to execute only once. The results of the first call are cached, and any future calls will simply return the cached result instead of executing the JavaScript function (or feature) again and again. 

This does require you to move 'set-up' into a separate `*.feature` (or JavaScript) file. But this totally makes sense for things not part of the 'main' test flow and which typically need to be re-usable anyway.

So you can indeed get the same effect as using a [`@BeforeClass`](http://junit.sourceforge.net/javadoc/org/junit/BeforeClass.html) annotation, and you can find an example in the [karate-demo](karate-demo).

# Advanced / Tricks

## GraphQL / RegEx replacement example
As a demonstration of Karate's power and flexibility, here is an example that reads a 
GraphQL string (which could be from a file) and manipulates it to build custom dynamic queries 
and filter criteria.

Here we have this JavaScript utlity function `replacer.js` that uses a regular-expression to 
replace-inject a criteria expression into the right place, given a GraphQL query.

```javascript
function(args) {
  var query = args.query;
  karate.log('before replacement: ', query);
  // the RegExp object is standard JavaScript
  var regex = new RegExp('\\s' + args.field + '\\s*{');
  karate.log('regex: ', regex);
  query = query.replace(regex, ' ' + args.field + '(' + args.criteria + ') {');
  karate.log('after replacement: ', query);
  return query; 
} 
```

Once the function is declared, observe how calling it and performing the replacement 
is an elegant one-liner.
```cucumber
* def replacer = read('replacer.js')

# this 'base GraphQL query' would also likely be read from a file in real-life
* def query = 'query q { company { taxAgencies { edges { node { id, name } } } } }'

# the next line is where the criteria is injected using the regex function
* def query = call replacer { query: '#(query)', field: 'taxAgencies', criteria: 'first: 5' }

# and here is the result of the 'replace'
* assert query == 'query q { company { taxAgencies(first: 5) { edges { node { id, name } } } } }'

Given request { query: '#(query)' }
And header Accept = 'application/json'
When method post
Then status 200

* def agencies = $.data.company.taxAgencies.edges
* match agencies[0].node == { id: '#uuid', name: 'John Smith' }
```
## Multi-line Comments
### How do I 'block-comment' multiple lines ?
One limitation of the Cucumber / Gherkin format is the lack of a way to denote 
multi-line comments.  This can be a pain during development when you want to comment out
whole blocks of script.  Fortunately there is a reasonable workaround for this.

Of course, if your [IDE supports the Gherkin / Cucumber format](https://github.com/cucumber/cucumber-jvm/wiki/IDE-support),
nothing like it. But since Gherkin comments look exactly like comments in `*.properties` files, all you need
to do is tell your IDE that `*.feature` files should be treated as `*.properties` files.

And once that is done, if you hit CTRL + '/' (or Command + '/') with multiple
lines selected - you can block-comment or un-comment them all in one-shot.

## Cucumber Tags
Cucumber has a great way to sprinkle meta-data into test-scripts - which gives you some
interesting options when running tests in bulk.  The most common use-case would be to
partition your tests into 'smoke', 'regression' and the like - which enables being 
able to selectively execute a sub-set of tests.

The documentation on how to run tests via the [command line](#command-line) has an example of how to use tags
to decide which tests to *not* run (or ignore). The [Cucumber wiki](https://github.com/cucumber/cucumber/wiki/Tags) 
has more information on tags.

## Dynamic Port Numbers
In situations where you start an (embedded) application server as part of the test set-up phase, a typical
challenge is that the HTTP port may be determined at run-time. So how can you get this value injected
into the Karate configuration ?

It so happens that the [`karate`](#the-karate-object) object has a field called `properties` 
which can read a Java system-property by name like this: `properties['myName']`. Since the `karate` object is injected
within [`karate-config.js`](#configuration) on start-up, it is a simple and effective way for other 
processes within the same JVM to pass configuration values into Karate at run-time.

You can look at the [Wiremock](http://wiremock.org) based unit-test code of Karate to see how this can be done.
* [HelloWorldTest.java](karate-junit4/src/test/java/com/intuit/karate/junit4/wiremock/HelloWorldTest.java#L30) - see line #30
* [karate-config.js](karate-junit4/src/test/java/karate-config.js#L10) - see line #10
* [hello-world.feature](karate-junit4/src/test/java/com/intuit/karate/junit4/wiremock/hello-world.feature#L6) - see line #6

The [Karate Demos](karate-demo) use a similar approach for determining the URL for each test.

## Java API
It should be clear now that Karate provides a super-simple way to make HTTP requests compared to how you would have done so in Java. It is also possible to invoke a feature file via a Java API which can be very useful in some test-automation situations.

A common use case is to mix API-calls into a larger test-suite, for example a Selenium or WebDriver UI test. So you can use Karate to set-up data via API calls, then run the UI test-automation, and finally again use Karate to assert that the system-state is as expected.

There are two static methods in `com.intuit.karate.cucumber.CucumberRunner` (`runFeature()` and `runClasspathFeature()`) which are best explained in this demo unit-test: [`JavaApiTest.java`](karate-demo/src/test/java/demo/java/JavaApiTest.java). 

You can optionally pass in variable values or over-ride config via a `HashMap` or leave the last argument as `null`. The variable state after feature execution would be returned as a `HashMap`.

## Data Driven Tests
### The Cucumber Way
Cucumber has a concept of [Scenario Outlines](https://github.com/cucumber/cucumber/wiki/Scenario-Outlines) where you can re-use a set of data-driven steps and assertions, and the data can be declared in a very user-friendly fashion. Observe the usage of `Scenario Outline:` instead of `Scenario:`, and the new `Examples:` section.

You should take a minute to compare this with the [exact same example implemented in REST-assured and TestNG](https://github.com/basdijkstra/workshops/blob/466e6842cd2438b416888a79e6e0bc9a9bf6395f/rest-assured/RestAssuredWorkshop/src/test/java/com/ontestautomation/restassured/workshop/answers/RestAssuredAnswers2.java).

```cucumber
Feature: karate answers 2

Background:
* url 'http://localhost:8080'

Scenario Outline: given circuit name, validate country

Given path 'api/f1/circuits/<name>.json'
When method get
Then match $.MRData.CircuitTable.Circuits[0].Location.country == '<country>'

Examples:
| name   | country  |
| monza  | Italy    |
| spa    | Belgium  |
| sepang | Malaysia |

Scenario Outline: given race number, validate number of pitstops for Max Verstappen in 2015

Given path 'api/f1/2015/<race>/drivers/max_verstappen/pitstops.json'
When method get
Then assert response.MRData.RaceTable.Races[0].PitStops.length == <stops>

Examples:
| race | stops |
| 1    | 1     |
| 2    | 3     |
| 3    | 2     |
| 4    | 2     |
```
This is great for testing boundary conditions against a single end-point, with the added bonus that
your test becomes even more readable. This approach can certainly enable product-owners or domain-experts 
who are not programmer-folk, to review, and even collaborate on test-scenarios and scripts.

### The Karate Way
The limitation of the Cucumber `Scenario Outline:` is that the number of rows in the `Examples:` is fixed. But take a look at how Karate can [loop over a `*.feature` file](#data-driven-features) for each object in a JSON array - which gives you dynamic data-driven testing, if you need it.

