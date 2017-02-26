# Karate
> Web-Services Testing Made **Simple**.

Karate enables you to script a sequence of calls to any kind of web-service and assert
that the responses are as expected.  It makes it really easy to build complex request 
payloads, traverse data within the responses, and chain data from responses into the next request. 
Karate's payload validation engine can perform a 'smart compare' of two JSON or XML documents 
without being affected by white-space or the order in which data-elements actually appear, and you 
can opt to ignore fields that you choose.

Since Karate is built on top of
[Cucumber-JVM](https://github.com/cucumber/cucumber-jvm), you can run tests and
generate reports like any standard Java project. But instead of Java - you write 
tests in a language designed to make dealing with HTTP, JSON or XML - **simple**.

## Hello World
```cucumber
Feature: simple example of a karate test
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
It is worth pointing out that JSON is a 'first class citizen' of the syntax such that you can 
express payload and expected data without having to use double-quotes and without having to enclose JSON field names
in quotes.  There is no need to 'escape' characters like you would have had to in Java.

And you don't need to create Java objects (or POJO-s) for any of the payloads that you need to work with.

# Index 

 | | | | | 
----- | ---- | ---- | --- | ---
**Getting Started** | [Maven / Quickstart](#maven) | [Folder Structure](#recommended-folder-structure) | [File Extension](#file-extension) | [JUnit](#running-with-junit) / [TestNG](#running-with-testng)
 | [Cucumber Options](#cucumber-options) | [Command Line](#command-line) | [Logging](#logging) | [Configuration](#configuration)
 | [Environment Switching](#switching-the-environment) | [Script Structure](#script-structure) | [Given-When-Then](#given-when-then) | [Cucumber vs Karate](#cucumber-vs-karate)
**Variables & Expressions** | [`def`](#def) | [`assert`](#assert) | [`print`](#print) | [Multi-line](#multi-line-expressions)
**Data Types** | [JSON](#json) | [XML](#xml) | [JS Functions](#javascript-functions) | [Reading Files](#reading-files) 
**Primary HTTP Keywords** | [`url`](#url) | [`path`](#path) | [`request`](#request) | [`method`](#method) 
 | [`status`](#status) | [`multipart post`](#multipart-post) | [`soap action`](#soap-action) | [`configure`](#configure)
**Secondary HTTP Keywords** | [`param`](#param) | [`header`](#header) | [`cookie`](#cookie)
 | [`form field`](#form-field) | [`multipart field`](#multipart-field) | [`multipart entity`](#multipart-entity)
**Set, Match, Assert** | [`set`](#set) / [`match ==`](#match) | [`match contains`](#match-contains) | [`match contains only`](#match-contains-only)| [`match each`](#match-each)
**Special Variables** | [`response`](#response) | [`cookies`](#cookies) | [`read`](#read)
 | [`responseHeaders`](#responseheaders) | [`responseStatus`](#responsestatus) | [`responseTime`](#responsetime)
 **Reusable Functions** | [`call`](#call) | [`karate` object](#the-karate-object)
 **Tips and Tricks** | [Embedded Expressions](#embedded-expressions) | [GraphQL RegEx Example](#graphql--regex-replacement-example) | [Multi-line Comments](#multi-line-comments) | [Cucumber Tags](#cucumber-tags)
 | [Data Driven Tests](#data-driven-tests) | [Auth](#sign-in-example) / [Headers](#http-basic-authentication-example) | [Dynamic Port Numbers](#dynamic-port-numbers) | [Ignore / Vallidate](#ignore-or-validate)

# Features
* Java knowledge is not required to write tests
* Scripts are plain-text files and require no compilation step or IDE
* Based on the popular Cucumber / Gherkin standard, and IDE support and syntax-coloring options exist
* Syntax 'natively' supports JSON and XML - including [JsonPath](https://github.com/jayway/JsonPath) and [XPath](https://www.w3.org/TR/xpath/) expressions
* Express expected results as well-formed JSON or XML, and assert that the entire response payload (no matter how complex or deeply nested) is as expected
* Embedded JavaScript engine that enables you to build a library of re-usable functions that suit your specific environment
* Re-use of payload-data and user-defined functions across tests is so easy - that it becomes a natural habit for the test-developer
* Built-in support for switching configuration across different environments (e.g. dev, QA, pre-prod)
* Support for data-driven tests and being able to tag (or group) tests is built-in, no need to rely on TestNG or JUnit
* Seamless integration into existing Java projects as both JUnit and TestNG are supported
* Easily invoke JDK classes, Java libraries or re-use custom Java code if needed for ultimate extensibility
* Simple plug-in system for authentication and HTTP header management that will handle any complex real-world scenario
* Comprehensive support for different flavors of HTTP calls:
  * SOAP / XML requests
  * HTTPS / SSL - without needing certificates, key-stores or trust-stores
  * Use an HTTP proxy server if required
  * URL-encoded HTML-form data
  * Multi-part file-upload - including 'multipart/mixed' and 'multipart/related'
  * Browser-like cookie handling
  * Full control over HTTP headers, path and query parameters
  * Intelligent defaults

## Hello Real World
Here below is a slightly more involved example, which can serve as a basic cheat-sheet for you to 
get started.  This actually demonstrates a whole bunch of capabilities - handling [ssl](#configure), [cookies](#cookie), 
[headers](#configure-headers), [time-outs](#configure), [multipart](#multipart-field) file-uploads, html-[forms](#form-field), XML [responses](#response), 
custom [functions](#javascript-functions), calling [Java](#calling-java) code, reading 
test-data [files](#reading-files), using [environment variables](#configuration), and asserting for 
expected results using [`match`](#match).

And still ends up being **very** concise. Keep in mind that there are almost as many comments as actual 
lines of script here below.

```cucumber
Feature: a more real-world example of a karate test-case that does a bunch of stuff

Background:
# import re-usable code that manipulates http headers for each request
# even though the BDD 'Given-When-Then' convention is encouraged, you can use '*'
* configure headers = read('classpath:my-headers.js')

# enable SSL (and there is no need for a certificate or keystore)
* configure ssl = true

# configure time out for the http client (milliseconds), it defaults to 0 (infinite)
* configure readTimeout = 10000

# invoke re-usable code that performs custom authentication
* def signIn = read('classpath:my-signin.js')
* def ticket = call signIn { username: 'john@smith.com', password: 'secret1234' }

# create a javascript function on-the-fly that can re-use JVM code
* def time = function() { return java.lang.System.currentTimeMillis() }

# since this is also in the 'Background' section it applies to all Scenario-s in this file
# and the url here comes from the global config
* url documentsBaseUrl

Scenario: create session, upload and download document

# call the 'sessions' end-point with a cookie and a query-param set
Given path 'sessions'
And cookie my.authId = ticket.userId
And param someParam = 'someValue'
When method get
Then status 200

# save response to a variable.  '$' is shorthand for 'response'
Given def session = $

# assert that the expected response payload was received
Then match session == { issued: '#ignore', token: '#ignore', userId: '#(ticket.userId)' }

# for complex payloads, you can also separate them out into separate files
And match session == read('expected-session-response.json')

# multipart upload
Given path 'documents/upload'
And multipart field file = read('test.pdf')
# the custom function is used here
And multipart field fileTime = time() + ''
And header Accept = 'application/xml'
When multipart post
Then status 200

# the response happens to be XML here, so we use XPath
# the leading '/' is short-hand for 'response/'
* def documentId = /RootElement/EntityId

# path can also take comma delimited values to save you from string + '/' concatenation
Given path 'documents', 'download', documentId
When method get
Then status 200
And assert responseTime < 1000

# the 'match' syntax auto-converts types, and even binary file contents can be compared
And match response == read('test.pdf')
```

# Getting Started
Karate requires [Java](http://www.oracle.com/technetwork/java/javase/downloads/index.html) 8
and [Maven](http://maven.apache.org) to be installed.

## Maven

This is all that you need within your `<dependencies>`:

```xml
<dependency>
    <groupId>com.intuit.karate</groupId>
    <artifactId>karate-junit4</artifactId>
    <version>0.2.0</version>
    <scope>test</scope>
</dependency>
```
### TestNG instead of JUnit
If you want to use TestNG, use the artifactId `karate-testng`. If you are starting a project from scratch,
we strongly recommend that you use JUnit. Do note that [data-driven](#data-driven-tests) testing and 
[tag-groups](#cucumber-tags) are built-in to Karate, so you don't need to depend on things like the TestNG 
[`@DataProvider`](http://testng.org/doc/documentation-main.html#parameters-dataproviders) anymore.

Use the TestNG test-runner only when you are trying to add Karate tests side-by-side with an existing set of
TestNG test-classes, possibly as a migration strategy.

### Quickstart
It may be easier for you to use the Karate Maven archetype to create a skeleton project with one command.
You can then skip the next few sections, as the `pom.xml`, recommended directory structure and starter files
would be created for you.

You can replace the values of 'com.mycompany' and 'myproject' as per your needs.

```
mvn archetype:generate \
-DarchetypeGroupId=com.intuit.karate \
-DarchetypeArtifactId=karate-archetype \
-DarchetypeVersion=0.2.0 \
-DgroupId=com.mycompany \
-DartifactId=myproject
```

This will create a folder called 'myproject' (or whatever you set the name to).

## Recommended Folder Structure
The Maven tradition is to have non-Java source files in a separate 'src/test/resources'
folder structure - but we recommend that you keep them side-by-side with your `*.java` files.
When you have a large and complex project, you will end up with a few data files (e.g. `*.js`, `*.json`, `*.txt`)
as well and it is much more convenient to see the `*.java` and `*.feature` files and all
related artifacts in the same place.

This can be easily achieved with the following tweak to your maven 'build' section.
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
This is very common in the world of Maven users and keep in mind that these are tests 
and not production code.  

With the above in place, you don't have to keep switching between your 'src/test/java' 
and 'src/test/resources' folders, you can have all your test-code and artifacts under 
'src/test/java' and everything will work as expected.

Once you get used to this, you may even start wondering why projects need a 'src/test/resources'
folder at all !

### File Extension
A Karate test script has the file extension `.feature` which is the standard followed by
Cucumber.  You are free to organize your files using regular Java package conventions.

But since these are tests and not production Java code, you don't need to be bound by the 
'com.mycompany.foo.bar' convention and the un-necessary explosion of sub-folders that ensues. 
We suggest that you have a folder hierarchy only one or two levels deep - where the folder 
names clearly identify which 'resource', 'entity' or API is the web-service under test.

For example:
```
src/test/java
    |
    +-- karate-config.js
    +-- logback-test.xml
    +-- some-classpath-function.js
    +-- some-classpath-payload.json
    |
    \-- animals
        |
        +-- cats
        |   |
        |   +-- cats-post.feature
        |   +-- cats-get.feature
        |   +-- cat.json
        |   \-- CatsTest.java
        |
        \-- dogs
            |
            +-- dog-crud.feature
            +-- dog.json
            +-- some-helper-function.js
            \-- DogsTest.java
```
For details on what actually goes into a script or `*.feature` file, refer to the
[syntax guide](#syntax-guide).

## Running With JUnit
To run a script `*.feature` file from your Java IDE, you just need the following empty test-class in the same package.
The name of the class doesn't matter, and it will automatically run any `*.feature` file in the same package.
This comes in useful because depending on how you organize your files and folders - you can have 
multiple feature files executed by a single JUnit test-class.
```java
package animals.cats;

import com.intuit.karate.junit4.Karate;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
public class CatsTest {
	
}
```
Refer to your IDE documentation for how to run a JUnit class.  Typically right-clicking on the file in the
project browser or even within the editor view would bring up the "Run as JUnit Test" menu option.

> Karate will traverse sub-directories and look for `*.feature` files. For example if you have the JUnit class
in the `com.mycompany` package, `*.feature` files in `com.mycompany.foo` and `com.mycompany.bar` will also be
run.

## Running With TestNG
You extend a class from the [`karate-testng`](#maven) Maven artifact like so. All other behavior
is the same as if using JUnit.

```java
package animals.cats;

import com.intuit.karate.testng.KarateTest;

public class CatsTest extends KarateTest {
    
}
```

## Cucumber Options
You normally don't need to - but if you want to run only a specific feature file 
from a JUnit test even if there are multiple *.feature files in the same folder,
you could use the [`@CucumberOptions`](https://cucumber.io/docs/reference/jvm#configuration) annotation.

```java
package animals.cats;

import com.intuit.karate.junit4.Karate;
import cucumber.api.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@CucumberOptions(features = "classpath:animals/cats/cats-post.feature")
public class CatsPostTest {
	
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
It is possible to run tests from the command-line as well.  Refer to the 
[Cucumber documentation](https://cucumber.io/docs/reference/jvm) for more, including
how to enable other report output formats such as HTML. For example, if you wanted to generate
a report in JUnit XML format:
```
mvn test -Dcucumber.options="--plugin junit:target/cucumber-junit.xml"
```
Or in HTML format:
```
mvn test -Dcucumber.options="--plugin html:target/cucumber-html"
```

A problem you may run into is that the report is generated for every JUnit class with the
`@RunWith(Karate.class)` annotation. So if you have multiple JUnit classes involved in a 
test-run, you will end up with only the report for the last class as it would have over-written
everything else. There are a couple of solutions, one is to use
[JUnit suites](https://github.com/junit-team/junit4/wiki/Aggregating-tests-in-suites) -
but the simplest should be to have a JUnit class (with the Karate annotation) at the 'root' 
of your test packages (`src/test/java`, no package name). With that in position, you can do this:

```
mvn test -Dcucumber.options="--plugin junit:target/cucumber-junit.xml --tags ~@ignore" -Dtest=TestAll
```
Here, `TestAll` is the name of the Java class you designated to run all your tests. And yes, Cucumber
has a neat way to [tag your tests](#cucumber-tags) and the above example demonstrates how to 
run all tests _except_ the ones tagged `@ignore`.

Also refer to the section on [switching the environment](#switching-the-environment) for more ways
of running tests via Maven using the command-line.

## Logging
> This is optional, and Karate will work without the logging config in place, but the default
console logging may be too verbose for your needs.

Karate uses [LOGBack](http://logback.qos.ch) which looks for a file called logback-test.xml 
on the classpath.  If you use the Maven test-resources setup described earlier (recommended), 
keep this file in 'src/test/java', or else it should go into 'src/test/resources'.  

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
You can change the 'com.intuit' logger level to 'INFO' to reduce the amount of logging.  
When the level is 'DEBUG' the entire request and response payloads are logged.

# Configuration
> You can skip this section and jump straight to the [Syntax Guide](#syntax-guide) 
if you are in a hurry to get started with Karate. Things will work even if the `karate-config.js`
file is not present.

The only 'rule' is that on start-up Karate expects a file called `karate-config.js` 
to exist on the classpath and contain a JavaScript function.  Karate will invoke this
function and from that point onwards, you are free to set config properties in a variety 
of ways.  One possible method is shown below, based on reading a Java system property.

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
  }
  if (env == 'stage') {
    // over-ride only those that need to be
    config.someUrlBase: 'https://stage-host/v1/auth';
  } else if (env == 'e2e') {
    config.someUrlBase: 'https://e2e-host/v1/auth';
  }
  return config;
}
```
The function is expected to return a JSON object and all keys and values in that JSON
object will be made available as script variables.  And that's all there is to Karate
configuration.

> The `karate` object has a few helper methods described in detail later in this document
where the [`call`](#call) keyword is explained.  Here above, you see `karate.log()` and `karate.env`
being used.

This decision to use JavaScript for config is influenced by years of experience with the set-up of 
complicated test-suites and fighting with
[Maven profiles](http://maven.apache.org/guides/introduction/introduction-to-profiles.html), 
[Maven resource-filtering](https://maven.apache.org/plugins/maven-resources-plugin/examples/filter.html) 
and the XML-soup that somehow gets summoned by the
[Maven AntRun plugin](http://maven.apache.org/plugins/maven-antrun-plugin/usage.html).

Karate's approach frees you from Maven, is far more expressive, allows you to eyeball 
all environments in one place, and is still a plain-text file.  If you want, you could even
create nested chunks of JSON that 'name-space' your config variables.

This approach is indeed slightly more complicated than traditional `*.properties` files - but you
_need_ this complexity. Keep in mind that these are tests (not production code) and this config
is going to be maintained more by the dev or QE team instead of the 'ops' or operations team.

And there is no more worrying about Maven profiles and whether the 'right' `*.properties` file has been
copied to the proper place.

## Switching the Environment
There is only one thing you need to do to switch the environment - 
which is to set a Java system property.

The recipe for doing this when running Maven from the command line is:
```
mvn test -DargLine="-Dkarate.env=e2e"
```
You can refer to the documentation of the
[Maven Surefire Plugin](http://maven.apache.org/plugins-archives/maven-surefire-plugin-2.12.4/examples/system-properties.html)
for alternate ways of achieving this, but the `argLine` approach is the simplest and should
be more than sufficient for your Continuous Integration or test-automation needs.

Here's a reminder that running any [single JUnit test via Maven](https://maven.apache.org/surefire/maven-surefire-plugin/examples/single-test.html)
can be done by:
```
mvn test -Dtest=CatsTest
```
Where `CatsTest` is the JUnit class name (in any package) you wish to run.

Karate is flexible, you can easily over-write config variables within each individual test-script -
which is very convenient when in dev-mode or rapid-prototyping.

Just for illustrative purposes, you could 'hard-code' the `karate.env` for a specific JUnit test 
like this. But don't get into the habit of doing this during development though - because if you
forget to remove it, bad things would happen.
```java
package animals.cats;

import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
public class CatsTest {   
    
    @BeforeClass
    public static void before() {
        System.setProperty("karate.env", "pre-prod");
    }

}
```

# Syntax Guide
## Script Structure
Karate scripts are technically in '[Gherkin](https://github.com/cucumber/cucumber/wiki/Gherkin)' 
format - but all you need to grok as someone who needs to test web-services 
are the three sections: `Feature`, `Background` and `Scenario`.  There can be multiple Scenario-s 
in a `*.feature` file.  

Lines that start with a '#' are comments.
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
The business of web-services testing requires access to low-level aspects such as
HTTP headers, URL-paths, query-parameters, complex JSON or XML payloads and response-codes.
And Karate gives you control over these aspects with the small set of keywords focused on HTTP such as `url`, 
`path`, `param`, etc.

Karate does not attempt to have tests be in "natural language" like how Cucumber tests are traditionally expected
to be. That said, the syntax is very concise, and the convention of every step having to start with either 
`Given`, `And`, `When` or `Then`, makes things very readable. You end up with a decent approximation of BDD even
though web-services by nature are "headless", without a UI, and not really human-friendly.

#### Cucumber vs Karate
If you are familiar with Cucumber (JVM), you may be wondering if you need to write 
[step-definitions](https://cucumber.io/docs/reference/jvm#step-definitions). The answer is **no**.

Karate's approach is that all the step-definitions you need in order to work with HTTP, JSON and XML
have been already implemented. And since you can easily extend Karate [using JavaScript](#call), there is
no need to compile Java code any more.

 -     | Cucumber | Karate
------ | -------- | ------
**More Step Definitions Needed** | **Yes**. You need to keep implementing them as your functionality grows. [This can get very tedious](https://angiejones.tech/rest-assured-with-cucumber-using-bdd-for-web-services-automation#comment-40). | **No**.
**Layers of Code to Maintain** | **2** Layers. One is the Gherkin custom grammar for your business domain, and you will also have the corresponding Java step-definitions. | **1** Layer. Just Karate-script, and no Java code needs to be implemented.
**Readable Specification** | **Yes**. Cucumber will read like natural language _if_ you implement the step-definitions right. | **No**. Although Karate is simple, and a [true DSL](https://ayende.com/blog/2984/dsl-vs-fluent-interface-compare-contrast), it is ultimately a mini-programming language. But it is perfect for testing web-services at the level of HTTP requests and responses.
**BDD Syntax** | **Yes** | **Yes**

One nice thing about the design of the underlying Cucumber framework is that
script-steps are treated the same no matter whether they start with the keyword 
`Given`, `And`, `When` or `Then`.  What this means is that you are free to use 
whatever makes sense for you.  You could even have all the steps start with `When` 
and Karate won't care.

In fact [Cucumber supports `*`](https://www.relishapp.com/cucumber/cucumber/docs/gherkin/using-star-notation-instead-of-given-when-then)
instead of forcing you to use `Given`, `When` or `Then`. This is perfect for those
cases where it really doesn't make sense - for example the [`Background`](#script-structure)
section or when you use the [`def`](#def) or [`set`](#set) syntax. When eyeballing a test-script,
think of the `*` as a 'bullet-point'.

You can read more about the Given-When-Then convention at the
[Cucumber reference documentation](https://cucumber.io/docs/reference).

Since Karate is based on Cucumber, you can also employ [data-driven](#data-driven-tests)
techniques such as expressing data-tables in test scripts.

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
Note that `def` will over-write any variable that was using the same name earlier.
Keep in mind that the start-up [configuration routine](#configuration) could have already
initialized some variables before the script even started.

## `assert`
### Assert if an expression evaluates to `true`
Once defined, you can refer to a variable by name. Expressions are evaluated using the embedded 
JavaScript engine. The assert keyword can be used to assert that an expression returns a boolean value.

```cucumber
Given def color = 'red '
And def num = 5
Then assert color + num == 'red 5'
```
Everything to the right of the `assert` keyword will be evaluated as a single expression.

Something worth mentioning here is that you would hardly need to use `assert` in your test scripts.
Instead you would typically use the [`match`](#match) keyword, that is designed for performing 
powerful assertions against JSON and XML response payloads.

## `print`
### Log to the console
You can use `print` to log variables to the console in the middle of a script.
All of the text to the right of the `print` keyword will be evaluated as a single expression
(somewhat like [`assert`](#assert)).
```cucumber
* print 'the value of a is ' + a
```

# 'Native' data types
Native data types mean that you can insert them into a script without having to worry about
enclosing them in strings and then having to 'escape' double-quotes all over the place.
They seamlessly fit 'in-line' within your test script.

## JSON
Note that the parser is 'lenient' so that you don't have to enclose all keys in double-quotes.
```cucumber
* def cat = { name: 'Billie', scores: [2, 5] }
* assert cat.scores[1] == 5
```
When inspecting JSON (or XML) for expected values you are probably better off 
using [`match`](#match) instead of `assert`.

## XML
```cucumber
Given def cat = <cat><name>Billie</name><scores><score>2</score><score>5</score></scores></cat>
# sadly, xpath list indexes start from 1
Then match cat/cat/scores/score[2] == '5'
# but karate allows you to traverse xml like json !!
Then match cat.cat.scores.score[1] == 5
```

### Embedded Expressions
In the '[Hello Real World](#hello-real-world)' example, you may have noticed a short-cut 
hidden in the value of the 'userId' field:
```cucumber
And match session == { issued: '#ignore', token: '#ignore', userId: '#(ticket.userId)' }
```
So the rule is - if a string value within a JSON (or XML) object declaration is enclosed
between `#(` and `)` - it will be evaluated as a JavaScript expression. And any variables which are
alive in the context can be used in this expression.

This comes in useful in some cases - and avoids needing to use JavaScript functions or 
[JsonPath](https://github.com/jayway/JsonPath#path-examples) expressions to [manipulate JSON](#set).  
So you get the best of both worlds: the elegance of JSON to express complex nested data - while at 
the same time being able to dynamically plug values (that could be also JSON trees) into a JSON 'template'.

The [GraphQL / RegEx Replacement example](#graphql--regex-replacement-example) also demonstrates the usage
of 'embedded expressions', look for: `'#(query)'`.

### Multi-Line Expressions
The keywords [`def`](#def), [`set`](#set), [`match`](#match) and [`request`](#request) take multi-line input as
the last argument. This is useful when you want to express a one-off lengthy snippet of text in-line,
without having to split it out into a separate [file](#reading-files). Here are some examples:
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

### JavaScript Functions
JavaScript Functions are also 'native'. And yes, functions can take arguments.  
Standard JavaScript syntax rules apply.

> [ES6 arrow functions](https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Functions/Arrow_functions) 
are **not** supported.

```cucumber
* def greeter = function(name) { return 'hello ' + name }
* assert greeter('Bob') == 'hello Bob'
```
### Java Interop
For more complex functions you are better off using the multi-line 'doc-string' approach.
This example actually calls into existing Java code, and being able to do this opens up a 
whole lot of possibilities. The JavaScript interpreter will try to convert types across 
Java and JavaScript as smartly as possible. For e.g. JS objects become Java Map-s, and 
Java Bean properties are accessible (and update-able) using dot notation e.g. '`object.name`'
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
If you want to do advanced stuff such as make HTTP requests within a function - 
that is what the [`call`](#call) keyword is for.

[More examples](#calling-java) of calling Java appear later on in this document.

## Reading Files
This actually is a good example of how you could extend Karate with custom functions.
The variable `read` is a JavaScript function that is automatically available when Karate starts.
It takes the name of a file as the only argument.

By default, the file is expected to be in the same folder (package) as the `*.feature` file.
But you can prefix the name with `classpath:`.  Prefer `classpath:` when a file is expected
to be heavily re-used all across your project.  And yes, relative paths will work.

```cucumber
# json
* def someJson = read('some-json.json')
* def moreJson = read('classpath:more-json.json')

# xml
* def someXml = read('my-xml.xml')

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
If a file does not end in '.json', '.xml', '.js' or '.txt' - it is treated as a stream 
which is typically what you would need for [`multipart`](#multipart-field) file uploads.
```cucumber
* def someStream = read('some-pdf.pdf')
```

# Core Keywords
They are `url`, `path`, `request`, `method` and `status`.

These are essential HTTP operations, they focus on setting one (non-keyed) value at a time and 
don't involve any '=' signs in the syntax.
## `url`
```cucumber
Given url 'https://myhost.com/v1/cats'
```
A URL remains constant until you use the `url` keyword again, so this is a good place to set-up 
the 'non-changing' parts of your REST URL-s.

A URL can take expressions, so the approach below is legal.  And yes, variables
can come from global [config](#configuration).
```cucumber
Given url 'https://' + e2eHostName + '/v1/api'
```
## `path`
REST-style path parameters.  Can be expressions that will be evaluated.  Comma delimited values are 
supported which can be more convenient, and takes care of URL-encoding and appending '/' where needed.
```cucumber
Given path 'documents/' + documentId + '/download'

# this is equivalent to the above
Given path 'documents', documentId, 'download'

# or you can do the same on multiple lines if you wish
Given path 'documents'
And path documentId
And path 'download'
```
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
Defining the `request` is mandatory if you are using an HTTP `method` that expects a body such as
`post`. You can always specify an empty body as follows, and force the right `Content-Type` header
by using the [`header`](#header) keyword.
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
It is worth internalizing that during test-execution, it is upon the `method` keyword 
that the actual HTTP request is issued.  Which suggests that the step should be in the `When`
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

The syntax will include a '=' sign between the key and the value.  The key does not need to
be within quotes.
## `param` 
Setting query-string parameters:
```cucumber
Given param someKey = 'hello'
And param anotherKey = someVariable
```
## `header`
You can even use functions or expressions:
```cucumber
Given header Authorization = myAuthFunction()
And header transaction-id = 'test-' + myIdString
```
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
## `cookie`
Setting a cookie:
```cucumber
Given cookie foo = 'bar'
```
## `form field` 
These would be URL-encoded when the HTTP request is submitted (by the [`method`](#method) step).
```cucumber
Given form field foo = 'bar'
```
## `multipart field`
Use this for building multipart named (form) field requests.  The submit has to be issued with 
[`multipart post`](#multipart-post).

 ```cucumber
Given multipart field file = read('test.pdf')
And multipart field fileName = 'custom-name.pdf'
```

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
When multipart post 
Then status 201
```

# Multipart and SOAP
## `multipart post`
Since a multipart request needs special handling, this is a rare case where the
[`method`](#method) step is not used to actually fire the request to the server.  The only other
exception is `soap action` (see below).

 ```cucumber
When multipart post
 ```
The `multipart post` step will default to setting the `Content-Type` header as `multipart/form-data`.
You can over-ride it by using the [`header`](#header) keyword before this step.  Look at
[`multipart entity`](#multipart-entity) for an example.

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

# Managing Headers, SSL, Timeouts and HTTP Proxy
## `configure`
You can adjust configuration settings for the HTTP client used by Karate using this keyword. The syntax is
similar to [`def`](#def) but instead of a named variable, you update configuration. Here are the 
configuration keys supported:

 Key | Type | Description
------ | ---- | ---------
`headers` | JavaScript Function | see [`configure headers`](#configure-headers)
`ssl` | boolean | Enable HTTPS calls without needing to configure a trusted certificate or key-store.
`ssl` | string | Like above, but force the SSL algorithm to one of [these values](http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#SSLContext). (The above defaults to `TLS` if set to `true`).
`connectTimeout` | integer | Set the connect timeout (milliseconds). The default is 0 (which means infinity).
`readTimeout` | integer | Set the read timeout (milliseconds). The default is 0 (which means infinity).
`proxy` | string | Set the URI of the HTTP proxy to use.
`proxy` | json | For a proxy that requires authentication, set the `uri`, `username` and `password`. (See example below).


Examples:
```cucumber
# enable ssl (and no certificate is required)
* configure ssl = true

# enable ssl and force the algorithm to TLSv1.2
* configure ssl = 'TLSv1.2'

# time-out if the response is not forthcoming within 10 seconds
* configure readTimeout = 10000

# set the uri of the http proxy server to use
* configure proxy = 'http://my.proxy.host:8080'

# proxy which needs authentication
* configure proxy = { uri: 'http://my.proxy.host:8080', username: 'john', password: 'secret' }
```

# Preparing, Manipulating and Matching Data
One of the most time-consuming parts of writing tests for web-services is traversing the 
response payload and checking for expected results and data.  You can appreciate how 
Karate makes this simple since you can express payload or expected data in JSON or XML 
'natively', either in-line or read from a file. And since you have the option of loading data 
from files for complex payloads, this has a couple of advantages - you don't have to clutter 
your test-script, and even better - you can re-use the same data in multiple scenario-s.  

Combined with the ease of setting values on and manipulating JSON or XML documents that 
Karate provides (see [`set`](#set)) - setting up test cases for boundary conditions and edge-cases 
is a simple matter of defining your payload data-object once - and then re-using it with tweaks.

Gone are the days of laboriously creating Java POJO-s or data-objects for every single JSON payload.
Even if your payloads are complex, there are plenty of ways you could acquire 
the JSON or XML that you initially need, for e.g. using WireShark or Fiddler. Even if a service is
in early development, you should expect (or demand) documentation from the dev-team (for e.g. in Swagger) 
which you could refer to.

Once you have a JSON object ready, making an HTTP request is typically accomplished using 
two or three lines of script. This solves another problem visible in many Java projects that 
depend on the Apache HTTPClient (or equivalent) - which is a proliferation of 'helper classes' 
and 'framework utilities' that evolve with every new end-point that is developed. In the long 
run this actually impedes readability and maintainability of tests, because one has to 
dig through multiple layers of code (and possibly JAR dependencies) to figure out what is 
going on. It is also worth mentioning that this kind of 'over-engineered' re-use has the side effect of
causing tests to be harder to maintain, for e.g. changes in one of the core 'helper classes'
could cause tests in completely unrelated projects to break. And having to make changes in a
'parent' project reduces the velocity of the team.

Writing tests for a new endpoint, is a lot harder in this kind of environment than it
needs to be. Not to mention the pain a Java developer will go through when needing to compare 
two objects with deeply nested fields and collections - you need null-checks everywhere -
and some fields may need to be ignored as well.

> Even worse is when the POJO-s used in the server-side implementation get 're-used' as part
of the test framework. This introduces the risk that changes to POJO-s that could break the 
end-user experience (such as adding or removing a field) will not be caught by the tests.

## `match`
### Payload Assertions / Smart Comparison
The `match` operation is smart because white-space does not matter, and the order of keys 
(or data elements) does not matter. Karate is even able to [ignore fields you choose](#ignore-or-validate) - 
which is very useful when you want to handle server-side dynamically generated fields such as 
UUID-s, time-stamps, security-tokens and the like.

The match syntax involves a double-equals sign '==' to represent a comparison
(and not an assignment '=').

Since `match` and `set` go well together, they are both introduced in 
the examples in the section below.

## `set`
### Manipulating Data
Game, `set` and `match` - Karate !

Setting values on JSON documents is simple using the `set` keyword and 
[JsonPath expressions](https://github.com/jayway/JsonPath#path-examples).

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
* match xml == <foo><hello>world</hello></foo>
```

## Ignore or Validate
When expressing expected results (in JSON or XML) you can mark some fields to be ignored when
the match (comparison) is performed.  You can even use a regular-expression so that instead of
checking for equality, Karate will just validate that the actual value conforms to the expected
pattern.

This means that even when you have dynamic server-side generated values such as UUID-s and 
time-stamps appearing in the response, you can still assert that the full-payload matched 
in one step.

```cucumber
* def cat = { name: 'Billie', type: 'LOL', id: 'a9f7a56b-8d5c-455c-9d13-808461d17b91' }
* match cat == { name: '#ignore', type: '#regex[A-Z]{3}', id: '#uuid' }
# this will fail
# * match cat == { name: '#ignore', type: '#regex.{2}', id: '#uuid' }	
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
#regexSTR | Expects actual (string) value to match the regular-expression 'STR' (see examples above)
#?EXPR | Expects the JavaScript expression 'EXPR' to evaluate to true (see examples below)

### 'Self' Validation Expressions
The special 'predicate' marker in the last row of the table above is an interesting one.  It is best
explained via examples.

Observe how the value of the field being validated (or 'self') is injected into
the 'underscore' expression variable: '`_`'
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

And functions work as well ! You can imagine how you could evolve a nice set of utilities that 
validate all your domain objects.
```cucumber
* def date = { month: 3 }
* def isValidMonth = function(m) { return m >= 0 && m <= 12 }
* match date == { month: '#? isValidMonth(_)' }
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

Checking if a string is contained within another string is a very common need and 
[`match` (name) `contains`](#match-contains) works just like you'd expect:
```cucumber
* def hello = 'Hello World!'
* match hello contains 'World'
```

### `match header`
Since asserting against header values in the response is a common task - `match header`
has a special meaning.  It short-cuts to the pre-defined variable [`responseHeaders`](#responseheaders) and
reduces some complexity - because strictly, HTTP headers are a 'multi-valued map' or a 
'map of lists' - the Java-speak equivalent being `Map<String, List<String>>`.
```cucumber
# so after a http request
Then match header Content-Type == 'application/json'
# 'contains' works as well
Then match header Content-Type contains 'application'
```  
Note the extra convenience where you don't have to enclose the LHS key in quotes.

You can always directly access the variable called [`responseHeaders`](#responseheaders)
if you wanted to do more checks, but you typically won't need to.

## Matching Sub-Sets of JSON Keys and Arrays
### `match contains`
#### JSON Keys
In some cases where the response JSON is wildly dynamic, you may want to only check for the existence of
some keys. And `match` (name) `contains` is how you can do so:
```cucumber
* def foo = { bar: 1, baz: 'hello', ban: 'world' }

* match foo contains { bar: 1 }
* match foo contains { baz: 'hello' }
* match foo contains { bar:1, baz: 'hello' }
# this will fail
# * match foo == { bar:1, baz: 'hello' }
```

#### JSON Arrays

This is a good time to deep-dive into JsonPath, which is perfect for slicing and dicing JSON into 
manageable chunks. It is worth taking a few minutes to go through the documentation and examples 
here: [JsonPath Examples](https://github.com/jayway/JsonPath#path-examples).

Here are some example assertions performed while scraping a list of child elements out of the JSON below.
Observe how you can `match` the result of a JsonPath expression with your expected data.

```cucumber
Given def cat = 
"""
{
  name: 'Billie',
  rivals: [
      { id: 23, name: 'Bob' },
      { id: 42, name: 'Wild' }
  ]
}
"""
# normal 'equality' match. note the wildcard '*' in the JsonPath (returns an array)
Then match cat.rivals[*].id == [23, 42]

# when inspecting a json array, 'contains' just checks if the expected items exist
# and the size and order of the actual array does not matter
Then match cat.rivals[*].id contains [23]
Then match cat.rivals[*].id contains [42]
Then match cat.rivals[*].id contains [23, 42]
Then match cat.rivals[*].id contains [42, 23]

# and yes, you can assert against nested objects within JSON arrays !
Then match cat.rivals[*] contains [{ id: 42, name: 'Wild' }, { id: 23, name: 'Bob' }]

# ... and even ignore fields at the same time !
Then match cat.rivals[*] contains [{ id: 42, name: '#ignore' }]
```

It is worth mentioning that to do the equivalent of the last line in Java, you would typically have to
traverse 2 Java Objects, one of which is within a list, and you would have to check for nulls as well.

When you use Karate, all your data assertions can be done in pure JSON and without needing a thick
forest of companion Java objects. And when you [`read`](#read) your JSON objects from (re-usable) files,
even complex response payload assertions can be accomplished in just a single line of Karate-script.

#### `match contains only`
For those cases where you need to assert that **all** array elements are present but in **any order**
you can do this:

```cucumber
* def data = { foo: [1, 2, 3] }
* match data.foo contains [1]
* match data.foo contains [3, 2]
* match data.foo contains only [3, 2, 1]
* match data.foo contains only [2, 3, 1]
# this will fail
# * match data.foo contains only [2, 3]
```

## Validate every element in a JSON array
### `match each`
Karate has syntax sugar that can iterate over all elements in a JSON array. Here's how it works:
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

# Special Variables

## `response`
After every HTTP call this variable is set with the response and is available until the next HTTP
request over-writes it.

The response is automatically available as a JSON, XML or String object depending on what the
response contents are.

As a short-cut, when running JsonPath expressions - '$' represents the `response`.  This
has the advantage that you can use pure [JsonPath](https://github.com/jayway/JsonPath#path-examples)
and be more concise.  For example:

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

## `cookies`
The `cookies` variable is set upon any HTTP response and is a map-like (or JSON-like) object.
It can be easily inspected or used in expressions.
```cucumber
Then assert cookies['my.key'] == 'someValue'
```
As a convenience, cookies from the previous response are collected and passed as-is as 
part of the next HTTP request.  This is what is normally expected and simulates a 
browser - which makes it easy to script things like HTML-form based authentication into test-flows.

Of course you can manipulate `cookies` or even set it to `null` if you wish - at any point
within a test script.

## `responseHeaders`
See also [`match header`](#match-header) which is what you would normally need.

But if you need to use values in the response headers - they will be in a variable 
named `responseHeaders`. Note that it is a 'map of lists' so you will need to do things 
like this:
```cucumber
* def contentType = responseHeaders['Content-Type'][0]
```
## `responseStatus`
You would normally only need to use the [`status`](#status) keyword.  But if you really need to use 
the HTTP response code in an expression or save it for later, you can get it as an integer:
```cucumber
* def uploadStatusCode = responseStatus
```
## `responseTime`
The response time (in milliseconds) for every HTTP request would be available in a variable called
`responseTime`. You can use this to assert that the response was returned within the expected time
like so:
```cucumber
When method post
Then status 201
And assert responseTime < 1000
```
## `read`
This is a great example of how you can extend Karate by defining your own functions. Behind the scenes
the `read(filename)` function is actually implemented in JavaScript.

Refer to the section on [reading files](#reading-files) for how to use this built-in function.

# HTTP Header Manipulation
## `configure headers`
Custom header manipulation for every HTTP request is something that Karate makes very easy and pluggable.
For every HTTP request made from Karate, the internal flow is as follows:
* did we [`configure`](#configure) the value of `headers` ?
* if so, is the configured value a JavaScript function ?
* if so, a [`call`](#call) is made to that function.
* did the function invocation return a map-like (or JSON) object ?
* if so, all the key-value pairs in the returned object are added to the HTTP headers.

This makes setting up of complex authentication schemes for your test-flows really easy.
It typically ends up being a one-liner that appears in the `Background` section at 
the start of your test-scripts.  You can re-use the function you create across your whole project.
 
Here is an example JavaScript function that uses some variables in the context
(which have been possibly set as the result of a sign-in) to build the `Authorization` header.

> In the example below, note the use of the [`karate`](#the-karate-object) object 
for getting the value of a variable. This is preferred because it takes care of 
situations such as if the value is 'undefined' in JavaScript.

```javascript
function() {
  var out = { // hard-coded here, but you can dynamically generate these values if needed
    txid_header: '1e2bd51d-a865-4d37-9ac9-c345dc59119b',
    ip_header: '123.45.67.89',    
  };
  var authString = '';
  var authToken = karate.get('authToken'); // use the 'karate' helper to do a 'safe' get of a variable
  if (authToken) { // and if 'authToken' is not null ... 
    authString = ',auth_type=MyAuthScheme'
        + ',auth_key=' + authToken.key
        + ',auth_user=' + authToken.userId
        + ',auth_project=' + authToken.projectId;
  }
  // the 'appId' variable here is expected to have been set via config (or a 'def' step)
  out.Authorization = 'My_Auth app_id=' + appId + authString;
  return out;
}
```
And this is how it looks like in action - at the beginning of a test script:
```cucumber
Feature: some feature

Background:
* configure headers = read('classpath:the-above-function.js')
* def signId = read('classpath:sign-in.js')
* def authToken = call signIn { username: 'john@smith.com', password: 'secret1234' }

Scenario:
# actual steps
```
For an example of what the 'sign-in.js' could look like, refer to the documentation
of [`call`](#call) and the example provided. Notice how once the `authToken` variable is initialized,
it is used by the configured `headers` function to generate headers for every HTTP call made as part 
of the test flow.

If a few steps in your flow need to temporarily change (or completely bypass) the currently-set 
header-manipulation scheme, just update the `headers` configuration value or set it to `null` in the
middle of a script.

# Advanced JavaScript Function Invocation
## `call`
This is one of the most powerful features of Karate.  With `call` you can:
* call re-usable functions that take complex data as an argument and return complex data that can be stored in a variable
* share and re-use functionality across your organization
* move sequences of 'set up' HTTP calls out of your test-scripts so that the test can fully focus on the feature being tested
* greatly reduce clutter and improve the readability of your test-scripts

A very common use-case is the inevitable 'sign-in' that you need to perform at the beginning of every test script.

### Sign-In Example

This example actually makes two HTTP requests - the first is a standard 'sign-in' POST 
and then (for illustrative purposes) another GET is made for retrieving a list of projects for the 
signed-in user, the first one is 'chosen' and added to the returned 'auth token' JSON object.

So you get the idea, any kind of complicated authentication flow can be accomplished and re-used.

```javascript
function(credentials) {
    var req = {
        url: loginUrlBase, // use config variable
        method: 'post',
        body: { userId: credentials.username, userPass: credentials.password }
    };
    var res = karate.request(req); // first HTTP call, to sign-in
    if (res.status != 200) {
        throw ('sign in failed: ' + res);
    }
    var authToken = res.body;
    karate.log('authToken:', authToken);
    // update the context, so that the 'headers' request-filter uses the auth token
    // even for the second HTTP request that happens in the next few lines 
    karate.set('authToken', authToken);
    req = {
        url: loginUrlBase + 'users/' + authToken.userId + '/projects',
        method: 'get'        
    };
    res = karate.request(req); // second HTTP call, to get a list of 'projects'
    if (res.status != 200) {
        throw ('get user projects failed: ' + res);
    }
    var projectId = res.body.projects[0].projectId; // logic to 'choose' first project
    authToken.projectId = projectId;
    return authToken;
}
```
## The `karate` object
As demonstrated in the example above, a function invoked with `call` has access to a 
special object in a variable named: `karate`.  This provides the following methods:

> TODO: right now only JSON HTTP calls are supported by `karate.request(req)`

* `karate.request(req)` - make an HTTP request. The JSON argument has the following keys:
  * `url`: URL of the HTTP call to be made
  * `method`: HTTP method, can be lower-case
  * `body`: JSON payload
* `karate.set(key, value)` - set the value of a variable immediately, which ensures that any active [`headers`](#headers) routine does the right thing for future HTTP calls (even those made by this function being `call`-ed)
* `karate.get(key)` - get the value of a variable by name, if not found - this returns `null` which is easier to handle in JavaScript (than `undefined`)
* `karate.log(... args)` - log to the same logger being used by the parent process
* `karate.env` - gets the value (read-only) of the environment setting 'karate.env' used for bootstrapping [configuration](#configuration)
* `karate.properties[key]` - get the value of any Java system-property by name, useful for advanced custom configuration

## Rules for Passing Arguments
Only one argument is allowed.  But this does not limit you in any way because you can 
pass a whole JSON object as the argument.  Which has the advantage of being easier to read.

So for the above sign-in example, this is how it can be invoked:
```cucumber
* def signIn = read('classpath:sign-in.js')
* def authToken = call signIn { username: 'john@smith.com', password: 'secret1234' }
```
Do look at the documentation and example for [`configure headers`](#configure-headers) also as it goes hand-in-hand with `call`.
In the above example, the function (`sign-in.js`) returns an object assigned to the `authToken` variable.
Take a look at how the [`configure headers`](#configure-headers) example uses the `authToken` variable.
## Return types
Naturally, only one value can be returned.  But again you can return a JSON object.
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
And here's how it works in a test-script. Note that you need to do this only once within a `Scenario:`,
perhaps at the beginning, or within the `Background:` section.
```cucumber
* header Authorization = call read('basic-auth.js') { username: 'john', password: 'secret' }

```

## Calling Java
There are examples of calling JVM classes in the '[Hello Real World](#hello-real-world)' example and
in the section on [Java Interop](#java-interop).

Calling any Java code is that easy.  Given this custom / user-defined Java class:
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

    public static String staticMethod() {
        return "fantastic";
    }    

}
```
This is how it can be called from a test-script, and yes, even static methods can be invoked:
```cucumber
* def doWork =
"""
function() {
  var JavaDemo = Java.type("com.mycompany.JavaDemo");
  var jd = new JavaDemo();
  return jd.doWork("world");  
}
"""
* def result = call doWork
* assert result.someKey == 'hello world'

# example of calling a static method
* def staticWork = 
"""
function() {
  var JavaDemo = Java.type("com.mycompany.JavaDemo");
  return JavaDemo.staticMethod()
}
"""
* def result = call staticWork
* assert result == 'fantastic'
```

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
nothing like it. But since Gherkin comments look exactly like comments in *.properties files, all you need
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
* [HelloWorldTest.java](karate-junit4/src/test/java/com/intuit/karate/junit4/wiremock/HelloWorldTest.java) - see line #30
* [karate-config.js](karate-junit4/src/test/java/karate-config.js) - see line #10
* [hello-world.feature](karate-junit4/src/test/java/com/intuit/karate/junit4/wiremock/hello-world.feature) - see line #5

## Data Driven Tests
Cucumber has a concept of [Scenario Outlines](https://github.com/cucumber/cucumber/wiki/Scenario-Outlines)
where you can re-use a set of data-driven steps and assertions, and the data can be declared in a
very user-friendly fashion. Observe the usage of `Scenario Outline:` instead of `Scenario:`, and the 
new `Examples:` section.

This example is a port of the [REST-Assured](http://rest-assured.io) tutorial by 
[@Bas Dijkstra](https://twitter.com/_basdijkstra) and you should take a minute to compare the 
below code with the [original](http://bit.ly/2kGxiU0).

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

