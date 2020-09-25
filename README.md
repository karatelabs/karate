# <img src="karate-core/src/main/resources/res/karate-logo.svg" height="60" width="60"/> Karate
## Test Automation Made `Simple.`
[![Maven Central](https://img.shields.io/maven-central/v/com.intuit.karate/karate-core.svg)](https://mvnrepository.com/artifact/com.intuit.karate/karate-core) [![Build Status](https://travis-ci.org/intuit/karate.svg?branch=master)](https://travis-ci.org/intuit/karate) [![GitHub release](https://img.shields.io/github/release/intuit/karate.svg)](https://github.com/intuit/karate/releases) [![Support Slack](https://img.shields.io/badge/support-slack-red.svg)](https://github.com/intuit/karate/wiki/Support) [![Twitter Follow](https://img.shields.io/twitter/follow/KarateDSL.svg?style=social&label=Follow)](https://twitter.com/KarateDSL)

Karate is the only open-source tool to combine API test-automation, [mocks](karate-netty), [performance-testing](karate-gatling) and even [UI automation](karate-core) into a **single**, *unified* framework. The BDD syntax popularized by Cucumber is language-neutral, and easy for even non-programmers. Powerful JSON & XML assertions are built-in, and you can run tests in parallel for speed.

Test execution and report generation feels like any standard Java project. But there's also a [stand-alone executable](karate-netty#standalone-jar) for teams not comfortable with Java. You don't have to compile code. Just write tests in a **simple**, *readable* syntax - carefully designed for HTTP, JSON, GraphQL and XML. And you can mix API and [UI test-automation](karate-core) within the same test script.

## Hello World

<a href="https://gist.github.com/ptrthomas/d5a2d9e15d0b07e4f1b46f692a599f93"><img src="karate-demo/src/test/resources/karate-hello-world.jpg" height="400" /></a>

> If you are familiar with Cucumber / Gherkin, the [*big difference*](#cucumber-vs-karate) here is that you **don't** need to write extra "glue" code or Java "step definitions" !

It is worth pointing out that JSON is a 'first class citizen' of the syntax such that you can express payload and expected data without having to use double-quotes and without having to enclose JSON field names in quotes.  There is no need to 'escape' characters like you would have had to in Java or other programming languages.

And you don't need to create additional Java classes for any of the payloads that you need to work with.

# Index

<table>
<tr>
  <th>Start</th>
  <td>
      <a href="#maven">Maven</a> 
    | <a href="#gradle">Gradle</a>
    | <a href="#quickstart">Quickstart</a>
    | <a href="https://github.com/intuit/karate/tree/master/karate-netty#standalone-jar">Standalone Executable</a>
    | <a href="#folder-structure">Naming Conventions</a>
    | <a href="#script-structure">Script Structure</a>
  </td>
</tr>
<tr>
  <th>Run</th>
  <td>
      <a href="#junit-4">JUnit 4</a>
    | <a href="#junit-5">JUnit 5</a>
    | <a href="#command-line">Command Line</a>
    | <a href="#ide-support">IDE Support</a>    
    | <a href="#tags">Tags / Grouping</a>
    | <a href="#parallel-execution">Parallel Execution</a>
    | <a href="#java-api">Java API</a>    
  </td>
</tr>
<tr>
  <th>Report</th>
  <td>
      <a href="#configuration">Configuration</a> 
    | <a href="#switching-the-environment">Environment Switching</a>
    | <a href="#test-reports">Reports</a>
    | <a href="#junit-html-report">JUnit HTML Report</a>
    | <a href="#report-verbosity">Report Verbosity</a>
    | <a href="#logging">Logging</a>
    | <a href="#log-masking">Log Masking</a>
  </td>
</tr>
<tr>
  <th>Types</th>
  <td>
      <a href="#json">JSON</a> 
    | <a href="#xml">XML</a>
    | <a href="#javascript-functions">JavaScript Functions</a>
    | <a href="#reading-files">Reading Files</a>
    | <a href="#type-conversion">Type / String Conversion</a>
    | <a href="#floats-and-integers">Floats and Integers</a>
    | <a href="#embedded-expressions">Embedded Expressions</a>
    | <a href="#jsonpath-filters">JsonPath</a>
    | <a href="#xpath-functions">XPath</a>
    | <a href="#karate-expressions">Karate Expressions</a>
  </td>
</tr>
<tr>
  <th>Variables</th>
  <td>
      <a href="#def"><code>def</code></a>
    | <a href="#text"><code>text</code></a>
    | <a href="#table"><code>table</code></a>
    | <a href="#yaml"><code>yaml</code></a>
    | <a href="#csv"><code>csv</code></a>
    | <a href="#type-string"><code>string</code></a>
    | <a href="#type-json"><code>json</code></a>
    | <a href="#type-xml"><code>xml</code></a>
    | <a href="#type-xmlstring"><code>xmlstring</code></a>
    | <a href="#type-bytes"><code>bytes</code></a>
    | <a href="#type-copy"><code>copy</code></a>
  </td>
</tr>
<tr>
  <th>Actions</th>
  <td>
      <a href="#assert"><code>assert</code></a>
    | <a href="#print"><code>print</code></a>
    | <a href="#replace"><code>replace</code></a>
    | <a href="#get"><code>get</code></a> 
    | <a href="#set"><code>set</code></a>
    | <a href="#remove"><code>remove</code></a>    
    | <a href="#configure"><code>configure</code></a>
    | <a href="#call"><code>call</code></a> 
    | <a href="#callonce"><code>callonce</code></a>
    | <a href="#eval"><code>eval</code></a>
    | <a href="#reading-files"><code>read()</code></a>
    | <a href="#the-karate-object"><code>karate</code> API</a>     
  </td>
</tr>
<tr>
  <th>HTTP</th>
  <td>
      <a href="#url"><code>url</code></a> 
    | <a href="#path"><code>path</code></a>
    | <a href="#request"><code>request</code></a>
    | <a href="#method"><code>method</code></a>
    | <a href="#status"><code>status</code></a>
    | <a href="#soap-action"><code>soap action</code></a>
    | <a href="#retry-until"><code>retry until</code></a>
  </td>
</tr>
<tr>
  <th>Request</th>
  <td>
      <a href="#param"><code>param</code></a> 
    | <a href="#header"><code>header</code></a>    
    | <a href="#cookie"><code>cookie</code></a>
    | <a href="#form-field"><code>form field</code></a>
    | <a href="#multipart-file"><code>multipart file</code></a>
    | <a href="#multipart-field"><code>multipart field</code></a>       
    | <a href="#multipart-entity"><code>multipart entity</code></a>    
    | <a href="#params"><code>params</code></a>
    | <a href="#headers"><code>headers</code></a>
    | <a href="#cookies"><code>cookies</code></a>        
    | <a href="#form-fields"><code>form fields</code></a>
    | <a href="#multipart-files"><code>multipart files</code></a>
    | <a href="#multipart-fields"><code>multipart fields</code></a>
  </td>
</tr>
<tr>
  <th>Response</th>
  <td>
      <a href="#response"><code>response</code></a>
    | <a href="#responsebytes"><code>responseBytes</code></a> 
    | <a href="#responsestatus"><code>responseStatus</code></a>
    | <a href="#responseheaders"><code>responseHeaders</code></a>
    | <a href="#responsecookies"><code>responseCookies</code></a>
    | <a href="#responsetime"><code>responseTime</code></a>
    | <a href="#responsetype"><code>responseType</code></a>
    | <a href="#requesttimestamp"><code>requestTimeStamp</code></a>
  </td>
</tr>
<tr>
  <th>Assert</th>
  <td>
      <a href="#match"><code>match ==</code></a>
    | <a href="#match--not-equals"><code>match !=</code></a>
    | <a href="#match-contains"><code>match contains</code></a>
    | <a href="#match-contains-only"><code>match contains only</code></a>
    | <a href="#match-contains-any"><code>match contains any</code></a>
    | <a href="#match-contains-deep"><code>match contains deep</code></a>    
    | <a href="#not-contains"><code>match !contains</code></a>
    | <a href="#match-each"><code>match each</code></a>
    | <a href="#match-header"><code>match header</code></a>    
    | <a href="#fuzzy-matching">Fuzzy Matching</a>
    | <a href="#schema-validation">Schema Validation</a>
    | <a href="#contains-short-cuts"><code>contains</code> short-cuts</a>
  </td>
</tr>
<tr>
  <th>Re-Use</th>
  <td>
      <a href="#calling-other-feature-files">Calling Other <code>*.feature</code> Files</a>
    | <a href="#data-driven-features">Data Driven Features</a>       
    | <a href="#calling-javascript-functions">Calling JavaScript Functions</a>
    | <a href="#calling-java">Calling Java Code</a>
    | <a href="#commonly-needed-utilities">Commonly Needed Utilities</a>
    | <a href="#data-driven-tests">Data Driven Scenarios</a>    
  </td>
</tr>
<tr>
  <th>Advanced</th>
  <td>
      <a href="#polling">Polling</a>
    | <a href="#conditional-logic">Conditional Logic</a>
    | <a href="#hooks">Before / After Hooks</a>
    | <a href="#json-transforms">JSON Transforms</a>
    | <a href="#loops">Loops</a>
    | <a href="#http-basic-authentication-example">HTTP Basic Auth</a> 
    | <a href="#http-header-manipulation">Header Manipulation</a> 
    | <a href="#text">GraphQL</a>
    | <a href="#async">Websockets / Async</a>
    | <a href="#call-vs-read"><code>call</code> vs <code>read()</code></a>
  </td>
</tr>
<tr>
  <th>More</th>
  <td>
      <a href="karate-mock-servlet">Mock Servlet</a>
    | <a href="karate-netty">Test Doubles</a>
    | <a href="karate-gatling">Performance Testing</a>
    | <a href="karate-core">UI Testing</a>
    | <a href="karate-robot">Desktop Automation</a>
    | <a href="https://github.com/intuit/karate/wiki/IDE-Support#vs-code-karate-plugin">VS Code / Debug</a>
    | <a href="#comparison-with-rest-assured">Karate vs REST-assured</a>
    | <a href="#cucumber-vs-karate">Karate vs Cucumber</a>
    | <a href="karate-demo">Examples and Demos</a>
  </td>
</tr>
</table>

# Features
* Java knowledge is not required and even non-programmers can write tests
* Scripts are plain-text, require no compilation step or IDE, and teams can collaborate using Git / standard SCM
* Based on the popular Cucumber / Gherkin standard - with [IDE support](https://github.com/intuit/karate/wiki/IDE-Support) and syntax-coloring options
* Elegant [DSL](https://en.wikipedia.org/wiki/Domain-specific_language) syntax 'natively' supports JSON and XML - including [JsonPath](#set) and [XPath](#xpath-functions) expressions
* Eliminate the need for 'Java Beans' or 'helper code' to represent payloads and HTTP end-points, and [dramatically reduce the lines of code](https://twitter.com/KarateDSL/status/873035687817117696) needed for a test
* Ideal for testing the highly dynamic responses from [GraphQL](http://graphql.org) API-s because of Karate's built-in [text-manipulation](#text) and [JsonPath](https://github.com/json-path/JsonPath#path-examples) capabilities
* Tests are super-readable - as scenario data can be expressed in-line, in human-friendly [JSON](#json), [XML](#xml), Cucumber [Scenario](#the-cucumber-way) Outline [tables](#table), or a [payload builder](#set-multiple) approach [unique to Karate](https://gist.github.com/ptrthomas/d6beb17e92a43220d254af942e3ed3d9)
* Express expected results as readable, well-formed JSON or XML, and [assert in a single step](#match) that the entire response payload (no matter how complex or deeply nested) - is as expected
* Comprehensive [assertion capabilities](#fuzzy-matching) - and failures clearly report which data element (and path) is not as expected, for easy troubleshooting of even large payloads
* [Fully featured debugger](https://github.com/intuit/karate/wiki/IDE-Support#vs-code-karate-plugin) that can step *backwards* and even [re-play a step while editing it](https://twitter.com/KarateDSL/status/1167533484560142336) - a *huge* time-saver
* Simpler and more [powerful alternative](https://twitter.com/KarateDSL/status/878984854012022784) to JSON-schema for [validating payload structure](#schema-validation) and format - that even supports [cross-field](#referring-to-the-json-root) / domain validation logic
* Scripts can [call other scripts](#calling-other-feature-files) - which means that you can easily re-use and maintain authentication and 'set up' flows efficiently, across multiple tests
* Embedded JavaScript engine that allows you to build a library of [re-usable functions](#calling-javascript-functions) that suit your specific environment or organization
* Re-use of payload-data and user-defined functions across tests is [so easy](#reading-files) - that it becomes a natural habit for the test-developer
* Built-in support for [switching configuration](#switching-the-environment) across different environments (e.g. dev, QA, pre-prod)
* Support for [data-driven tests](#data-driven-tests) and being able to [tag or group](#tags) tests is built-in, no need to rely on an external framework
* Native support for reading [YAML](#yaml) and even [CSV](#csv-files) files - and you can use them for data-driven tests
* Standard Java / Maven project structure, and [seamless integration](#command-line) into CI / CD pipelines - and support for [JUnit 5](#junit-5)
* Option to use as a light-weight [stand-alone executable](https://github.com/intuit/karate/tree/master/karate-netty#standalone-jar) - convenient for teams not comfortable with Java
* Multi-threaded [parallel execution](#parallel-execution), which is a huge time-saver, especially for integration and end-to-end tests
* Built-in [test-reports](#test-reports) compatible with Cucumber so that you have the option of using third-party (open-source) maven-plugins for even [better-looking reports](karate-demo#example-report)
* Reports include HTTP request and response [logs *in-line*](#test-reports), which makes [troubleshooting](https://twitter.com/KarateDSL/status/899671441221623809) and [debugging](https://twitter.com/KarateDSL/status/935029435140489216) easier
* Easily invoke JDK classes, Java libraries, or re-use custom Java code if needed, for [ultimate extensibility](#calling-java)
* Simple plug-in system for [authentication](#http-basic-authentication-example) and HTTP [header management](#configure-headers) that will handle any complex, real-world scenario
* Future-proof 'pluggable' HTTP client abstraction supports both Apache and Jersey so that you can [choose](#maven) what works best in your project, and not be blocked by library or dependency conflicts
* [Cross-browser Web UI automation](karate-core) so that you can test *all* layers of your application with the same framework
* Cross platform [Desktop Automation](karate-robot) that can be [mixed into Web Automation flows](https://twitter.com/ptrthomas/status/1215534821234995200) if needed
* Option to invoke via a [Java API](#java-api),  which means that you can easily [mix Karate into Java projects or legacy UI-automation suites](https://stackoverflow.com/q/47795762/143475)
* [Save significant effort](https://twitter.com/ptrthomas/status/986463717465391104) by re-using Karate test-suites as [Gatling performance tests](karate-gatling) that *deeply* assert that server responses are accurate under load
* Gatling integration can hook into [*any* custom Java code](https://github.com/intuit/karate/tree/master/karate-gatling#custom) - which means that you can perf-test even non-HTTP protocols such as [gRPC](https://github.com/thinkerou/karate-grpc)
* Built-in [distributed-testing capability](https://github.com/intuit/karate/wiki/Distributed-Testing) that works for API, UI and even [load-testing](https://github.com/intuit/karate/wiki/Distributed-Testing#gatling) - without needing any complex "grid" infrastructure
* [API mocks](karate-netty) or test-doubles that even [maintain CRUD 'state'](https://hackernoon.com/api-consumer-contract-tests-and-test-doubles-with-karate-72c30ea25c18) across multiple calls - enabling TDD for micro-services and [Consumer Driven Contracts](https://martinfowler.com/articles/consumerDrivenContracts.html)
* [Async](#async) support that allows you to seamlessly integrate the handling of custom events or listening to message-queues
* [Mock HTTP Servlet](karate-mock-servlet) that enables you to test __any__ controller servlet such as Spring Boot / MVC or Jersey / JAX-RS - without having to boot an app-server, and you can use your HTTP integration tests un-changed
* Comprehensive support for different flavors of HTTP calls:
  * [SOAP](#soap-action) / XML requests
  * HTTPS / [SSL](#configure) - without needing certificates, key-stores or trust-stores
  * HTTP [proxy server](#configure) support
  * URL-encoded [HTML-form](#form-field) data
  * [Multi-part](#multipart-field) file-upload - including `multipart/mixed` and `multipart/related`
  * Browser-like [cookie](#cookie) handling
  * Full control over HTTP [headers](#header), [path](#path) and query [parameters](#param)
  * [Re-try](#retry-until) until condition
  * [Websocket](http://www.websocket.org) [support](#async)

## Real World Examples
A set of real-life examples can be found here: [Karate Demos](karate-demo)

## Comparison with REST-assured
For teams familiar with or currently using [REST-assured](http://rest-assured.io), this detailed comparison of [Karate vs REST-assured](http://tinyurl.com/karatera) - can help you evaluate Karate.

## References
* [Intro to all features of Karate](https://youtu.be/yu3uupBZyxc) - video + demos (with subtitles) by [Peter Thomas](https://twitter.com/ptrthomas) (creator / lead dev of Karate)
* [Karate entered the ThoughtWorks Tech Radar](https://twitter.com/KarateDSL/status/1120985060843249664) in 2019 and was [upgraded in ranking](https://twitter.com/KarateDSL/status/1262719979104817152) in May 2020
* [マイクロサービスにおけるテスト自動化 with Karate](https://www.slideshare.net/takanorig/microservices-test-automation-with-karate/) - (*Microservices Test Automation with Karate*) presentation by [Takanori Suzuki](https://twitter.com/takanorig)

You can find a lot more references, tutorials and blog-posts [in the wiki](https://github.com/intuit/karate/wiki/Community-News). Karate also has a dedicated "tag", and a very active and supportive community at [Stack Overflow](https://stackoverflow.com/questions/tagged/karate) - where you can get support and ask questions.

# Getting Started
If you are a Java developer - Karate requires [Java](http://www.oracle.com/technetwork/java/javase/downloads/index.html) 8 (at least version 1.8.0_112 or greater) and then either [Maven](http://maven.apache.org), [Gradle](https://gradle.org), [Eclipse](#eclipse-quickstart) or [IntelliJ](https://github.com/intuit/karate/wiki/IDE-Support#intellij-community-edition) to be installed. Note that Karate works fine on OpenJDK. Any Java version from 8-12 is supported.

If you are new to programming or test-automation, refer to this video for [getting started with just the (free) IntelliJ Community Edition](https://youtu.be/W-af7Cd8cMc). Other options are the [quickstart](#quickstart) or the [standalone executable](karate-netty#standalone-jar).

If you *don't* want to use Java, you have the option of just downloading and extracting the [ZIP release](https://github.com/intuit/karate/wiki/ZIP-Release). Try this especially if you don't have much experience with programming or test-automation. We recommend that you use the [Karate extension for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=kirkslota.karate-runner) - and with that, JavaScript, .NET and Python programmers will feel right at home.

Visual Studio Code can be used for Java (or Maven) projects as well. One reason to use it is the excellent [*debug support* that we have for Karate](https://twitter.com/KarateDSL/status/1167533484560142336).

## Maven
Karate is designed so that you can choose between the [Apache](https://hc.apache.org/index.html) or [Jersey](https://jersey.java.net) HTTP client implementations.

So you need two `<dependencies>`:

```xml
<dependency>
    <groupId>com.intuit.karate</groupId>
    <artifactId>karate-apache</artifactId>
    <version>0.9.6</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.intuit.karate</groupId>
    <artifactId>karate-junit5</artifactId>
    <version>0.9.6</version>
    <scope>test</scope>
</dependency>
```

And if you run into class-loading conflicts, for example if an older version of the Apache libraries are being used within your project - then use `karate-jersey` instead of `karate-apache`.

If you want to use [JUnit 4](#junit-4), use `karate-junit4` instead of `karate-junit5`.

## Gradle
Alternatively for [Gradle](https://gradle.org) you need these two entries:

```yml
    testCompile 'com.intuit.karate:karate-junit5:0.9.6'
    testCompile 'com.intuit.karate:karate-apache:0.9.6'
```

Also refer to the wiki for using [Karate with Gradle](https://github.com/intuit/karate/wiki/Gradle).

### Quickstart
It may be easier for you to use the Karate Maven archetype to create a skeleton project with one command. You can then skip the next few sections, as the `pom.xml`, recommended directory structure, sample test and [JUnit 5](#junit-5) runners - will be created for you.

> If you are behind a corporate proxy, or especially if your local Maven installation has been configured to point to a repository within your local network, the command below may not work. One workaround is to temporarily disable or rename your Maven [`settings.xml`](https://maven.apache.org/settings.html) file, and try again.

You can replace the values of `com.mycompany` and `myproject` as per your needs.

```
mvn archetype:generate \
-DarchetypeGroupId=com.intuit.karate \
-DarchetypeArtifactId=karate-archetype \
-DarchetypeVersion=0.9.6 \
-DgroupId=com.mycompany \
-DartifactId=myproject
```

This will create a folder called `myproject` (or whatever you set the name to).

### IntelliJ Quickstart
Refer to this video for [getting started with the free IntelliJ Community Edition](https://youtu.be/W-af7Cd8cMc). It simplifies the above process, since you only need to install IntelliJ. For Eclipse, refer to the wiki on [IDE Support](https://github.com/intuit/karate/wiki/IDE-Support).

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

Alternatively, if using Gradle then add the following `sourceSets` definition
         
```yml
sourceSets {
    test {
        resources {
            srcDir file('src/test/java')
            exclude '**/*.java'
        }
    }
}
```

With the above in place, you don't have to keep switching between your `src/test/java` and `src/test/resources` folders, you can have all your test-code and artifacts under `src/test/java` and everything will work as expected.

Once you get used to this, you may even start wondering why projects need a `src/test/resources` folder at all !

### Spring Boot Example
[Soumendra Daas](https://twitter.com/sdaas) has created a nice example and guide that you can use as a reference here: [`hello-karate`](https://github.com/Sdaas/hello-karate). This demonstrates a Java Maven + JUnit 5 project set up to test a [Spring Boot](http://projects.spring.io/spring-boot/) app.

## Naming Conventions

Since these are tests and not production Java code, you don't need to be bound by the `com.mycompany.foo.bar` convention and the un-necessary explosion of sub-folders that ensues. We suggest that you have a folder hierarchy only one or two levels deep - where the folder names clearly identify which 'resource', 'entity' or API is the web-service under test.

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
* `AnimalsTest.java` (the only file that follows the `*Test.java` naming convention) acts as the 'test suite' for the entire project. By default, Karate will load all `*.feature` files from sub-directories as well. But since `some-reusable.feature` is _above_ `AnimalsTest.java` in the folder hierarchy, it will **not** be picked-up. Which is exactly what we want, because `some-reusable.feature` is designed to be [called](#calling-other-feature-files) only from one of the other test scripts (perhaps with some parameters being passed). You can also use [tags](#tags) to skip files.
* `some-classpath-function.js` and `some-classpath-payload.json` are in the 'root' of the Java ['classpath'](#classpath) which means they can be easily [read](#reading-files) (and re-used) from any test-script by using the `classpath:` prefix, for e.g: `read('classpath:some-classpath-function.js')`. Relative paths will also work.

For details on what actually goes into a script or `*.feature` file, refer to the [syntax guide](#syntax-guide).

## IDE Support
Refer to the wiki - [IDE Support](https://github.com/intuit/karate/wiki/IDE-Support).

#### `file.encoding`
In some cases, for large payloads and especially when the default system encoding is not `UTF-8` (Windows or non-US locales), you may run into issues where a `java.io.ByteArrayInputStream` is encountered instead of a string. Other errors could be a `java.net.URISyntaxException` and [`match`](#match) not working as expected because of special or foreign characters, e.g. German or `ISO-8859-15`. Typical symptoms are your tests working fine via the IDE but not when running via Maven or Gradle. The solution is to ensure that when Karate tests run, the JVM `file.encoding` is set to `UTF-8`. This can be done via the [`maven-surefire-plugin` configuration](http://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html#argLine). Add the plugin to the `<build>/<plugins>` section of your `pom.xml` if not already present: 

```xml
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>2.10</version>
        <configuration>
            <argLine>-Dfile.encoding=UTF-8</argLine>
        </configuration>
    </plugin>
``` 

## JUnit 4
> If you want to use JUnit 4, use the [`karate-junit4` Maven dependency](#maven) instead of `karate-junit5`.

To run a script `*.feature` file from your Java IDE, you just need the following empty test-class in the same package. The name of the class doesn't matter, and it will automatically run any `*.feature` file in the same package. This comes in useful because depending on how you organize your files and folders - you can have multiple feature files executed by a single JUnit test-class.

```java
package animals.cats;

import com.intuit.karate.junit4.Karate;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
public class CatsRunner {
	
}
```

Refer to your IDE documentation for how to run a JUnit class.  Typically right-clicking on the file in the project browser or even within the editor view would bring up the "Run as JUnit Test" menu option.

> Karate will traverse sub-directories and look for `*.feature` files. For example if you have the JUnit class in the `com.mycompany` package, `*.feature` files in `com.mycompany.foo` and `com.mycompany.bar` will also be run. This is one reason why you may want to prefer a 'flat' directory structure as [explained above](#naming-conventions).

## JUnit 5
Karate supports JUnit 5 and the advantage is that you can have multiple methods in a test-class. Only 1 `import` is needed, and instead of a class-level annotation, you use a nice [DRY](https://en.wikipedia.org/wiki/Don%27t_repeat_yourself) and [fluent-api](https://en.wikipedia.org/wiki/Fluent_interface) to express which tests and tags you want to use.

Note that the Java class does not need to be `public` and even the test methods do not need to be `public` - so tests end up being very concise.

Here is an [example](karate-junit5/src/test/java/karate/SampleTest.java):

```java
package karate;

import com.intuit.karate.junit5.Karate;

class SampleTest {

    @Karate.Test
    Karate testSample() {
        return Karate.run("sample").relativeTo(getClass());
    }
    
    @Karate.Test
    Karate testTags() {
        return Karate.run("tags").tags("@second").relativeTo(getClass());
    }

    @Karate.Test
    Karate testFullPath() {
        return Karate.run("classpath:karate/tags.feature").tags("@first");
    }

}
```

You should be able to right-click and run a single method using your IDE - which should be sufficient when you are in development mode. But to be able to run JUnit 5 tests from the command-line, you need to ensure that the latest version of the [maven-surefire-plugin](https://maven.apache.org/surefire/maven-surefire-plugin/examples/junit-platform.html) is present in your project `pom.xml` (within the `<build>/<plugins>` section):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>2.22.2</version>
</plugin>
```

To run a single test method, for example the `testTags()` in the example above, you can do this:

```
mvn test -Dtest=SampleTest#testTags
```

Also look at how to run tests via the [command-line](#command-line) and the [parallel runner](#junit-5-parallel-execution).

### JUnit HTML report
When you use a JUnit runner - after the execution of each feature, an HTML report is output to the `target/surefire-reports` folder and the full path will be printed to the console (see [video](https://twitter.com/KarateDSL/status/935029435140489216)).

```
html report: (paste into browser to view)
-----------------------------------------
file:/projects/myproject/target/surefire-reports/TEST-mypackage.myfeature.html
```

You can easily select (double-click), copy and paste this `file:` URL into your browser address bar. This report is useful for troubleshooting and debugging a test because all requests and responses are shown in-line with the steps, along with error messages and the output of [`print`](#print) statements. Just re-fresh your browser window if you re-run the test.

## Karate Options
To run only a specific feature file from a JUnit 4 test even if there are multiple `*.feature` files in the same folder (or sub-folders), use the `@KarateOptions` annotation.

> If you want to use JUnit 4, use the [`karate-junit4` Maven dependency](#maven) instead of `karate-junit5`. The [JUnit 5 support](#junit-5) does not require a class-level annotation to specify the feature(s) and tags to use.

```java
package animals.cats;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@KarateOptions(features = "classpath:animals/cats/cats-post.feature")
public class CatsPostRunner {
	
}
```

The `features` parameter in the annotation can take an array, so if you wanted to associate multiple feature files with a JUnit 4 test, you could do this:

```java
@KarateOptions(features = {
    "classpath:animals/cats/cats-post.feature",
    "classpath:animals/cats/cats-get.feature"})
```

You can even point to a directory (or package). Combine this with [tags](#test-suites) to execute multiple features, without having to list every one of them.

```java
@KarateOptions(features = "classpath:animals/cats", tags = "~@ignore") 
// this will run all feature files in 'animals/cats'
// except the ones tagged as @ignore
```

## Command Line
Normally in dev mode, you will use your IDE to run a `*.feature` file directly or via the companion 'runner' JUnit Java class. When you have a 'runner' class in place, it would be possible to run it from the command-line as well.

Note that the `mvn test` command only runs test classes that follow the `*Test.java` [naming convention](#naming-conventions) by default. But you can choose a single test to run like this:

```
mvn test -Dtest=CatsRunner
```

When your Java test "runner" is linked to multiple feature files, which will be the case when you use the recommended [parallel runner](#parallel-execution), you can narrow down your scope to a single feature, scenario or directory via the command-line, useful in dev-mode. Note how even [tags](#tags) to exclude (or include) can be specified using the [Karate options](#karate-options).

```
mvn test "-Dkarate.options=--tags ~@ignore classpath:demo/cats/cats.feature" -Dtest=DemoTestParallel
```

Multiple feature files (or paths) can be specified, de-limited by the space character. They should be at the end of the `karate.options`. To run only a single scenario, append the line number on which the scenario is defined, de-limited by `:`.
```
mvn test "-Dkarate.options=PathToFeatureFiles/order.feature:12" -Dtest=DemoTestParallel
```
Note that this is currently not supported for [JUnit 5](#junit-5)  `@Karate.Test` annotation.

### Command Line - Gradle

For Gradle you must extend the test task to allow the `karate.options` to be passed to the runtime (otherwise they get consumed by Gradle itself). To do that, add the following:

```yml
test {
    // pull karate options into the runtime
    systemProperty "karate.options", System.properties.getProperty("karate.options")
    // pull karate env into the runtime
    systemProperty "karate.env", System.properties.getProperty("karate.env")
    // ensure tests are always run
    outputs.upToDateWhen { false }
}
```

And then the above command in Gradle would look like:

```
./gradlew test -Dtest=CatsRunner
```

### Test Suites
> The recommended way to define and run test-suites and reporting in Karate is to use the [parallel runner](#parallel-execution), described in the next section. The approach in this section is more suited for troubleshooting in dev-mode, using your IDE.

One way to define 'test-suites' in Karate is to have a JUnit class at a level 'above' (in terms of folder hierarchy) all the `*.feature` files in your project. So if you take the previous [folder structure example](#naming-conventions), you can do this on the command-line:

```
mvn test "-Dkarate.options=--tags ~@ignore" -Dtest=AnimalsTest
```

Here, `AnimalsTest` is the name of the Java class we designated to run the multiple `*.feature` files that make up your test-suite. There is a neat way to [tag your tests](#tags) and the above example demonstrates how to run all tests _except_ the ones tagged `@ignore`.

For JUnit 4, The tag options can be specified in the test-class via the `@KarateOptions` annotation, in which case you don't need to pass the `-Dkarate.options` on the command-line:

```java
@KarateOptions(tags = {"~@ignore"})
```

You can 'lock down' the fact that you only want to execute the single JUnit class that functions as a test-suite - by using the following [maven-surefire-plugin configuration](http://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>${maven.surefire.version}</version>
    <configuration>
        <includes>
            <include>animals/AnimalsTest.java</include>
        </includes>
        <systemProperties>
            <karate.options>--tags ~@ignore</karate.options>
        </systemProperties>            
    </configuration>
</plugin> 
```

Note how the `karate.options` can be specified using the `<systemProperties>` configuration. Options here would over-ride corresponding options specified if a `@KarateOptions` annotation is present (on `AnimalsTest.java`).

For Gradle, you simply specify the test which is to be `include`-d:

```yml
test {
    include 'animals/AnimalsTest.java'
    // pull karate options into the runtime
    systemProperty "karate.options", System.properties.getProperty("karate.options")
    // pull karate env into the runtime
    systemProperty "karate.env", System.properties.getProperty("karate.env")
    // ensure tests are always run
    outputs.upToDateWhen { false }
}
```

The big drawback of the approach above is that you cannot run tests in parallel. The recommended approach for Karate reporting in a Continuous Integration set-up is described in the next section which focuses on generating the [JUnit XML](https://wiki.jenkins-ci.org/display/JENKINS/JUnit+Plugin) format that most CI tools can consume. The [Cucumber JSON format](https://relishapp.com/cucumber/cucumber/docs/formatters/json-output-formatter) is also emitted, which gives you plenty of options for generating pretty reports using third-party maven plugins.

And most importantly - you can run tests in parallel without having to depend on third-party hacks that introduce code-generation and config 'bloat' into your `pom.xml` or `build.gradle`.

## Parallel Execution
Karate can run tests in parallel, and dramatically cut down execution time. This is a 'core' feature and does not depend on JUnit, Maven or Gradle. 

* You can easily "choose" features and tags to run and compose test-suites in a very flexible manner.
* You can use the returned `Results` object to check if any scenarios failed, and to even summarize the errors
* [JUnit XML](https://wiki.jenkins-ci.org/display/JENKINS/JUnit+Plugin) reports will be generated in the "`reportDir`" path you specify, and you can easily configure your CI to look for these files after a build (for e.g. in `**/*.xml` or `**/surefire-reports/*.xml`)
* [Cucumber JSON reports](https://relishapp.com/cucumber/cucumber/docs/formatters/json-output-formatter) will be generated side-by-side with the JUnit XML reports and with the same name, except that the extension will be `.json` instead of `.xml`

### JUnit 4 Parallel Execution
> Important: **do not** use the `@RunWith(Karate.class)` annotation. This is a *normal* JUnit 4 test class ! If you want to use JUnit 4, use the [`karate-junit4` Maven dependency](#maven) instead of `karate-junit5`.

```java
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.Assert.*;
import org.junit.Test;

public class TestParallel {
    
    @Test
    public void testParallel() {
        Results results = Runner.path("classpath:some/package").tags("~@ignore").parallel(5);
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }
    
}
```

* You don't use a JUnit runner (no `@RunWith` annotation), and you write a plain vanilla JUnit test (it could even be a normal Java class with a `main` method)
* The `Runner.path()` "builder" method in `karate-core` is how you refer to the package you want to execute, and all feature files within sub-directories will be picked up
* `Runner.path()` takes multiple string parameters, so you can refer to multiple packages or even individual `*.feature` files and easily "compose" a test-suite
  * e.g. `Runner.path("classpath:animals", "classpath:some/other/package.feature")`
* To [choose tags](#tags), call the `tags()` API, note that in the example above, any `*.feature` file tagged as `@ignore` will be skipped - as the `~` prefix means a "NOT" operation. You can also specify tags on the [command-line](#test-suites). The `tags()` method also takes multiple arguments, for e.g.
  * this is an "AND" operation: `tags("@customer", "@smoke")`
  * and this is an "OR" operation: `tags("@customer,@smoke")`
* There is an optional `reportDir()` method if you want to customize the directory to which the [XML and JSON](#parallel-execution) will be output, it defaults to `target/surefire-reports`
* If you want to dynamically and programmatically determine the tags and features to be included - the API also accepts `List<String>` as the `path()` and `tags()` methods arguments 
* `parallel()` *has* to be the last method called, and you pass the number of parallel threads needed. It returns a `Results` object that has all the information you need - such as the number of passed or failed tests.

### JUnit 5 Parallel Execution
For [JUnit 5](#junit-5) you can omit the `public` modifier for the class and method, and there are some changes to `import` package names. The method signature of the `assertTrue` has flipped around a bit. Also note that you don't use `@Karate.Test` for the method, and you just use the *normal* JUnit 5 `@Test` annotation.

Else the `Runner.path()` "builder" API is the same, refer the description above for [JUnit 4](#junit-4-parallel-execution).

```java
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TestParallel {

    @Test
    void testParallel() {
        Results results = Runner.path("classpath:animals").tags("~@ignore").parallel(5);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

}
```

### Parallel Stats
For convenience, some stats are logged to the console when execution completes, which should look something like this:

```
======================================================
elapsed:   2.35 | threads:    5 | thread time: 4.98 
features:    54 | ignored:   25 | efficiency: 0.42
scenarios:  145 | passed:   145 | failed: 0
======================================================
```

The parallel runner will always run `Feature`-s in parallel. Karate will also run `Scenario`-s in parallel by default. So if you have a `Feature` with multiple `Scenario`-s in it - they will execute in parallel, and even each `Examples` row in a `Scenario Outline` will do so ! 

A `timeline.html` file will also be saved to the report output directory mentioned above (`target/surefire-reports` by default) - which is useful for visually verifying or troubleshooting the effectiveness of the test-run ([see video](https://twitter.com/KarateDSL/status/1049321708241317888)).

### `@parallel=false`
In rare cases you may want to suppress the default of `Scenario`-s executing in parallel and the special [`tag`](#tags) `@parallel=false` can be used. If you place it above the [`Feature`](#script-structure) keyword, it will apply to all `Scenario`-s. And if you just want one or two `Scenario`-s to NOT run in parallel, you can place this tag above only *those* `Scenario`-s. See [example](karate-demo/src/test/java/demo/encoding/encoding.feature).

Note that forcing `Scenario`-s to run in a particular sequence [is an anti-pattern](https://stackoverflow.com/a/46080568/143475), and should be avoided as far as possible.

## Test Reports
As mentioned above, most CI tools would be able to process the JUnit XML output of the [parallel runner](#parallel-execution) and determine the status of the build as well as generate reports.

The [Karate Demo](karate-demo) has a working example of the recommended parallel-runner set up. It also [details how](karate-demo#example-report) a third-party library can be easily used to generate some very nice-looking reports, from the JSON output of the parallel runner.

For example, here below is an actual report generated by the [cucumber-reporting](https://github.com/damianszczepanik/cucumber-reporting) open-source library.  

<img src="karate-demo/src/test/resources/karate-maven-report.jpg" height="600px"/>

This report is recommended especially because Karate's integration includes the HTTP request and response logs [in-line with the test report](https://twitter.com/KarateDSL/status/899671441221623809), which is extremely useful for troubleshooting test failures.

<img src="karate-demo/src/test/resources/karate-maven-report-http.jpg" height="600px"/>

The demo also features [code-coverage using Jacoco](karate-demo#code-coverage-using-jacoco), and some tips for even non-Java back-ends. Some third-party report-server solutions integrate with Karate such as [ReportPortal.io](https://github.com/reportportal/agent-java-karate).

## Logging
> This is optional, and Karate will work without the logging config in place, but the default console logging may be too verbose for your needs.

Karate uses [LOGBack](http://logback.qos.ch) which looks for a file called `logback-test.xml` on the '[classpath](#classpath)'.

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
   
    <logger name="com.intuit.karate" level="DEBUG"/>
   
    <root level="info">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE" />
    </root>
  
</configuration>
```
You can change the `com.intuit.karate` logger level to `INFO` to reduce the amount of logging. When the level is `DEBUG` the entire request and response payloads are logged. If you use the above config, logs will be captured in `target/karate.log`.

If you want to keep the level as `DEBUG` ([for HTML reports](#test-reports)) but suppress logging to the console, you can comment out the `STDOUT` "root" `appender-ref`:

```xml
  <root level="warn">
      <!-- <appender-ref ref="STDOUT" /> -->
      <appender-ref ref="FILE" />
  </root>
```

Or another option is to use a [`ThresholdFilter`](http://logback.qos.ch/manual/filters.html#thresholdFilter), so you still see critical logs on the console:

```xml
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
      <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
          <level>WARN</level>
      </filter>
      <encoder>
          <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
      </encoder>
  </appender>
```

For suppressing sensitive information such as secrets and passwords from the log, see [Log Masking](#log-masking).

# Configuration
> You can skip this section and jump straight to the [Syntax Guide](#syntax-guide) if you are in a hurry to get started with Karate. Things will work even if the `karate-config.js` file is not present.

## Classpath
The 'classpath' is a Java concept and is where some configuration files such as the one for [logging](#logging) are expected to be by default. If you use the Maven `<test-resources>` tweak [described earlier](#folder-structure) (recommended), the 'root' of the classpath will be in the `src/test/java` folder, or else would be `src/test/resources`.

## `karate-config.js`

The only 'rule' is that on start-up Karate expects a file called `karate-config.js` to exist on the 'classpath' and contain a [JavaScript function](#javascript-functions). The function is expected to return a JSON object and all keys and values in that JSON object will be made available as script variables.

And that's all there is to Karate configuration ! You can easily get the value of the [current 'environment' or 'profile'](#switching-the-environment), and then set up 'global' variables using some simple JavaScript. Here is an example:

```javascript    
function fn() {   
  var env = karate.env; // get java system property 'karate.env'
  karate.log('karate.env system property was:', env);
  if (!env) {
    env = 'dev'; // a custom 'intelligent' default
  }
  var config = { // base config JSON
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

> Here above, you see the [`karate.log()`](#karate-log), [`karate.env`](#karate-env) and [`karate.configure()`](#karate-configure) "helpers" being used. Note that the `karate-config.js` is re-processed for *every* `Scenario` and in rare cases, you may want to initialize (e.g. auth tokens) only once for all of your tests. This can be achieved using [`karate.callSingle()`](#karate-callsingle).

A common requirement is to pass dynamic parameter values via the command line, and you can use the [`karate.properties['some.name']`](#karate-properties) syntax for getting a system property passed via JVM options in the form `-Dsome.name=foo`. Refer to the section on [dynamic port numbers](#dynamic-port-numbers) for an example.

> You can even retrieve operating-system environment variables via [Java interop](#calling-java) as follows: `var systemPath = java.lang.System.getenv('PATH');`

This decision to use JavaScript for config is influenced by years of experience with the set-up of complicated test-suites and fighting with [Maven profiles](http://maven.apache.org/guides/introduction/introduction-to-profiles.html), [Maven resource-filtering](https://maven.apache.org/plugins/maven-resources-plugin/examples/filter.html) and the XML-soup that somehow gets summoned by the [Maven AntRun plugin](http://maven.apache.org/plugins/maven-antrun-plugin/usage.html).

Karate's approach frees you from Maven, is far more expressive, allows you to eyeball all environments in one place, and is still a plain-text file.  If you want, you could even create [nested chunks of JSON that 'name-space' your config variables](https://stackoverflow.com/a/49693808/143475).

> One way to appreciate Karate's approach is to think over what it takes to add a new environment-dependent variable (e.g. a password) into a test. In typical frameworks it could mean changing multiple properties files, maven profiles and placeholders, and maybe even threading the value via a dependency-injection framework - before you can even access the value within your test.

This approach is indeed slightly more complicated than traditional `*.properties` files - but you _need_ this complexity. Keep in mind that these are tests (not production code) and this config is going to be maintained more by the dev or QE team instead of the 'ops' or operations team.

And there is no more worrying about Maven profiles and whether the 'right' `*.properties` file has been copied to the proper place.

### Restrictions on Global Variables
Non-JSON values such as Java object references or JS functions are supported only if they are at the "root" of the JSON returned from [`karate-config.js`](#karate-configjs). So this below will *not* work:

```javascript
function fn() {
  var config = {};
  config.utils = {};
  config.utils.uuid = function(){ return java.util.UUID.randomUUID() + '' };
  // this is wrong, the "nested" uuid will be lost
  return config;
}
```

The recommended best-practice is to move the `uuid` function into a common feature file following the pattern described [here](#multiple-functions-in-one-file):

```javascript
function fn() {
  var config = {};
  config.utils = karate.call('utils.feature')
  return config;
}
```

But you can opt for using [`karate.toMap()`](#karate-tomap) which will "wrap" things so that the nested objects are not "lost":

```javascript
function fn() {
  var config = {};
  var utils = {};
  utils.uuid = function(){ return java.util.UUID.randomUUID() + '' };
  config.utils = karate.toMap(utils);
  return config;
}
```

## Switching the Environment
There is only one thing you need to do to switch the environment - which is to set a Java system property.

> By default, the value of `karate.env` when you access it within [`karate-config.js`](#karate-configjs) - would be `null`.

The recipe for doing this when running Maven from the command line is:
```
mvn test -DargLine="-Dkarate.env=e2e"
```

Or in Gradle:

```
./gradlew test -Dkarate.env=e2e
```

You can refer to the documentation of the
[Maven Surefire Plugin](http://maven.apache.org/plugins-archives/maven-surefire-plugin-2.12.4/examples/system-properties.html) for alternate ways of achieving this, but the `argLine` approach is the simplest and should be more than sufficient for your Continuous Integration or test-automation needs.

Here's a reminder that running any [single JUnit test via Maven](https://maven.apache.org/surefire/maven-surefire-plugin/examples/single-test.html) can be done by:
```
mvn test -Dtest=CatsRunner
```
Where `CatsRunner` is the JUnit class name (in any package) you wish to run.

Karate is flexible, you can easily over-write config variables within each individual test-script - which is very convenient when in dev-mode or rapid-prototyping.

Just for illustrative purposes, you could 'hard-code' the `karate.env` for a specific JUnit 4 test like this. Since CI test-automation would typically use a [designated 'top-level suite' test-runner](#test-suites), you can actually have these individual test-runners lying around without any ill-effects. They are useful for running tests from the IDE and for dev-mode troubleshooting. To ensure that they don't get run by CI by mistake - just *don't* use the [`*Test.java` naming convention](http://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html).

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

## Environment Specific Config
When your project gets complex, you can have separate `karate-config-<env>.js` files that will be processed for that specific value of [`karate.env`](#switching-the-environment). This is especially useful when you want to maintain passwords, secrets or even URL-s specific for your local dev environment. 

> Make sure you configure your source code management system (e.g. Git) to ignore `karate-config-*.js` if needed.

Here are the rules Karate uses on bootstrap (before every `Scenario` or `Examples` row in a `Scenario Outline`):

* if the system-property `karate.config.dir` was set, Karate will look in this folder for `karate-config.js` - and if found, will process it
* else if `karate-config.js` was not found in the above location (or `karate.config.dir` was not set), [`classpath:karate-config.js`](#configuration) would be processed (this is the default / common case)
* if the `karate.env` system property was set
  * if `karate.config.dir` was set, Karate will also look for `file:<karate.config.dir>/karate-config-<env>.js`
  * else (if the `karate.config.dir` was *not* set), Karate will look for `classpath:karate-config-<env>.js`
* if the over-ride `karate-config-<env>.js` exists, it will be processed, and the configuration (JSON entries) returned by this function will over-ride any set by `karate-config.js`

Refer to the [karate demo](karate-demo) for an [example](karate-demo/src/test/java/karate-config-contract.js).

### `karate-base.js`
Advanced users who build frameworks on top of Karate have the option to supply a `karate-base.js` file that Karate will look for on the [`classpath:`](#classpath). This is useful when you ship a JAR file containing re-usable features and JavaScript / Java code and want to 'default' a few variables that teams can 'inherit' from. So an additional rule in the above flow of 'rules' (before the *first* step) is as follows:

* if `classpath:karate-base.js` exists - Karate will process this as a [configuration source](#configuration) before anything else

# Syntax Guide
## Script Structure
Karate scripts are technically in '[Gherkin](https://docs.cucumber.io/gherkin/reference/)' format - but all you need to grok as someone who needs to test web-services are the three sections: `Feature`, `Background` and `Scenario`. There can be multiple Scenario-s in a `*.feature` file, and at least one should be present. The `Background` is optional. 

> Variables set using [`def`](#def) in the `Background` will be re-set before *every* `Scenario`. If you are looking for a way to do something only **once** per `Feature`, take a look at [`callonce`](#callonce). On the other hand, if you are expecting a variable in the `Background` to be modified by one `Scenario` so that later ones can see the updated value - that is *not* how you should think of them, and you should combine your 'flow' into one scenario. Keep in mind that you should be able to comment-out a `Scenario` or skip some via [`tags`](#tags) without impacting any others. Note that the [parallel runner](#parallel-execution) will run `Scenario`-s in parallel, which means they can run in *any* order. If you are looking for ways to do something only *once* per feature or across *all* your tests, see [Hooks](#hooks).

Lines that start with a `#` are comments.
```cucumber
Feature: brief description of what is being tested
    more lines of description if needed.

Background:
  # this section is optional !
  # steps here are executed before each Scenario in this file
  # variables defined here will be 'global' to all scenarios
  # and will be re-initialized before every scenario

Scenario: brief description of this scenario
  # steps for this scenario

Scenario: a different scenario
  # steps for this other scenario
```

> There is also a variant of `Scenario` called `Scenario Outline` along with `Examples`, useful for [data-driven tests](#data-driven-tests).

### Given-When-Then
The business of web-services testing requires access to low-level aspects such as HTTP headers, URL-paths, query-parameters, complex JSON or XML payloads and response-codes. And Karate gives you control over these aspects with the small set of keywords focused on HTTP such as [`url`](#url), [`path`](#path), [`param`](#param), etc.

Karate does not attempt to have tests be in "natural language" like how Cucumber tests are [traditionally expected to be](https://docs.cucumber.io/gherkin/reference/). That said, the syntax is very concise, and the convention of every step having to start with either `Given`, `And`, `When` or `Then`, makes things very readable. You end up with a decent approximation of BDD even though web-services by nature are "headless", without a UI, and not really human-friendly.

#### Cucumber vs Karate
> Karate was based on Cucumber-JVM until version 0.8.0 but the parser and engine were [re-written from scratch](https://github.com/intuit/karate/issues/444) in 0.9.0 onwards. So we use the same [Gherkin](https://docs.cucumber.io/gherkin/) syntax - but the similarity ends there.

If you are familiar with Cucumber (JVM), you may be wondering if you need to write [step-definitions](https://docs.cucumber.io/gherkin/step-organization/). The answer is **no**.

Karate's approach is that all the step-definitions you need in order to work with HTTP, JSON and XML have been already implemented. And since you can easily extend Karate [using JavaScript](#call), there is no need to compile Java code any more.

The following table summarizes some key differences between Cucumber and Karate.

:white_small_square: | Cucumber | Karate
-------------------- | -------- | ------
**Step Definitions Built-In** | **No**. You need to keep implementing them as your functionality grows. [This can get very tedious](https://thepracticaldeveloper.com/2017/08/03/microservices-end-to-end-tests-with-cucumber-and-spring-boot/), especially since for [dependency-injection](https://docs.cucumber.io/cucumber/state/#dependency-injection), you are [on your own](http://angiejones.tech/rest-assured-with-cucumber-using-bdd-for-web-services-automation?refreshed=y#comment-40). | :white_check_mark: **Yes**. No extra Java code needed.
**Single Layer of Code To Maintain** | **No**. There are 2 Layers. The [Gherkin](https://docs.cucumber.io/gherkin/reference/) spec or `*.feature` files make up one layer, and you will also have the corresponding Java step-definitions. | :white_check_mark: **Yes**. Only 1 layer of Karate-script (based on Gherkin).
**Readable Specification** | **Yes**. Cucumber will read like natural language _if_ you implement the step-definitions right. | :x: **No**. Although Karate is simple, and a [true DSL](https://ayende.com/blog/2984/dsl-vs-fluent-interface-compare-contrast), it is ultimately a [mini-programming language](https://hackernoon.com/yes-karate-is-not-true-bdd-698bf4a9be39). But it is [perfect for testing web-services](https://stackoverflow.com/a/47799207/143475) at the level of HTTP requests and responses.
**Re-Use Feature Files** | **No**. Cucumber does not support being able to call (and thus re-use) other `*.feature` files from a test-script. | :white_check_mark: [**Yes**](#calling-other-feature-files).
**Dynamic Data-Driven Testing** | **No**. Cucumber's [`Scenario Outline`](#the-cucumber-way) expects the `Examples` to contain a fixed set of rows. | :white_check_mark: **Yes**. Karate's support for calling other `*.feature` files allows you to use a [JSON array as the data-source](#data-driven-features) and you can [use JSON](https://twitter.com/KarateDSL/status/1051433711814627329) or [even CSV](#csv-files) directly in a data-driven `Scenario Outline`.
**Parallel Execution** | **No**. There are some challenges (especially with reporting) and you can find various discussions and third-party projects on the web that attempt to close this gap: [1](https://github.com/cucumber/cucumber-jvm/issues/630) [2](https://opencredo.com/test-automation-concepts-parallel-test-execution/) [3](http://stackoverflow.com/questions/41034116/how-to-execute-cucumber-feature-file-parallel) [4](https://github.com/DisneyStudios/cucumber-slices-maven-plugin) [5](https://github.com/temyers/cucumber-jvm-parallel-plugin) [6](https://github.com/trivago/cucable-plugin) [7](https://github.com/eu-evops/cucumber-runner-maven-plugin) [8](https://automationrhapsody.com/running-cucumber-tests-in-parallel/) | :white_check_mark: [**Yes**](#parallel-execution). Karate runs even `Scenario`-s in parallel, not just `Feature`-s.
**Run 'Set-Up' Routines Only Once** | **No**. Cucumber has a limitation where `Background` steps are re-run for every `Scenario` and worse - even for every `Examples` row within a `Scenario Outline`. This has been a [highly-requested open issue](https://github.com/cucumber/cucumber-jvm/issues/515) for a *long* time. | :white_check_mark: [**Yes**](#hooks).
**Embedded JavaScript Engine** | **No**. And you have to roll your own approach to environment-specific configuration and worry about [dependency-injection](https://docs.cucumber.io/cucumber/state/#dependency-injection). | :white_check_mark: **Yes**. Easily define all environments in a [single file](#configuration) and share variables across all scenarios. Full script-ability via [JS](#calling-javascript-functions) or [Java interop](#java-interop).

One nice thing about the design of the Gherkin syntax is that script-steps are treated the same no matter whether they start with the keyword `Given`, `And`, `When` or `Then`.  What this means is that you are free to use whatever makes sense for you.  You could even have all the steps start with `When` and Karate won't care.

In fact Gherkin supports the [catch-all symbol '`*`'](https://www.relishapp.com/cucumber/cucumber/docs/gherkin/using-star-notation-instead-of-given-when-then) - instead of forcing you to use `Given`, `When` or `Then`. This is perfect for those cases where it really doesn't make sense - for example the [`Background`](#script-structure) section or when you use the [`def`](#def) or [`set`](#set) syntax. When eyeballing a test-script, think of the `*` as a 'bullet-point'.

You can read more about the Given-When-Then convention at the [Cucumber reference documentation](https://docs.cucumber.io/gherkin/reference/). Since Karate uses Gherkin, you can also employ [data-driven](#data-driven-tests) techniques such as expressing data-tables in test scripts. Another good thing that Karate inherits is the nice IDE support for Cucumber that [IntelliJ](https://www.jetbrains.com/idea/help/cucumber.html) and [Eclipse](https://cucumber.io/cucumber-eclipse/) have. So you can do things like right-click and run a `*.feature` file (or scenario) without needing to use a JUnit runner.

For a detailed discussion on BDD and how Karate relates to Cucumber, please refer to this blog-post: [Yes, Karate is not *true* BDD](https://medium.com/@ptrthomas/yes-karate-is-not-true-bdd-698bf4a9be39). It is the opinion of the author of Karate that *true* BDD is un-necessary over-kill for API testing, and this is explained more in [this answer](https://stackoverflow.com/a/47799207/143475) on [Stack Overflow](https://stackoverflow.com/questions/tagged/karate).

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
Note that `def` will over-write any variable that was using the same name earlier. Keep in mind that the start-up [configuration routine](#configuration) could have already initialized some variables before the script even started. For details of scope and visibility of variables, see [Script Structure](#script-structure).

> Note that `url` and `request` are not allowed as variable names. This is just to reduce confusion for users new to Karate who tend to do `* def request = {}` and expect the [`request`](#request) body or similarly, the [`url`](#url) to be set.

The examples above are simple, but a variety of expression 'shapes' are supported on the right hand side of the `=` symbol. The section on [Karate Expressions](#karate-expressions) goes into the details.

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
You can use `print` to log variables to the console in the middle of a script. For convenience, you can have multiple expressions separated by commas, so this is the recommended pattern:

```cucumber
* print 'the value of a is:', a
```

Similar to [`assert`](#assert), the expressions on the right-hand-side of a `print` have to be valid JavaScript. JsonPath and [Karate expressions](#karate-expressions) are not supported.

If you use commas (instead of concatenating strings using `+`), Karate will 'pretty-print' variables, which is what you typically want when dealing with [JSON or XML](#native-data-types).

```cucumber
* def myJson = { foo: 'bar', baz: [1, 2, 3] }
* print 'the value of myJson is:', myJson
```
Which results in the following output:
```
20:29:11.290 [main] INFO  com.intuit.karate - [print] the value of myJson is: {
  "foo": "bar",
  "baz": [
    1,
    2,
    3
  ]
}
```

> Since XML is represented internally as a JSON-like or map-like object, if you perform string concatenation when printing, you will *not* see XML - which can be confusing at first. Use the comma-delimited form (see above) or the JS helper (see below).

The built-in [`karate` object](#the-karate-object) is explained in detail later, but for now, note that this is also injected into `print` (and even `assert`) statements, and it has a helpful [`pretty`](#karate-pretty) method, that takes a JSON argument and a [`prettyXml`](#karate-prettyxml) method that deals with XML. So you could have also done something like:

```cucumber
* print 'the value of myJson is:\n' + karate.pretty(myJson)
```

Also refer to the [`configure`](#configure) keyword on how to switch on pretty-printing of all HTTP requests and responses.

# 'Native' data types
Native data types mean that you can insert them into a script without having to worry about enclosing them in strings and then having to 'escape' double-quotes all over the place. They seamlessly fit 'in-line' within your test script.

## JSON
Note that the parser is 'lenient' so that you don't have to enclose all keys in double-quotes.
```cucumber
* def cat = { name: 'Billie', scores: [2, 5] }
* assert cat.scores[1] == 5
```

> Some characters such as the hyphen `-` are not permitted in 'lenient' JSON keys (because they are interpreted by the JS engine as a 'minus sign'). In such cases, you *have* to use string quotes: `{ 'Content-Type': 'application/json' }`

When asserting for expected values in JSON or XML, always prefer using [`match`](#match) instead of [`assert`](#assert). Match failure messages are much more descriptive and useful, and you get the power of [embedded expressions](#embedded-expressions) and [fuzzy matching](#fuzzy-matching).
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
Karate has a very useful payload 'templating' approach. Variables can be referred to within JSON, for example:

```cucumber
Given def user = { name: 'john', age: 21 }
And def lang = 'en'
When def session = { name: '#(user.name)', locale: '#(lang)', sessionUser: '#(user)'  }
```
So the rule is - if a string value within a JSON (or XML) object declaration is enclosed between `#(` and `)` - it will be evaluated as a JavaScript expression. And any variables which are alive in the context can be used in this expression. Here's how it works for XML:

```cucumber
Given def user = <user><name>john</name></user>
And def lang = 'en'
When def session = <session><locale>#(lang)</locale><sessionUser>#(user)</sessionUser></session>
```

This comes in useful in some cases - and avoids needing to use the [`set`](#set) keyword or [JavaScript functions](#javascript-functions) to manipulate JSON. So you get the best of both worlds: the elegance of JSON to express complex nested data - while at the same time being able to dynamically plug values (that could even be other JSON or XML 'trees') into a 'template'.

Note that embedded expressions will be evaluated even when you [`read()` from a JSON or XML file](#reading-files). This is super-useful for re-use and data-driven tests.

A few special built-in variables such as `$` (which is a [reference to the JSON root](#referring-to-self)) - can be mixed into JSON embedded expressions.

A [special case](#remove-if-null) of embedded expressions can remove a JSON key (or XML element / attribute) if the expression evaluates to `null`.

#### Rules for Embedded Expressions
* They work only within JSON or XML and when on the Right Hand Side of a `def` or `match` or when you [`read()`](#reading-files) a JSON or XML file. And the expression *has* to start with `"#(` and end with `)` - so note that string-concatenation may not work quite the way you expect:

```cucumber
# wrong !
* def foo = { bar: 'hello #(name)' }
# right !
* def foo = { bar: '#("hello " + name)' }
```

Observe how you can achieve string concatenation if you really want, because any valid JavaScript expression can be stuffed within an embedded expression. You could always do this in two steps:
```cucumber        
* def temp = 'hello ' + name
* def foo = { bar: '#(temp)' }
```

As a convenience, embedded expressions are supported on the Right Hand Side of a [`match`](#match) statement even for "quoted string" literals:
```cucumber
* def foo = 'a1'
* match foo == '#("a" + 1)'
```

### Enclosed JavaScript
An alternative to embedded expressions (for JSON only) is to enclose the entire payload within parentheses - which tells Karate to evaluate it as pure JavaScript. This can be a lot simpler than embedded expressions in many cases, and JavaScript programmers will feel right at home.

The example below shows the difference between embedded expressions and enclosed JavaScript:

```cucumber
When def user = { name: 'john', age: 21 }
And def lang = 'en'

* def embedded = { name: '#(user.name)', locale: '#(lang)', sessionUser: '#(user)' }
* def enclosed = ({ name: user.name, locale: lang, sessionUser: user })
* match embedded == enclosed
```

> So how would you choose between the two approaches to create JSON ? [Embedded expressions](#embedded-expressions) are useful when you have complex JSON [`read`](#reading-files) from files, because you can auto-replace (or even [remove](#remove-if-null)) data-elements with values dynamically evaluated from variables. And the JSON will still be 'well-formed', and editable in your IDE or text-editor. Embedded expressions also make more sense in [validation](#ignore-or-validate) and [schema-like](#schema-validation) short-cut situations. It can also be argued that the `#` symbol is easy to spot when eyeballing your test scripts - which makes things more readable and clear.

### Multi-Line Expressions
The keywords [`def`](#def), [`set`](#set), [`match`](#match), [`request`](#request) and [`eval`](#eval) take multi-line input as the last argument. This is useful when you want to express a one-off lengthy snippet of text in-line, without having to split it out into a separate [file](#reading-files). Note how triple-quotes (`"""`) are used to enclose content. Here are some examples:

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
### A simple way to create JSON Arrays
Now that we have seen how JSON is a 'native' data type that Karate understands, there is a very nice way to create JSON using Cucumber's support for expressing [data-tables](http://www.thinkcode.se/blog/2014/06/30/cucumber-data-tables).

```cucumber
* table cats
  | name   | age |
  | 'Bob'  | 2   |
  | 'Wild' | 4   |
  | 'Nyan' | 3   |

* match cats == [{name: 'Bob', age: 2}, {name: 'Wild', age: 4}, {name: 'Nyan', age: 3}]
```

The [`match`](#match) keyword is explained later, but it should be clear right away how convenient the `table` keyword is. JSON can be combined with the ability to [call other `*.feature` files](#data-driven-features) to achieve dynamic data-driven testing in Karate.

Notice that in the above example, string values within the table need to be enclosed in quotes. Otherwise they would be evaluated as expressions - which does come in useful for some dynamic data-driven situations:

```cucumber
* def one = 'hello'
* def two = { baz: 'world' }
* table json
  | foo     | bar            |
  | one     | { baz: 1 }     |
  | two.baz | ['baz', 'ban'] |
* match json == [{ foo: 'hello', bar: { baz: 1 } }, { foo: 'world', bar: ['baz', 'ban'] }]
```
Yes, you can even nest chunks of JSON in tables, and things work as you would expect.

Empty cells or expressions that evaluate to `null` will result in the key being omitted from the JSON. To force a `null` value, wrap it in parentheses:
```cucumber
* def one = { baz: null }
* table json
  | foo     | bar    |
  | 'hello' |        |
  | one.baz | (null) |
  | 'world' | null   |
* match json == [{ foo: 'hello' }, { bar: null }, { foo: 'world' }]
```

An alternate way to create data is using the [`set` multiple](#set-multiple) syntax. It is actually a 'transpose' of the `table` approach, and can be very convenient when there are a large number of keys per row or if the nesting is complex. Here is an example of what is possible:

```cucumber
* set search
  | path       | 0        | 1      | 2       |
  | name.first | 'John'   | 'Jane' |         |
  | name.last  | 'Smith'  | 'Doe'  | 'Waldo' |
  | age        | 20       |        |         |

* match search[0] == { name: { first: 'John', last: 'Smith' }, age: 20 }
* match search[1] == { name: { first: 'Jane', last: 'Doe' } }
* match search[2] == { name: { last: 'Waldo' } }
```

## `text`
### Don't parse, treat as raw text
Not something you would commonly use, but in some cases you need to disable Karate's default behavior of attempting to parse anything that looks like JSON (or XML) when using [multi-line](#multi-line-expressions) / string [expressions](#karate-expressions). This is especially relevant when manipulating [GraphQL](http://graphql.org) queries - because although they look suspiciously like JSON, they are not, and tend to confuse Karate's internals. And as shown in the example below, having text 'in-line' is useful especially when you use the `Scenario Outline:` and `Examples:` for [data-driven tests](#data-driven-tests) involving Cucumber-style place-holder substitutions in strings.

```cucumber
Scenario Outline:
  # note the 'text' keyword instead of 'def'
  * text query =
    """
    {
      hero(name: "<name>") {
        height
        mass
      }
    }
    """
  Given path 'graphql'
  And request { query: '#(query)' }
  And header Accept = 'application/json'
  When method post
  Then status 200

  Examples:
    | name  |
    | John  |
    | Smith | 
```

Note that if you did not need to inject [`Examples:`](#data-driven-tests) into 'placeholders' enclosed within `<` and `>`, [reading from a file](#reading-files) with the extension `*.txt` may have been sufficient.

For placeholder-substitution, the [`replace`](#replace) keyword can be used instead, but with the advantage that the text can be read from a file or dynamically created.

Karate is a great fit for testing GraphQL because of how easy it is to deal with dynamic and deeply nested JSON responses. Refer to this example for more details: [`graphql.feature`](karate-demo/src/test/java/demo/graphql/graphql.feature).

## `replace`
### Text Placeholder Replacement
> Modifying existing JSON and XML is __natively__ supported by Karate via the [`set`](#set) keyword, and `replace` is primarily intended for dealing with raw strings. But when you deal with complex, nested JSON (or XML) - it may be easier in some cases to use `replace`, especially when you want to substitute multiple placeholders with one value, and when you don't need array manipulation. Since `replace` auto-converts the result to a string, make sure you perform [type conversion](#type-conversion) back to JSON (or XML) if applicable.

Karate provides an elegant 'native-like' experience for placeholder substitution within strings or text content. This is useful in any situation where you need to concatenate dynamic string fragments to form content such as GraphQL or SQL.

The placeholder format defaults to angle-brackets, for example: `<replaceMe>`. Here is how to replace one placeholder at a time:

```cucumber
* def text = 'hello <foo> world'
* replace text.foo = 'bar'
* match text == 'hello bar world'
```

Karate makes it really easy to substitute multiple placeholders in a single, readable step as follows:

```cucumber
* def text = 'hello <one> world <two> bye'

* replace text
  | token | value   |
  | one   | 'cruel' |
  | two   | 'good'  |

* match text == 'hello cruel world good bye'
```

Note how strings have to be enclosed in quotes. This is so that you can mix expressions into text replacements as shown below. This example also shows how you can use a custom placeholder format instead of the default:

```cucumber
* def text = 'hello <one> world ${two} bye'
* def first = 'cruel'
* def json = { second: 'good' }

* replace text
    | token  | value       |
    | one    | first       |
    | ${two} | json.second |

* match text == 'hello cruel world good bye'
```
Refer to this file for a detailed example: [`replace.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/replace.feature)

## YAML Files
For those who may prefer [YAML](http://yaml.org) as a simpler way to represent data, Karate allows you to read YAML content from a [file](#reading-files) - and it will be auto-converted into JSON.

```cucumber
# yaml from a file (the extension matters), and the data-type of 'bar' would be JSON
* def bar = read('data.yaml')
```

### `yaml`
A very rare need is to be able to convert a string which happens to be in YAML form into JSON, and this can be done via the `yaml` type cast keyword. For example - if a response data element or downloaded file is YAML and you need to use the data in subsequent steps. Also see [type conversion](#type-conversion).

```cucumber
* text foo =
  """
  name: John
  input:
    id: 1
    subType: 
      name: Smith
      deleted: false
  """
# yaml to json type conversion  
* yaml foo = foo
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
```

## CSV Files
Karate can read `*.csv` files and will auto-convert them to JSON. A header row is always expected. See the section on [reading files](#reading-files) - and also this example [`dynamic-csv.feature`](karate-demo/src/test/java/demo/outline/dynamic-csv.feature), which shows off the convenience of [dynamic `Scenario Outline`-s](#dynamic-scenario-outline).

In rare cases you may want to use a csv-file as-is and *not* auto-convert it to JSON. A good example is when you want to use a CSV file as the [request-body](#request) for a file-upload. You could get by by renaming the file-extension to say `*.txt` but an alternative is to use the [`karate.readAsString()`](#read-file-as-string) API.

### `csv`
Just like [`yaml`](#yaml), you may occasionally need to [convert a string](#type-conversion) which happens to be in CSV form into JSON, and this can be done via the `csv` keyword.

```cucumber
* text foo =
    """
    name,type
    Billie,LOL
    Bob,Wild
    """
* csv bar = foo
* match bar == [{ name: 'Billie', type: 'LOL' }, { name: 'Bob', type: 'Wild' }]
```

## JavaScript Functions
JavaScript Functions are also 'native'. And yes, functions can take arguments.

> Standard JavaScript syntax rules apply, but the right-hand-side (or contents of the `*.js` file if applicable) should begin with the `function` keyword. This means that JavaScript comments are *not* supported if they appear *before* the function body. Also note that [ES6 arrow functions](https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Functions/Arrow_functions) 
are **not** supported. Finally, especially when using stand-alone `*.js` files, you can use `fn` as the function name, so that your IDE does not complain about JavaScript syntax errors, e.g. `function fn(x){ return x + 1 }`

```cucumber
* def greeter = function(title, name) { return 'hello ' + title + ' ' + name }
* assert greeter('Mr.', 'Bob') == 'hello Mr. Bob'
```

> When JavaScript executes in Karate, the built-in [`karate` object](#the-karate-object) provides some commonly used utility functions. And with [Karate expressions](#karate-expressions), you can "dive into" JavaScript without needing to define a function - and [conditional logic](#conditional-logic) is a good example.

### Java Interop
For more complex functions you are better off using the [multi-line](#multi-line-expressions) 'doc-string' approach. This example actually calls into existing Java code, and being able to do this opens up a whole lot of possibilities. The JavaScript interpreter will try to convert types across Java and JavaScript as smartly as possible. For e.g. JSON objects become Java `Map`-s, JSON arrays become Java `List`-s, and Java Bean properties are accessible (and update-able) using 'dot notation' e.g. '`object.name`'

```cucumber
* def dateStringToLong =
  """
  function(s) {
    var SimpleDateFormat = Java.type('java.text.SimpleDateFormat');
    var sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    return sdf.parse(s).time; // '.getTime()' would also have worked instead of '.time'
  } 
  """
* assert dateStringToLong("2016-12-24T03:39:21.081+0000") == 1482550761081
```

> More examples of Java interop and how to invoke custom code can be found in the section on [Calling Java](#calling-java).

The `call` keyword provides an [alternate way of calling JavaScript functions](#calling-javascript-functions) that have only one argument. The argument can be provided after the function name, without parentheses, which makes things slightly more readable (and less cluttered) especially when the solitary argument is JSON.

```cucumber
* def timeLong = call dateStringToLong '2016-12-24T03:39:21.081+0000'
* assert timeLong == 1482550761081

# a better example, with a JSON argument
* def greeter = function(name){ return 'Hello ' + name.first + ' ' + name.last + '!' }
* def greeting = call greeter { first: 'John', last: 'Smith' }
```

## Reading Files
Karate makes re-use of payload data, utility-functions and even other test-scripts as easy as possible. Teams typically define complicated JSON (or XML) payloads in a file and then re-use this in multiple scripts. Keywords such as [`set`](#set) and [`remove`](#remove) allow you to to 'tweak' payload-data to fit the scenario under test. You can imagine how this greatly simplifies setting up tests for boundary conditions. And such re-use makes it easier to re-factor tests when needed, which is great for maintainability.

> Note that the [`set` (multiple)](#set-multiple) keyword can build complex, nested JSON (or XML) from scratch in a data-driven manner, and you may not even need to read from files for many situations. Test data can be within the main flow itself, which makes scripts highly readable.

Reading files is achieved using the built-in JavaScript function called `read()`. By default, the file is expected to be in the same folder (package) and side-by-side with the `*.feature` file. But you can prefix the name with `classpath:` in which case the ['root' folder](#classpath) would be `src/test/java` (assuming you are using the [recommended folder structure](#folder-structure)).

Prefer [`classpath:`](#classpath) when a file is expected to be heavily re-used all across your project.  And yes, relative paths will work.

```cucumber
# json
* def someJson = read('some-json.json')
* def moreJson = read('classpath:more-json.json')

# xml
* def someXml = read('../common/my-xml.xml')

# import yaml (will be converted to json)
* def jsonFromYaml = read('some-data.yaml')

# csv (will be converted to json)
* def jsonFromCsv = read('some-data.csv')

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

When a *called* feature depends on some side-by-side resources such as JSON or JS files, you can use the `this:` prefix to ensure that relative paths work correctly - because by default Karate calculates relative paths from the "root" feature or the top-most "caller".

```cucumber
* def data = read('this:payload.json')
```

If a file does not end in `.json`, `.xml`, `.yaml`, `.js`, `.csv` or `.txt`, it is treated as a stream - which is typically what you would need for [`multipart`](#multipart-field) file uploads.

```cucumber
* def someStream = read('some-pdf.pdf')
```

> The `.graphql` and `.gql` extensions are also recognized (for GraphQL) but are handled the same way as `.txt` and treated as a string.

For JSON and XML files, Karate will evaluate any [embedded expressions](#embedded-expressions) on load. This enables more concise tests, and the file can be re-usable in multiple, data-driven tests.

Since it is internally implemented as a JavaScript function, you can mix calls to `read()` freely wherever JavaScript expressions are allowed:

```cucumber
* def someBigString = read('first.txt') + read('second.txt')
```

> Tip: you can even use JS expressions to dynamically choose a file based on some condition: `* def someConfig = read('my-config-' + someVariable + '.json')`. Refer to [conditional logic](#conditional-logic) for more ideas.

And a very common need would be to use a file as the [`request`](#request) body:

```cucumber
Given request read('some-big-payload.json')
```

Or in a [`match`](#match):

```cucumber
And match response == read('expected-response-payload.json')
```

The rarely used `file:` prefix is also supported. You could use it for 'hard-coded' absolute paths in dev mode, but is obviously not recommended for CI test-suites. A good example of where you may need this is if you programmatically write a file to the `target` folder, and then you can read it like this:

```cucumber
* def payload = read('file:target/large.xml')
```

To summarize the possible prefixes:

Prefix | Description
------ | -----------
`classpath:` | relative to the [classpath](#classpath), recommended for re-usable features 
`file:` | do not use this unless you know what you are doing, see above
`this:` | when in a *called* feature, ensure that files are resolved relative to the current feature file

Take a look at the [Karate Demos](karate-demo) for real-life examples of how you can use files for validating HTTP responses, like this one: [`read-files.feature`](karate-demo/src/test/java/demo/read/read-files.feature).

### Read File As String
In some rare cases where you don't want to auto-convert JSON, XML, YAML or CSV, and just get the raw string content (without having to re-name the file to end with `.txt`) - you can use the [`karate.readAsString()`](#karate-readasstring) API. Here is an example of using a CSV file as the request-body:

```cucumber
Given path 'upload'
And header Content-Type = 'text/csv'
And request karate.readAsString('classpath:my.csv')
When method post
Then status 202
```

## Type Conversion
> Best practice is to stick to using only [`def`](#def) unless there is a very good reason to do otherwise.

Internally, Karate will auto-convert JSON (and even XML) to Java `Map` objects. And JSON arrays would become Java `List`-s. But you will never need to worry about this internal data-representation most of the time.

In some rare cases, for e.g. if you acquired a string from some external source, or if you generated JSON (or XML) by concatenating text or using [`replace`](#replace), you may want to convert a string to JSON and vice-versa. You can even perform a conversion from XML to JSON if you want.

One example of when you may want to convert JSON (or XML) to a string is when you are passing a payload to custom code via [Java interop](#calling-java). Do note that when passing JSON, the default `Map` and `List` representations should suffice for most needs ([see example](karate-demo/src/test/java/demo/java/cats-java.feature)), and using them would avoid un-necessary string-conversion.

So you have the following type markers you can use instead of [`def`](#def) (or the rarely used [`text`](#text)). The first four below are best explained in this example file: [`type-conv.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/type-conv.feature).

* <a name="type-string"><code>string</code></a> - convert JSON or any other data-type (except XML) to a string
* <a name="type-json"><code>json</code></a> - convert XML, a map-like or list-like object, a string, or even a Java object into JSON
* <a name="type-xml"><code>xml</code></a> - convert JSON, a map-like object, a string, or even a Java object into XML
* <a name="type-xmlstring"><code>xmlstring</code></a> - specifically for converting the map-like Karate internal representation of XML into a string
* <a name="type-csv"><code>csv</code></a> - convert a CSV string into JSON, see [`csv`](#csv)
* <a name="type-yaml"><code>yaml</code></a> - convert a YAML string into JSON, see [`yaml`](#yaml)
* <a name="type-bytes"><code>bytes</code></a> - convert to a byte-array, useful for binary payloads or comparisons, see [example](karate-demo/src/test/java/demo/websocket/echo.feature)
* <a name="type-copy"><code>copy</code></a> - to clone a given payload variable reference (JSON, XML, Map or List), refer: [`copy`](#copy)

If you want to 'pretty print' a JSON or XML value with indenting, refer to the documentation of the [`print`](#print) keyword.

### Floats and Integers
While converting a number to a string is easy (just concatenate an empty string e.g. `myInt + ''`), in some rare cases, you may need to convert a string to a number. You can do this by multiplying by `1` or using the built-in JavaScript `parseInt()` function:

```cucumber
* def foo = '10'
* string json = { bar: '#(1 * foo)' }
* match json == '{"bar":10.0}'

* string json = { bar: '#(parseInt(foo))' }
* match json == '{"bar":10.0}'
```

As per the JSON spec, all numeric values are treated as doubles, so for integers - it really doesn't matter if there is a decimal point or not. In fact it may be a good idea to slip doubles instead of integers into some of your tests ! Anyway, there are times when you may want to force integers (perhaps for cosmetic reasons) and you can easily do so using the 'double-tilde' [short-cut: '`~~`'](http://rocha.la/JavaScript-bitwise-operators-in-practice).

```cucumber
* def foo = '10'
* string json = { bar: '#(~~foo)' }
* match json == '{"bar":10}'

# unfortunately JS math always results in a double
* def foo = 10
* string json = { bar: '#(1 * foo)' }
* match json == '{"bar":10.0}'

# but you can easily coerce to an integer if needed
* string json = { bar: '#(~~(1 * foo))' }
* match json == '{"bar":10}'
```

### Large Numbers
Sometimes when dealing with very large numbers, the JS engine may mangle the number into scientific notation:

```cucumber
* def big = 123123123123
* string json = { num: '#(big)' }
* match json == '{"num":1.23123123123E11}'
```

This can be easily solved by using `java.math.BigDecimal`:

```cucumber
* def big = new java.math.BigDecimal(123123123123)
* string json = { num: '#(big)' }
* match json == '{"num":123123123123}'
```

# Karate Expressions
Before we get to the HTTP keywords, it is worth doing a recap of the various 'shapes' that the right-hand-side of an assignment statement can take:

 Example | Shape | Description
-------- | ----- | -----------
`* def foo = 'bar'` | JS | simple strings, numbers or booleans
`* def foo = 'bar' + baz[0]` | JS | any valid JavaScript expression, and variables can be mixed in, another example: `bar.length + 1`
`* def foo = { bar: '#(baz)' }` | JSON | anything that starts with a `{` or a `[` is parsed as JSON, use [`text`](#text) instead of [`def`](#def) if you need to suppress the default behavior
`* def foo = ({ bar: baz })` | JS | [enclosed JavaScript](#enclosed-javascript), the result of which is exactly equivalent to the above
`* def foo = <foo>bar</foo>` | XML | anything that starts with a `<` is parsed as XML, use [`text`](#text) instead of [`def`](#def) if you need to suppress the default behavior
`* def foo = function(arg){ return arg + bar }` | JS Fn | anything that starts with `function(...){` is parsed as a JS function.
`* def foo = read('bar.json')` | JS | using the built-in [`read()`](#reading-files) function
`* def foo = $.bar[0]` | JsonPath | short-cut JsonPath on the [`response`](#response)
`* def foo = /bar/baz` | XPath | short-cut XPath on the [`response`](#response)
`* def foo = get bar $..baz[?(@.ban)]` | [`get`](#get) JsonPath | [JsonPath](https://github.com/json-path/JsonPath#path-examples) on the variable `bar`, you can also use [`get[0]`](#get-plus-index) to get the first item if the JsonPath evaluates to an array - especially useful when using wildcards such as `[*]` or [filter-criteria](#jsonpath-filters)
`* def foo = $bar..baz[?(@.ban)]` | $var.JsonPath | [convenience short-cut](#get-short-cut) for the above
`* def foo = get bar count(/baz//ban)` | [`get`](#get) XPath | XPath on the variable `bar`
`* def foo = karate.pretty(bar)` | JS | using the [built-in `karate` object](#the-karate-object) in JS expressions
`* def Foo = Java.type('com.mycompany.Foo')` | JS-Java | [Java Interop](#java-interop), and even package-name-spaced one-liners like `java.lang.System.currentTimeMillis()` are possible
`* def foo = call bar { baz: '#(ban)' }` | [`call`](#call) | or [`callonce`](#callonce), where expressions like [`read('foo.js')`](#reading-files) are allowed as the object to be called or the argument
`* def foo = bar({ baz: ban })` | JS | equivalent to the above, JavaScript function invocation

# Core Keywords
They are `url`, `path`, `request`, `method` and `status`.

These are essential HTTP operations, they focus on setting one (un-named or 'key-less') value at a time and therefore don't need an `=` sign in the syntax.

## `url`
```cucumber
Given url 'https://myhost.com/v1/cats'
```
A URL remains constant until you use the `url` keyword again, so this is a good place to set-up the 'non-changing' parts of your REST URL-s.

A URL can take expressions, so the approach below is legal.  And yes, variables can come from global [config](#configuration).
```cucumber
Given url 'https://' + e2eHostName + '/v1/api'
```

If you are trying to build dynamic URLs including query-string parameters in the form: `http://myhost/some/path?foo=bar&search=true` - please refer to the [`param`](#param) keyword.

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

> Important: If you attempt to build a URL in the form `?myparam=value` by using `path` the `?` will get encoded into `%3F`. Use either the [`param`](#param) keyword, e.g.: `* param myparam = 'value'` or [`url`](#url): `* url 'http://example.com/v1?myparam'`

## `request`
In-line JSON:
```cucumber
Given request { name: 'Billie', type: 'LOL' }
```
In-line XML:
```cucumber
And request <cat><name>Billie</name><type>Ceiling</type></cat>
```
From a [file](#reading-files) in the same package.  Use the `classpath:` prefix to load from the [classpath](#classpath) instead.
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

Sending a [file](#reading-files) as the entire binary request body is easy (note that [`multipart`](#multipart-file) is different):

```cucumber
Given path 'upload'
And request read('my-image.jpg')
When method put
Then status 200
```

## `method`
The HTTP verb - `get`, `post`, `put`, `delete`, `patch`, `options`, `head`, `connect`, `trace`.

Lower-case is fine.
```cucumber
When method post
```

It is worth internalizing that during test-execution, it is upon the `method` keyword that the actual HTTP request is issued. Which suggests that the step should be in the `When` form, for example: `When method post`. And steps that follow should logically be in the `Then` form. Also make sure that you complete the set up of things like [`url`](#url), [`param`](#param), [`header`](#header), [`configure`](#configure) etc. *before* you fire the `method`.

```cucumber
# set headers or params (if any) BEFORE the method step
Given header Accept = 'application/json'
When method get
# the step that immediately follows the above would typically be:
Then status 200
```

Although rarely needed, variable references or [expressions](#karate-expressions) are also supported:

```cucumber
* def putOrPost = (someVariable == 'dev' ? 'put' : 'post')
* method putOrPost
```

## `status`
This is a shortcut to assert the HTTP response code.
```cucumber
Then status 200
```
And this assertion will cause the test to fail if the HTTP response code is something else.

See also [`responseStatus`](#responsestatus) if you want to do some complex assertions against the HTTP status code.

# Keywords that set key-value pairs
They are `param`, `header`, `cookie`, `form field` and `multipart field`. 

The syntax will include a '=' sign between the key and the value.  The key should not be within quotes.

> To make dynamic data-driven testing easier, the following keywords also exist: [`params`](#params), [`headers`](#headers), [`cookies`](#cookies-json) and [`form fields`](#form-fields). They use JSON to build the relevant parts of the HTTP request.

## `param` 
Setting query-string parameters:
```cucumber
Given param someKey = 'hello'
And param anotherKey = someVariable
```

The above would result in a URL like: `http://myhost/mypath?someKey=hello&anotherKey=foo`. Note that the `?` and `&` will be automatically inserted.

Multi-value params are also supported:
```cucumber
* param myParam = 'foo', 'bar'
```

You can also use JSON to set multiple query-parameters in one-line using [`params`](#params) and this is especially useful for dynamic data-driven testing.

## `header`

You can use [functions](#calling-javascript-functions) or [expressions](#karate-expressions):
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
* configure headers = { 'Content-Type': 'application/xml' }
```

Note that `Content-Type` had to be enclosed in quotes in the JSON above because the "`-`" (hyphen character) would cause problems otherwise. Also note that "`; charset=UTF-8`" would be appended to the `Content-Type` header that Karate sends by default, and in some rare cases, you may need to suppress this behavior completely. You can do so by setting the `charset` to null via the [`configure`](#configure) keyword:

```cucumber
* configure charset = null
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

Note that any cookies returned in the HTTP response would be automatically set for any future requests. This mechanism works by calling [`configure cookies`](#configure) behind the scenes and if you need to stop auto-adding cookies for future requests, just do this:

```cucumber
* configure cookies = null
```

Also refer to the built-in variable [`responseCookies`](#responsecookies) for how you can access and perform assertions on cookie data values.

## `form field` 
HTML form fields would be URL-encoded when the HTTP request is submitted (by the [`method`](#method) step). You would typically use these to simulate a user sign-in and then grab a security token from the [`response`](#response).

Note that the `Content-Type` header will be automatically set to: `application/x-www-form-urlencoded`. You just need to do a normal `POST` (or `GET`).

For example:

```cucumber
Given path 'login'
And form field username = 'john'
And form field password = 'secret'
When method post
Then status 200
And def authToken = response.token
```

A good example of the use of `form field` for a typical sign-in flow is this OAuth 2 demo: [`oauth2.feature`](karate-demo/src/test/java/demo/oauth/oauth2.feature).

Multi-values are supported the way you would expect (e.g. for simulating check-boxes and multi-selects):
```cucumber
* form field selected = 'apple', 'orange'
```

You can also dynamically set multiple fields in one step using the [`form fields`](#form-fields) keyword.

## `multipart field`
Use this for building multipart named (form) field requests. This is typically combined with `multipart file` as shown below.  

> Multiple fields can be set in one step using [`multipart fields`](#multipart-fields).

## `multipart file`

 ```cucumber
Given multipart file myFile = { read: 'test.pdf', filename: 'upload-name.pdf', contentType: 'application/pdf' }
And multipart field message = 'hello world'
When method post
Then status 200
```

It is important to note that `myFile` above is the "field name" within the `multipart/form-data` request payload. This roughly corresponds to a [`cURL`](https://curl.haxx.se/docs/manpage.html#-F) argument of `-F @myFile=test.pdf`.

> `multipart` file uploads can be tricky, and hard to get right. If you get stuck and ask a question on [Stack Overflow](https://stackoverflow.com/questions/tagged/karate), make sure you provide a `cURL` command that works - or else it would be very difficult for anyone to troubleshoot what you could be doing wrong.

Also note that `multipart file` takes a JSON argument so that you can easily set the `filename` and the `contentType` (mime-type) in one step.

* `read`: the name of a file, and the [`classpath:`](#reading-files) prefix also is allowed. mandatory unless `value` is used, see below.
* `value`: alternative to `read` in rare cases where something like a JSON or XML file is being uploaded and you want to create it dynamically.
* `filename`: optional, if not specified there will be no `filename` attribute in `Content-Disposition` 
* `contentType`: optional, will default to `application/octet-stream`

When 'multipart' content is involved, the `Content-Type` header of the HTTP request defaults to `multipart/form-data`.
You can over-ride it by using the [`header`](#header) keyword before the `method` step.  Look at
[`multipart entity`](#multipart-entity) for an example.

Also refer to this [demo example](karate-demo) for a working example of multipart file uploads: [`upload.feature`](karate-demo/src/test/java/demo/upload/upload.feature).

You can also dynamically set multiple files in one step using [`multipart files`](#multipart-files).

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

# Multi-Param Keywords
## Keywords that set multiple key-value pairs in one step
`params`, `headers`, `cookies`, `form fields`, `multipart fields` and `multipart files` take a single JSON argument (which can be in-line or a variable reference), and this enables certain types of dynamic data-driven testing, especially because any JSON key with a `null` value will be ignored. Here is a good example in the demos: [`dynamic-params.feature`](karate-demo/src/test/java/demo/search/dynamic-params.feature)

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

## `multipart fields`
```cucumber
And multipart fields { message: 'hello world', json: { foo: 'bar' } }
```

See also [`multipart field`](#multipart-field).

## `multipart files`
The single JSON argument needs to be in the form `{ field1: { read: 'file1.ext' }, field2: { read: 'file2.ext' } }` where each nested JSON is in the form expected by [`multipart file`](#multipart-file)

```cucumber
* def json = {}
* set json.myFile1 = { read: 'test1.pdf', filename: 'upload-name1.pdf', contentType: 'application/pdf' }
# if you have dynamic keys you can do this
* def key = 'myFile2'
* json[key] = { read: 'test2.pdf', filename: 'upload-name2.pdf', contentType: 'application/pdf' }
And multipart files json
```

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

A [working example](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/soap.feature) of calling a SOAP service can be found within the Karate project test-suite. Refer to the [demos](karate-demo) for another example: [`soap.feature`](karate-demo/src/test/java/demo/soap/soap.feature).

More examples are available that showcase various ways of parameter-izing and dynamically manipulating SOAP requests in a data-driven fashion. Karate is quite flexible, and provides multiple options for you to evolve patterns that fit your environment, as you can see here: [`xml.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/xml/xml.feature).

## `retry until`
Karate has built-in support for re-trying an HTTP request until a certain condition has been met. The default setting for the max retry-attempts is 3 with a poll interval of 3000 milliseconds (3 seconds). If needed, this can be changed by using [`configure`](#configure) - any time during a test, or set globally via [`karate-config.js`](#configuration)

```cucumber
* configure retry = { count: 10, interval: 5000 }
```

The `retry` keyword is designed to extend the existing [`method`](#method) syntax (and should appear **before** a `method` step) like so:

```cucumber
Given url demoBaseUrl
And path 'greeting'
And retry until response.id > 3
When method get
Then status 200
```

Any JavaScript expression that uses any variable in scope can be placed after the "`retry until`" part. So you can refer to the [`response`](#response), [`responseStatus`](#responsestatus) or even [`responseHeaders`](#responseheaders) if needed. For example:

```cucumber
Given url demoBaseUrl
And path 'greeting'
And retry until responseStatus == 200 && response.id > 3
When method get
```

> Note that it has to be a pure JavaScript expression - which means that `match` syntax such as `contains` will *not* work. But you can easily achieve any complex logic by [using the JS API](https://stackoverflow.com/a/55823180/143475).

Refer to [`polling.feature`](karate-demo/src/test/java/demo/polling/polling.feature) for an example, and also see the alternative way to achieve [polling](#polling).

# `configure`
## Managing Headers, SSL, Timeouts and HTTP Proxy
You can adjust configuration settings for the HTTP client used by Karate using this keyword. The syntax is similar to [`def`](#def) but instead of a named variable, you update configuration. Here are the configuration keys supported:

 Key | Type | Description
------ | ---- | ---------
`headers` | JSON / JS function | See [`configure headers`](#configure-headers)
`cookies` | JSON / JS function | Just like `configure headers`, but for cookies. You will typically never use this, as response cookies are auto-added to all future requests. If you need to clear cookies at any time, just do `configure cookies = null`
`logPrettyRequest` | boolean | Pretty print the request payload JSON or XML with indenting (default `false`)
`logPrettyResponse` | boolean | Pretty print the response payload JSON or XML with indenting (default `false`)
`printEnabled` | boolean | Can be used to suppress the [`print`](#print) output when not in 'dev mode' by setting as `false` (default `true`)
`report` | JSON / boolean | see [report verbosity](#report-verbosity)
`afterScenario` | JS function | Will be called [after every `Scenario`](#hooks) (or `Example` within a `Scenario Outline`), refer to this example: [`hooks.feature`](karate-demo/src/test/java/demo/hooks/hooks.feature)
`afterFeature` | JS function | Will be called [after every `Feature`](#hooks), refer to this example: [`hooks.feature`](karate-demo/src/test/java/demo/hooks/hooks.feature)
`ssl` | boolean | Enable HTTPS calls without needing to configure a trusted certificate or key-store.
`ssl` | string | Like above, but force the SSL algorithm to one of [these values](http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#SSLContext). (The above form internally defaults to `TLS` if simply set to `true`).
`ssl` | JSON | see [X509 certificate authentication](#x509-certificate-authentication)
`followRedirects` | boolean | Whether the HTTP client automatically follows redirects - (default `true`), refer to this [example](karate-demo/src/test/java/demo/redirect/redirect.feature).
`connectTimeout` | integer | Set the connect timeout (milliseconds). The default is 30000 (30 seconds). Note that for `karate-apache`, this sets the [socket timeout](https://stackoverflow.com/a/22722260/143475) to the same value as well.
`readTimeout` | integer | Set the read timeout (milliseconds). The default is 30000 (30 seconds).
`proxy` | string | Set the URI of the HTTP proxy to use.
`proxy` | JSON | For a proxy that requires authentication, set the `uri`, `username` and `password`, see example below. Also a `nonProxyHosts` key is supported which can take a list for e.g. `{ uri: 'http://my.proxy.host:8080',  nonProxyHosts: ['host1', 'host2']}`
`localAddress` | string | see [`karate-gatling`](karate-gatling#configure-localaddress)
`charset` | string | The charset that will be sent in the request `Content-Type` which defaults to `utf-8`. You typically never need to change this, and you can over-ride (or disable) this per-request if needed via the [`header`](#header) keyword ([example](karate-demo/src/test/java/demo/headers/content-type.feature)).
`retry` | JSON | defaults to `{ count: 3, interval: 3000 }` - see [`retry until`](#retry-until)
`callSingleCache` | JSON | defaults to `{ minutes: 0, dir: 'target' }` - see [`configure callSingleCache`](#configure-callsinglecache)
`outlineVariablesAuto` | boolean | defaults to `true`, whether each key-value pair in the `Scenario Outline` example-row is automatically injected into the context as a variable (and not just `__row`), see [`Scenario Outline` Enhancements](#scenario-outline-enhancements)
 `lowerCaseResponseHeaders` | boolean | Converts every key and value in the [`responseHeaders`](#responseheaders) to lower-case which makes it easier to validate for e.g. using [`match header`](#match-header) (default `false`) [(example)](karate-demo/src/test/java/demo/headers/content-type.feature).
 `abortedStepsShouldPass` | boolean | defaults to `false`, whether steps after a [`karate.abort()`](#karate-abort) should be marked as `PASSED` instead of `SKIPPED` - this can impact the behavior of 3rd-party reports, see [this issue](https://github.com/intuit/karate/issues/755) for details
`logModifier` | Java Object | See [Log Masking](#log-masking)
`httpClientClass` | string | See [`karate-mock-servlet`](karate-mock-servlet)
`httpClientInstance` | Java Object | See [`karate-mock-servlet`](karate-mock-servlet)
`userDefined` | JSON | See [`karate-mock-servlet`](karate-mock-servlet)
`responseHeaders` | JSON / JS function | See [`karate-netty`](karate-netty#configure-responseheaders)
`cors` | boolean | See [`karate-netty`](karate-netty#configure-cors)
`driver` | JSON | See [UI Automation](karate-core)
`driverTarget` | JSON / Java Object | See [`configure driverTarget`](karate-core#configure-drivertarget)

> If you are mixing Karate into an existing Java project via Maven or Gradle, it can happen that the version of the Apache HTTP client used by Karate conflicts with something in the existing classpath. This can cause `* configure ssl = true` to fail. Read [this answer on Stack Overflow](https://stackoverflow.com/a/52396293/143475) for more details.

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

## `configure` globally 
If you need to set any of these "globally" you can easily do so using [the `karate` object](#the-karate-object) in [`karate-config.js`](#configuration) - for e.g:

```js
  karate.configure('ssl', true);
  karate.configure('readTimeout', 5000);
```

In rare cases where you need to add nested non-JSON data to the `configure` value, you have to play by the [rules](#restrictions-on-global-variables) that apply within [`karate-config.js`](#karate-configjs). Here is an example of performing a [`configure driver`](karate-core#configure-driver) step in JavaScript:

```js
  var LM = Java.type('com.mycompany.MyHttpLogModifier');
  var driverConfig = { type:'chromedriver', start: false, webDriverUrl:'https://user:password@zalenium.net/wd/hub' };
  driverConfig.httpConfig = karate.toMap({ logModifier: LM.INSTANCE });
  karate.configure('driver', driverConfig);
```

### Report Verbosity
By default, Karate will add logs to the report output so that HTTP requests and responses appear in-line in the HTML reports. There may be cases where you want to suppress this to make the reports "lighter" and easier to read.

The configure key here is `report` and it takes a JSON value. For example:

```cucumber
* configure report = { showLog: true, showAllSteps: false }
```

 `report` | Type | Description
------ | ---- | ---------
`showLog` | boolean | HTTP requests and responses (including headers) will appear in the HTML report, default `true`
`showAllSteps` | boolean | If `false`, any step that starts with `*` instead of `Given`, `When`, `Then` etc. will *not* appear in the HTML report. The [`print`](#print) step is an exception. Default `true`.

You can 'reset' default settings by using the following short-cut:

```cucumber
# reset to defaults
* configure report = true
```

Since you can use `configure` any time within a test, you have control over which requests or steps you want to show / hide. This can be convenient if a particular call results in a huge response payload.

The following short-cut is also supported which will disable all logs:

```cucumber
* configure report = false
```
#### `@report=false`

When you use a re-usable feature that has [commonly used utilities](multiple-functions-in-one-file), you may want to hide this completely from the HTML reports. The special [tag](#tags) `@report=false` can be used, and it can even be used only for a single `Scenario`:

```cucumber
@ignore @report=false
Feature:

Scenario:
# some re-usable steps
```

### Log Masking
In cases where you want to "mask" values which are sensitive from a security point of view from the output files, logs and HTML reports, you can implement the [`HttpLogModifier`](karate-core/src/main/java/com/intuit/karate/http/HttpLogModifier.java) and tell Karate to use it via the [`configure`](#configure) keyword. Here is an [example](karate-demo/src/test/java/demo/headers/DemoLogModifier.java) of an implementation. For performance reasons, you can implement  `enableForUri()` so that this "activates" only for some URL patterns.

Instantiating a Java class and using this in a test is easy (see [example](karate-demo/src/test/java/demo/headers/headers-masking.feature)):

```cucumber
# if this was in karate-config.js, it would apply "globally"
* def LM = Java.type('demo.headers.DemoLogModifier')
* configure logModifier = new LM()
```

Or globally in [`karate-config.js`](#karate-configjs)

```js
var LM = Java.type('demo.headers.DemoLogModifier');
karate.configure('logModifier', new LM());
```

Since `karate-config.js` is processed for every `Scenario`, you can use a singleton instead of calling `new` every time. Something like this:

```js
var LM = Java.type('demo.headers.DemoLogModifier');
karate.configure('logModifier', LM.INSTANCE);
```

#### Log Masking Caveats
The `logModifier` will not affect the [`call`](#call) argument that Karate outputs by default in the HTML / reports. This means that if you pass a sensitive value as part of a JSON argument (even in a [data driven `call` loop](#data-driven-features)) - it *will* appear in the report !

The recommendation is to *not* have sensitive values as part of your core test-flows. This is what most teams would be doing anyway, and there are three points to keep in mind:

* sensitive variables are typically set up in [`karate-config.js`](#configuration), they will be available "globally" and never need to be passed as `call` arguments
* all variables ["visible" in a "calling" feature](#call) will be available in the "called" feature. So if you really wanted to pass a sensitive value into a `call` on the fly, just use [`def`](#def) (or you can use [`karate.set()`](#karate-set) if within JS) to initialize a variable - and then proceed to make a `call` without arguments.
* you can of course hide the entire `call` from the report by using the [`@report=false`](#reportfalse) annotation

### System Properties for SSL and HTTP proxy
For HTTPS / SSL, you can also specify a custom certificate or trust store by [setting Java system properties](https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#InstallationAndCustomization). And similarly - for [specifying the HTTP proxy](https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html).

### X509 Certificate Authentication

Also referred to as "mutual auth" - if your API requires that clients present an X509 certificate for authentication, Karate supports this via JSON as the `configure ssl` value.  The following parameters are supported:

Key | Type | Required? | Description
---- | ----- | ------ | ----------
`keyStore` | string | optional | path to file containing public and private keys for your client certificate.
`keyStorePassword` | string | optional | password for keyStore file.
`keyStoreType` | string | optional | Format of the keyStore file.  Allowed keystore types are as described in the [Java `KeyStore` docs](https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#KeyStore).
`trustStore` | string | optional | path to file containing the trust chain for your server certificate.
`trustStorePassword` | string | optional | password for trustStore file.
`trustStoreType` | string | optional | Format of the trustStore file.  Allowed keystore types are as described in the [Java `KeyStore` docs](https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#KeyStore).
`trustAll` | boolean | optional | if all server certificates should be considered trusted. Default value is `false`. If `true` will allow self-signed certificates. If `false`, will expect the whole chain in the `trustStore` or use what is available in the environment.
`algorithm` | string | optional | force the SSL algorithm to one of [these values](http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#SSLContext). Default is `TLS`.  

Example:  
```
# enable X509 certificate authentication with PKCS12 file 'certstore.pfx' and password 'certpassword'
* configure ssl = { keyStore: 'classpath:certstore.pfx', keyStorePassword: 'certpassword', keyStoreType: 'pkcs12' }
```

```
# trust all server certificates, in the feature file
* configure ssl = { trustAll: true }
```

```
# trust all server certificates, global configuration in 'karate-config.js'
karate.configure('ssl', { trustAll: true });
```

For end-to-end examples in the Karate demos, look at the files in [this folder](karate-demo/src/test/java/ssl).

# Payload Assertions
## Prepare, Mutate, Assert.
Now it should be clear how Karate makes it easy to express JSON or XML. If you [read from a file](#reading-files), the advantage is that multiple scripts can re-use the same data.

Once you have a [JSON or XML object](#native-data-types), Karate provides multiple ways to manipulate, extract or transform data. And you can easily assert that the data is as expected by comparing it with another JSON or XML object.

## `match`
### Payload Assertions / Smart Comparison
The `match` operation is smart because white-space does not matter, and the order of keys (or data elements) does not matter. Karate is even able to [ignore fields you choose](#ignore-or-validate) - which is very useful when you want to handle server-side dynamically generated fields such as UUID-s, time-stamps, security-tokens and the like.

The match syntax involves a double-equals sign '==' to represent a comparison (and not an assignment '=').

Since `match` and `set` go well together, they are both introduced in the examples in the section below.

## `set`
Game, `set` and `match` - Karate !

### JS for JSON
Before you consider the `set` keyword - note that for simple JSON update operations, you can use [`eval`](#eval) - especially useful when the path you are trying to mutate is dynamic. Since the `eval` keyword can be omitted when operating on variables using JavaScript, this leads to very concise code:

```cucumber
* def myJson = { a: '1' }
* myJson.b = 2
* match myJson == { a: '1', b: 2 }
```

Refer to [`eval`](#eval) for more / advanced examples.

### Manipulating Data
Setting values on JSON documents is simple using the `set` keyword.

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

# omit the array index to append
* set myJson.zee[] = 6
* match myJson == { foo: 'world', hey: 'ho', zee: [5, 6] }

# nested json ? no problem
* set myJson.cat = { name: 'Billie' }
* match myJson == { foo: 'world', hey: 'ho', zee: [5, 6], cat: { name: 'Billie' } }

# and for match - the order of keys does not matter
* match myJson == { cat: { name: 'Billie' }, hey: 'ho', foo: 'world', zee: [5, 6] }

# you can ignore fields marked with '#ignore'
* match myJson == { cat: '#ignore', hey: 'ho', foo: 'world', zee: [5, 6] }
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

### `match` and variables
In case you were wondering, variables (and even expressions) are supported on the right-hand-side. So you can compare 2 JSON (or XML) payloads if you wanted to:
```cucumber
* def foo = { hello: 'world', baz: 'ban' }
* def bar = { baz: 'ban', hello: 'world' }
* match foo == bar
```

If you are wondering about the finer details of the `match` syntax, the Left-Hand-Side has to be either a 
* variable name - e.g. `foo`
* a 'named' JsonPath or XPath expression - e.g. `foo[0].bar` or `foo[*].bar`
    * note that this cannot be "dynamic" (with in-line variables) so [use an extra step](#get) if needed
* any valid function or method call - e.g. `foo.bar()` or `foo.bar('hello').baz`
* or anything wrapped in parentheses which will be evaluated as JavaScript - e.g. `(foo + bar)` or `(42)`

And the right-hand-side can be any valid [Karate expression](#karate-expressions). Refer to the section on [JsonPath short-cuts](#jsonpath-short-cuts) for a deeper understanding of 'named' JsonPath expressions in Karate.

### `match !=` (not equals)
The 'not equals' operator `!=` works as you would expect:

```cucumber
* def test = { foo: 'bar' }
* match test != { foo: 'baz' }
```

> You typically will *never* need to use the `!=` (not-equals) operator ! Use it sparingly, and only for string, number or simple payload comparisons.

### `set` multiple
Karate has an elegant way to set multiple keys (via path expressions) in one step. For convenience, non-existent keys (or array elements) will be created automatically. You can find more JSON examples here: [`js-arrays.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/js-arrays.feature).

```cucumber
* def cat = { name: '' }

* set cat
  | path   | value |
  | name   | 'Bob' |
  | age    | 5     |

* match cat == { name: 'Bob', age: 5 }
```

One extra convenience for JSON is that if the variable itself (which was `cat` in the above example) does not exist, it will be created automatically. You can even create (or modify existing) JSON arrays by using multiple columns.

```cucumber
* set foo
  | path | 0     | 1     |
  | bar  | 'baz' | 'ban' |

* match foo == [{ bar: 'baz' }, { bar: 'ban' }]
```

If you have to set a bunch of deeply nested keys, you can move the parent path to the top, next to the `set` keyword and save a lot of typing ! Note that this is not supported for "arrays" like above, and you can have only one `value` column.

```cucumber
* set foo.bar
  | path   | value |
  | one    | 1     |
  | two[0] | 2     |
  | two[1] | 3     |

* match foo == { bar: { one: 1, two: [2, 3] } }
```

The same concept applies to XML and you can build complicated payloads from scratch in just a few, extremely readable lines. The `value` column can take expressions, *even* XML chunks. You can find more examples here: [`xml.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/xml/xml.feature).

```cucumber
* set search /acc:getAccountByPhoneNumber
  | path                        | value |
  | acc:phone/@foo              | 'bar' |
  | acc:phone/acc:number[1]     | 1234  |
  | acc:phone/acc:number[2]     | 5678  |     
  | acc:phoneNumberSearchOption | 'all' |

* match search ==
  """
  <acc:getAccountByPhoneNumber>
      <acc:phone foo="bar">
          <acc:number>1234</acc:number>
          <acc:number>5678</acc:number>
      </acc:phone>
      <acc:phoneNumberSearchOption>all</acc:phoneNumberSearchOption>        
  </acc:getAccountByPhoneNumber>
  """
```

## `remove`
This is like the opposite of [`set`](#set) if you need to remove keys or data elements from JSON or XML instances. You can even remove JSON array elements by index.
```cucumber
* def json = { foo: 'world', hey: 'ho', zee: [1, 2, 3] }
* remove json.hey
* match json == { foo: 'world', zee: [1, 2, 3] }
* remove json $.zee[1]
* match json == { foo: 'world', zee: [1, 3] }
```

> For JSON, you can also use [`eval`](#eval) instead of `remove`, useful when the path you are trying to mutate is dynamic.

`remove` works for XML elements as well:
```cucumber
* def xml = <foo><bar><hello>world</hello></bar></foo>
* remove xml/foo/bar/hello
* match xml == <foo><bar/></foo>
* remove xml /foo/bar
* match xml == <foo/>
```

Also take a look at how a special case of [embedded-expressions](#embedded-expressions) can remove key-value pairs from a JSON (or XML) payload: [Remove if Null](#remove-if-null).

## Fuzzy Matching
### Ignore or Validate
When expressing expected results (in JSON or [XML](#match-and-xml)) you can mark some fields to be ignored when the match (comparison) is performed.  You can even use a regular-expression so that instead of checking for equality, Karate will just validate that the actual value conforms to the expected pattern.

This means that even when you have dynamic server-side generated values such as UUID-s and time-stamps appearing in the response, you can still assert that the full-payload matched in one step.

```cucumber
* def cat = { name: 'Billie', type: 'LOL', id: 'a9f7a56b-8d5c-455c-9d13-808461d17b91' }
* match cat == { name: '#ignore', type: '#regex [A-Z]{3}', id: '#uuid' }
# this will fail
# * match cat == { name: '#ignore', type: '#regex .{2}', id: '#uuid' }	
```

> Note that regex escaping has to be done with a *double* back-slash - for e.g: `'#regex a\\.dot'` will match `'a.dot'`

The supported markers are the following:

Marker | Description
------ | -----------
`#ignore` | Skip comparison for this field even if the data element or JSON key is present
`#null` | Expects actual value to be `null`, and the data element or JSON key *must* be present
`#notnull` | Expects actual value to be not-`null`
`#present` | Actual value can be any type or *even* `null`, but the key *must* be present (only for JSON / XML, see below)
`#notpresent` | Expects the key to be **not** present at all (only for JSON / XML, see below)
`#array` | Expects actual value to be a JSON array
`#object` | Expects actual value to be a JSON object
`#boolean` | Expects actual value to be a boolean `true` or `false`
`#number` | Expects actual value to be a number
`#string` | Expects actual value to be a string
`#uuid` | Expects actual (string) value to conform to the UUID format
`#regex STR` | Expects actual (string) value to match the regular-expression 'STR' (see examples above)
`#? EXPR` | Expects the JavaScript expression 'EXPR' to evaluate to true, see [self-validation expressions](#self-validation-expressions) below
`#[NUM] EXPR` | Advanced array validation, see [schema validation](#schema-validation)
`#(EXPR)` | For completeness, [embedded expressions](#embedded-expressions) belong in this list as well

Note that `#present` and `#notpresent` only make sense when you are matching within a JSON or XML context or using a JsonPath or XPath on the left-hand-side.
```cucumber
* def json = { foo: 'bar' }
* match json == { foo: '#present' }
* match json.nope == '#notpresent'
```

The rest can also be used even in 'primitive' data matches like so:
```cucumber
* match foo == '#string'
# convenient (and recommended) way to check for array length
* match bar == '#[2]'
```

### Optional Fields
If two cross-hatch `#` symbols are used as the prefix (for example: `##number`), it means that the key is optional or that the value can be null.
```cucumber
* def foo = { bar: 'baz' }
* match foo == { bar: '#string', ban: '##string' }
```

### Remove If Null
A very useful behavior when you combine the optional marker with an [embedded expression](#embedded-expressions) is as follows: if the embedded expression evaluates to `null` - the JSON key (or XML element or attribute) will be deleted from the payload (the equivalent of [`remove`](#remove)).

```cucumber
* def data = { a: 'hello', b: null, c: null }
* def json = { foo: '#(data.a)', bar: '#(data.b)', baz: '##(data.c)' }
* match json == { foo: 'hello', bar: null }
```

### `#null` and `#notpresent`
Karate's [`match`](#match) is strict, and the case where a JSON key exists but has a `null` value (`#null`) is considered different from the case where the key is not present at all (`#notpresent`) in the payload.

But note that `##null` can be used to represent a convention that many teams adopt, which is that keys with `null` values are stripped from the JSON payload. In other words, `{ a: 1, b: null }` is considered 'equal' to `{ a: 1 }` and `{ a: 1, b: '##null' }` will `match` both cases.

These examples (all exact matches) can make things more clear:

```cucumber
* def foo = { }
* match foo == { a: '##null' }
* match foo == { a: '##notnull' }
* match foo == { a: '#notpresent' }
* match foo == { a: '#ignore' }

* def foo = { a: null }
* match foo == { a: '#null' }    
* match foo == { a: '##null' }
* match foo == { a: '#present' }
* match foo == { a: '#ignore' }

* def foo = { a: 1 }
* match foo == { a: '#notnull' }
* match foo == { a: '##notnull' }
* match foo == { a: '#present' }
* match foo == { a: '#ignore' }
```

Note that you can alternatively use JsonPath on the left-hand-side:

```cucumber
* def foo = { a: 1 }
* match foo.a == '#present'
* match foo.nope == '#notpresent'
```

But of course it is preferable to match whole objects in one step as far as possible.

### 'Self' Validation Expressions
The special 'predicate' marker `#? EXPR` in the table above is an interesting one.  It is best explained via examples. Any valid JavaScript expression that evaluates to a [Truthy](https://developer.mozilla.org/en-US/docs/Glossary/Truthy) or [Falsy](https://developer.mozilla.org/en-US/docs/Glossary/Falsy) value is expected after the `#?`.

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

Especially since strings can be easily coerced to numbers (and vice-versa) in Javascript, you can combine built-in validators with the self-validation 'predicate' form like this: `'#number? _ > 0'`

```cucumber
# given this invalid input (string instead of number)
* def date = { month: '3' }
# this will pass
* match date == { month: '#? _ > 0' }
# but this 'combined form' will fail, which is what we want
# * match date == { month: '#number? _ > 0' }
```

#### Referring to the JSON root
You can actually refer to any JsonPath on the document via `$` and perform cross-field or conditional validations ! This example uses [`contains`](#match-contains) and the [`#?`](#self-validation-expressions) 'predicate' syntax, and situations where this comes in useful will be apparent when we discuss [`match each`](#match-each).

```cucumber
Given def temperature = { celsius: 100, fahrenheit: 212 }
Then match temperature == { celsius: '#number', fahrenheit: '#? _ == $.celsius * 1.8 + 32' }
# when validation logic is an 'equality' check, an embedded expression works better
Then match temperature contains { fahrenheit: '#($.celsius * 1.8 + 32)' }
```

### `match` text or binary
```cucumber
# when the response is plain-text
Then match response == 'Health Check OK'
And match response != 'Error'

# when the response is binary (byte-array)
Then match responseBytes == read('test.pdf')

# incidentally, match and assert behave exactly the same way for strings
* def hello = 'Hello World!'
* match hello == 'Hello World!'
* assert hello == 'Hello World!'
```

Checking if a string is contained within another string is a very common need and [`match` (name) `contains`](#match-contains) works just like you'd expect:
```cucumber
* def hello = 'Hello World!'
* match hello contains 'World'
* match hello !contains 'blah'
```

For case-insensitive string comparisons, see how to create [custom utilities](#commonly-needed-utilities) or [`karate.lowerCase()`](#karate-lowercase). And for dealing with binary content - see [`bytes`](#type-bytes).

### `match header`
Since asserting against header values in the response is a common task - `match header` has a special meaning.  It short-cuts to the pre-defined variable [`responseHeaders`](#responseheaders) and reduces some complexity - because strictly, HTTP headers are a 'multi-valued map' or a 'map of lists' - the Java-speak equivalent being `Map<String, List<String>>`.

> Since [as per the HTTP spec](https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2), headers are *case-insensitive* you may want to switch on this [setting](#configure): `* configure lowerCaseResponseHeaders = true` - if you need to perform a lot of assertions on the [`responseHeaders`](#responseheaders).

```cucumber
# so after a http request
Then match header Content-Type == 'application/json'
# 'contains' works as well
Then match header Content-Type contains 'application'
```  
Note the extra convenience where you don't have to enclose the LHS key in quotes.

You can always directly access the variable called [`responseHeaders`](#responseheaders) if you wanted to do more checks, but you typically won't need to.

### `match` and XML
All the [fuzzy matching](#fuzzy-matching) markers will work in XML as well. Here are some examples:

```cucumber
  * def xml = <root><hello>world</hello><foo>bar</foo></root>
  * match xml == <root><hello>world</hello><foo>#ignore</foo></root>
  * def xml = <root><hello foo="bar">world</hello></root>
  * match xml == <root><hello foo="#ignore">world</hello></root>
```

Refer to this file for a comprehensive set of XML examples: [`xml.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/xml/xml.feature).

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

Note that `match contains` will *not* "recurse" any nested JSON chunks so use [`match contains deep`](#match-contains-deep) instead.

Also note that [`match contains any`](#match-contains-any) is possible for JSON objects as well as [JSON arrays](#json-arrays).

### (not) `!contains`
It is sometimes useful to be able to check if a key-value-pair does **not** exist. This is possible by prefixing `contains` with a `!` (with no space in between).

```cucumber
* def foo = { bar: 1, baz: 'hello', ban: 'world' }
* match foo !contains { bar: 2 }
* match foo !contains { huh: '#notnull' }
```

Here's a reminder that the [`#notpresent`](#null-and-notpresent) marker can be mixed into an equality `match` (`==`) to assert that some keys exist and at the same time ensure that some keys do **not** exist:

```cucumber
* def foo = { a: 1 }
* match foo == { a: '#number', b: '#notpresent' }

# if b can be present (optional) but should always be null
* match foo == { a: '#number', b: '##null' }
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

# the .. operator is great because it matches nodes at any depth in the JSON "tree"
Then match cat..name == ['Billie', 'Bob', 'Wild']

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

#### `match contains any`
To assert that **any** of the given array elements are present.

```cucumber
* def data = { foo: [1, 2, 3] }
* match data.foo contains any [9, 2, 8]
```

And this happens to work as expected for JSON object keys as well:

```cucumber
* def data = { a: 1, b: 'x' }
* match data contains any { b: 'x', c: true }
```

#### `match contains deep`
This modifies the behavior of [`match contains`](#match-contains) so that nested lists or objects are processed for a "deep contains" match instead of a "deep equals" one which is the default. This is convenient for complex nested payloads where you are sure that you only want to check for *some* values in the various "trees" of data.

Here is an example:

```cucumber
Scenario: recurse nested json
  * def original = { a: 1, b: 2, c: 3, d: { a: 1, b: 2 } }
  * def expected = { a: 1, c: 3, d: { b: 2 } }
  * match original contains deep expected

Scenario: recurse nested array
  * def original = { a: 1, arr: [ { b: 2, c: 3 }, { b: 3, c: 4 } ] }
  * def expected = { a: 1, arr: [ { b: 2 }, { c: 4 } ] }
  * match original contains deep expected
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

# this is also possible, see the subtle difference from the above
* def isXabc = function(x) { return x.baz == 'a' || x.baz == 'b' || x.baz == 'c' }
* match each data.foo == '#? isXabc(_)'
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
Then match each json.hotels contains { totalPrice: '#? _ == _$.roomInformation[0].roomPrice' }
# when validation logic is an 'equality' check, an embedded expression works better
Then match each json.hotels contains { totalPrice: '#(_$.roomInformation[0].roomPrice)' }
```

#### Referring to self
While [`$`](#referring-to-the-json-root) always refers to the [JSON 'root'](#referring-to-the-json-root), note the use of `_$` above to represent the 'current' node of a `match each` iteration. Here is a recap of symbols that can be used in JSON [embedded expressions](#embedded-expressions):

Symbol  | Evaluates To
------- | ------                               
| `$`   | The ['root'](#referring-to-the-json-root) of the JSON document in scope          
| `_`   | The value of ['self'](#self-validation-expressions)
| `_$`  | The 'parent' of 'self' or 'current' item in the list, relevant when using [`match each`](#match-each)

There is a shortcut for `match each` explained in the next section that can be quite useful, especially for 'in-line' schema-like validations.

## Schema Validation
Karate provides a far more simpler and more powerful way than [JSON-schema](http://json-schema.org) to validate the structure of a given payload. You can even mix domain and conditional validations and perform all assertions in a single step.

But first, a special short-cut for array validation needs to be introduced:

```cucumber
* def foo = ['bar', 'baz']

# should be an array
* match foo == '#[]'

# should be an array of size 2
* match foo == '#[2]'

# should be an array of strings with size 2
* match foo == '#[2] #string'

# each array element should have a 'length' property with value 3
* match foo == '#[]? _.length == 3'

# should be an array of strings each of length 3
* match foo == '#[] #string? _.length == 3'

# should be null or an array of strings
* match foo == '##[] #string'
```

This 'in-line' short-cut for validating JSON arrays is similar to how [`match each`](#match-each) works. So now, complex payloads (that include arrays) can easily be validated in one step by combining [validation markers](#ignore-or-validate) like so:

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

And you can perform conditional / [cross-field validations](#referring-to-the-json-root) and even business-logic validations at the same time.

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

And there is another example in the [karate-demos](karate-demo): [`schema.feature`](karate-demo/src/test/java/demo/schema/schema.feature) where you can compare Karate's approach with an actual JSON-schema example. You can also find a nice visual comparison and explanation [here](https://twitter.com/KarateDSL/status/878984854012022784).

### `contains` short-cuts
Especially when payloads are complex (or highly dynamic), it may be more practical to use [`contains`](#match-contains) semantics. Karate has the following short-cut symbols designed to be mixed into [`embedded expressions`](#embedded-expressions):

Symbol  | Means
------- | ------                               
| `^`   | [`contains`](#match-contains)           
| `^^`  | [`contains only`](#match-contains-only) 
| `^*`  | [`contains any`](#match-contains-any) 
| `!^`  | [`not contains`](#not-contains)     

Here'a table of the alternative 'in-line' forms compared with the 'standard' form. Note that *all* the short-cut forms on the right-side of the table resolve to 'equality' (`==`) matches, which enables them to be 'in-lined' into a _full_ (single-step) payload `match`, using [embedded expressions](#embedded-expressions).

<a href="https://gist.github.com/ptrthomas/2a1e30bcb4d782279019b3d5c10b3ed1"><img src="karate-demo/src/test/resources/karate-json-assertions.jpg" height="690px"/></a>

A very useful capability is to be able to check that an array `contains` an object that `contains` the provided *sub-set* of keys instead of having to specify the *complete* JSON - which can get really cumbersome for large objects. This turns out to be very useful in practice, and this particular `match` *jsonArray* `contains '#(^`*partialObject*`)'` form has no 'in-line' equivalent (see the third-from-last row above).

> The last row in the table is a little different from the rest, and this short-cut form is the recommended way to validate the length of a JSON array. As a rule of thumb, prefer [`match`](#match) over [`assert`](#assert), because `match` failure messages are more detailed and descriptive.

In real-life tests, these are very useful when the order of items in arrays returned from the server are not guaranteed. You can easily assert that all expected elements are present, _even_ in nested parts of your JSON - while doing a [`match`](#match) on the _full_ payload.

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
* def expected = [{ id: 42, name: 'Wild' }, { id: 23, name: 'Bob' }]
* match cat == { name: 'Billie', kittens: '#(^^expected)' }
```

There's a lot going on in the last line above ! It validates the entire payload in one step and checks if the `kittens` array [_**contains all**_](#contains-short-cuts)  the `expected` items but in _**any order**_.

## `get`
By now, it should be clear that [JsonPath]((https://github.com/jayway/JsonPath#path-examples)) can be very useful for extracting JSON 'trees' out of a given object. The `get` keyword allows you to save the results of a JsonPath expression for later use - which is especially useful for dynamic [data-driven testing](#data-driven-features).

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

### `get` short-cut
The 'short cut' `$variableName` form is also supported. Refer to [JsonPath short-cuts](#jsonpath-short-cuts) for a detailed explanation. So the above could be re-written as follows:

```cucumber
* def kitnums = $cat.kittens[*].id
* match kitnums == [23, 42]
* def kitnames = $cat.kittens[*].name
* match kitnames == ['Bob', 'Wild']
```

It is worth repeating that the above can be condensed into 2 lines. Note that since [only JsonPath is expected](#match-and-variables) on the left-hand-side of the `==` sign of a [`match`](#match) statement, you don't need to prefix the variable reference with `$`:

```cucumber
* match cat.kittens[*].id == [23, 42]
* match cat.kittens[*].name == ['Bob', 'Wild']

# if you prefer using 'pure' JsonPath, you can do this
* match cat $.kittens[*].id == [23, 42]
* match cat $.kittens[*].name == ['Bob', 'Wild']
```

### `get` plus index
A convenience that the `get` syntax supports (but not the `$` short-cut form) is to return a single element if the right-hand-side evaluates to a list-like result (e.g. a JSON array). This is useful because the moment you use a wildcard `[*]` or search filter in JsonPath (see the next section), you get an *array* back - even though typically you would only be interested in the *first* item.

```cucumber
* def actual = 23

# so instead of this
* def kitnums = get cat.kittens[*].id
* match actual == kitnums[0]

# you can do this in one line
* match actual == get[0] cat.kittens[*].id
```

### JsonPath filters
JsonPath [filter expressions](https://github.com/json-path/JsonPath#filter-operators) are very useful for extracting elements that meet some filter criteria out of arrays.

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
# find single kitten where id == 23
* def bob = get[0] cat.kittens[?(@.id==23)]
* match bob.name == 'Bob'

# using the karate object if the expression is dynamic
* def temp = karate.jsonPath(cat, "$.kittens[?(@.name=='" + bob.name + "')]")
* match temp[0] == bob

# or alternatively
* def temp = karate.jsonPath(cat, "$.kittens[?(@.name=='" + bob.name + "')]")[0]
* match temp == bob
```

You usually won't need this, but the second-last line above shows how the `karate` object can be used to [evaluate JsonPath](#karate-jsonpath) if the filter expression depends on a variable. If you find yourself struggling to write [dynamic JsonPath filters](https://stackoverflow.com/a/52741196/143475), look at [`karate.filter()`](#karate-filter) as an alternative, described just below.

## JSON Transforms
Karate supports the following [functional-style](https://en.wikipedia.org/wiki/Functional_programming) operations via the JS API -  [`karate.map()`](#karate-map), [`karate.filter()`](#karate-filter) and [`karate.forEach()`](#karate-foreach). They can be very useful in some situations. A [good example](https://stackoverflow.com/a/53120851/143475) is when you have the *expected* data available as ready-made JSON but it is in a different "shape" from the *actual* data or HTTP `response`. There is also a [`karate.mapWithKey()`](#karate-mapwithkey) for a common need - which is to convert an array of primitives into an array of objects, which is the form that [data driven features](#data-driven-features) expect.

A few more useful "transforms" are to select a sub-set of key-value pairs using [`karate.filterKeys()`](#karate-filterkeys), merging 2 or more JSON-s using [`karate.merge()`](#karate-merge) and combining 2 or more arrays (or objects) into a single array using [`karate.append()`](#karate-append). And [`karate.appendTo()`](#karate-appendto) is for updating an existing variable (the equivalent of `array.push()` in JavaScript), which is especially useful in the body of a `karate.forEach()`.

> Note that a single JS function is sufficient to transform a given JSON object into a completely new one, and you can use complex conditional logic if needed.

```cucumber
Scenario: karate map operation
    * def fun = function(x){ return x * x }
    * def list = [1, 2, 3]
    * def res = karate.map(list, fun)
    * match res == [1, 4, 9]

Scenario: convert an array into a different shape
    * def before = [{ foo: 1 }, { foo: 2 }, { foo: 3 }]
    * def fun = function(x){ return { bar: x.foo } }
    * def after = karate.map(before, fun)
    * match after == [{ bar: 1 }, { bar: 2 }, { bar: 3 }]

Scenario: convert array of primitives into array of objects
    * def list = [ 'Bob', 'Wild', 'Nyan' ]
    * def data = karate.mapWithKey(list, 'name')
    * match data == [{ name: 'Bob' }, { name: 'Wild' }, { name: 'Nyan' }]

Scenario: karate filter operation
    * def fun = function(x){ return x % 2 == 0 }
    * def list = [1, 2, 3, 4]
    * def res = karate.filter(list, fun)
    * match res == [2, 4]

Scenario: forEach works even on object key-values, not just arrays
    * def keys = []
    * def vals = []
    * def idxs = []
    * def fun = 
    """
    function(x, y, i) { 
      karate.appendTo(keys, x); 
      karate.appendTo(vals, y); 
      karate.appendTo(idxs, i); 
    }
    """
    * def map = { a: 2, b: 4, c: 6 }
    * karate.forEach(map, fun)
    * match keys == ['a', 'b', 'c']
    * match vals == [2, 4, 6]
    * match idxs == [0, 1, 2]

Scenario: filterKeys
    * def schema = { a: '#string', b: '#number', c: '#boolean' }
    * def response = { a: 'x', c: true }
    # very useful for validating a response against a schema "super-set"
    * match response == karate.filterKeys(schema, response)
    * match karate.filterKeys(response, 'b', 'c') == { c: true }
    * match karate.filterKeys(response, ['a', 'b']) == { a: 'x' }

Scenario: merge
    * def foo = { a: 1 }
    * def bar = karate.merge(foo, { b: 2 })
    * match bar == { a: 1, b: 2 }

Scenario: append
    * def foo = [{ a: 1 }]
    * def bar = karate.append(foo, { b: 2 })
    * match bar == [{ a: 1 }, { b: 2 }]
```

### Loops
Given the examples above, it has to be said that a best practice with Karate is to avoid JavaScript `for` loops as far as possible. A common requirement is to build an array with `n` elements or do something `n` times where `n` is an integer (that could even be a variable reference). This is easily achieved with the [`karate.repeat()`](#karate-repeat) API:

```cucumber
* def fun = function(i){ return i * 2 }
* def foo = karate.repeat(5, fun)
* match foo == [0, 2, 4, 6, 8]

* def foo = []
* def fun = function(i){ karate.appendTo(foo, i) }
* karate.repeat(5, fun)
* match foo == [0, 1, 2, 3, 4]

# generate test data easily
* def fun = function(i){ return { name: 'User ' + (i + 1) } }
* def foo = karate.repeat(3, fun)
* match foo == [{ name: 'User 1' }, { name: 'User 2' }, { name: 'User 3' }]
```

Don't forget that Karate's [data-driven testing capabilities](#data-driven-tests) can loop over arrays of JSON objects automatically.

## XPath Functions
When handling XML, you sometimes need to call [XPath functions](https://docs.oracle.com/javase/tutorial/jaxp/xslt/xpath.html), for example to get the count of a node-set. Any valid XPath expression is allowed on the left-hand-side of a [`match`](#match) statement.

```cucumber
* def myXml =
  """
  <records>
    <record index="1">a</record>
    <record index="2">b</record>
    <record index="3" foo="bar">c</record>
  </records>
  """

* match foo count(/records//record) == 3
* match foo //record[@index=2] == 'b'
* match foo //record[@foo='bar'] == 'c'
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

If your XPath is dynamic and has to be formed 'on the fly' perhaps by using some variable derived from previous steps, you can use the [`karate.xmlPath()`](#karate-xmlpath) helper:

```cucumber
* def xml = <query><name><foo>bar</foo></name></query>
* def elementName = 'name'
* def name = karate.xmlPath(xml, '/query/' + elementName + '/foo')
* match name == 'bar'
* def queryName = karate.xmlPath(xml, '/query/' + elementName)
* match queryName == <name><foo>bar</foo></name>
```

You can refer to this file (which is part of the Karate test-suite) for more XML examples: [`xml-and-xpath.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/xml-and-xpath.feature)


# Special Variables
These are 'built-in' variables, there are only a few and all of them give you access to the HTTP response.

## `response`
After every HTTP call this variable is set with the response body, and is available until the next HTTP request over-writes it. You can easily assign the whole `response` (or just parts of it using Json-Path or XPath) to a variable, and use it in later steps.

The response is automatically available as a JSON, XML or String object depending on what the response contents are.

As a short-cut, when running JsonPath expressions - `$` represents the `response`.  This has the advantage that you can use pure [JsonPath](https://github.com/jayway/JsonPath#path-examples) and be more concise.  For example:

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

#### JsonPath short-cuts
The [`$varName` form](#get-short-cut) is used on the right-hand-side of [Karate expressions](#karate-expressions) and is *slightly* different from pure [JsonPath expressions](https://github.com/json-path/JsonPath#path-examples) which always begin with `$.` or `$[`. Here is a summary of what the different 'shapes' mean in Karate:

| Shape | Description |
| ----- | ----------- |
`$.bar` | Pure JsonPath equivalent of `$response.bar` where `response` is a JSON object
`$[0]`  | Pure JsonPath equivalent of `$response[0]` where `response` is a JSON array
`$foo.bar` | Evaluates the JsonPath `$.bar` on the variable `foo` which is a JSON object or map-like
`$foo[0]` | Evaluates the JsonPath `$[0]` on the variable `foo` which is a JSON array or list-like

> There is no need to prefix variable names with `$` on the left-hand-side of [`match`](#match) statements because it is implied. You *can* if you want to, but since [*only* JsonPath (on variables)](#match-and-variables) is allowed here, Karate ignores the `$` and looks only at the variable name. None of the examples in the documentation use the `$varName` form on the LHS, and this is the recommended best-practice.

## `responseBytes`
This will always hold the contents of the response as a byte-array. This is rarely used, unless you are expecting binary content returned by the server. The `match` keyword will [work as you expect](#match-text-or-binary). Here is an example: [`binary.feature`](karate-junit4/src/test/java/com/intuit/karate/mock/binary.feature).

## `responseCookies`
The `responseCookies` variable is set upon any HTTP response and is a map-like (or JSON-like) object. It can be easily inspected or used in expressions.

```cucumber
* assert responseCookies['my.key'].value == 'someValue'

# karate's unified data handling means that even 'match' works
* match responseCookies contains { time: '#notnull' }

# ... which means that checking if a cookie does NOT exist is a piece of cake
* match responseCookies !contains { blah: '#notnull' }

# save a response cookie for later use
* def time = responseCookies.time.value
```

As a convenience, cookies from the previous response are collected and passed as-is as part of the next HTTP request. This is what is normally expected and simulates a web-browser - which makes it easy to script things like HTML-form based authentication into test-flows. Refer to the documentation for [`cookie`](#cookie) for details and how you can disable this if need be.

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

# check if the response status is either of two values
Then assert responseStatus == 200 || responseStatus == 204
```
## `responseTime`
The response time (in milliseconds) for the current [`response`](#response) would be available in a variable called `responseTime`. You can use this to assert that it was returned within the expected time like so:
```cucumber
When method post
Then status 201
And assert responseTime < 1000
```

## `responseType`
Karate will attempt to parse the raw HTTP response body as JSON or XML and make it available as the [`response`](#response) value. If parsing fails, Karate will log a warning and the value of `response` will then be a plain string. You can still perform string comparisons such as a [`match contains`](#match-text-or-binary) and look for error messages etc. In rare cases, you may want to check what the "type" of the `response` is and it can be one of 3 different values: `json`, `xml` and `string`.

So if you really wanted to assert that the HTTP response body is well-formed JSON or XML you can do this:

```cucumber
When method post
Then status 201
And match responseType == 'json'
```

## `requestTimeStamp`
Very rarely used - but you can get the Java system-time (for the current [`response`](#response)) at the point when the HTTP request was initiated (the value of `System.currentTimeMillis()`) which can be used for detailed logging or custom framework / stats calculations.

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
 
Here is an example JavaScript function that uses some variables in the context (which have been possibly set as the result of a sign-in) to build the `Authorization` header. Note how even [calls to Java code](#calling-java) can be made if needed.

> In the example below, note the use of the [`karate.get()`](#karate-get) helper for getting the value of a dynamic variable (which was *not set* at the time this JS `function` was *declared*). This is preferred because it takes care of situations such as if the value is `undefined` in JavaScript. In rare cases you may need to *set* a variable from this routine, and a good example is to make the generated UUID "visible" to the currently executing script or feature. You can easily do this via [`karate.set('someVarName', value)`](#karate-set).

```javascript
function fn() {
  var uuid = '' + java.util.UUID.randomUUID(); // convert to string
  var out = { // so now the txid_header would be a unique uuid for each request
    txid_header: uuid,
    ip_header: '123.45.67.89', // hard coded here, but also can be as dynamic as you want   
  };
  var authString = '';
  var authToken = karate.get('authToken'); // use the 'karate' helper to do a 'safe' get of a 'dynamic' variable
  if (authToken) { // and if 'authToken' is not null ... 
    authString = ',auth_type=MyAuthScheme'
        + ',auth_key=' + authToken.key
        + ',auth_user=' + authToken.userId
        + ',auth_project=' + authToken.projectId;
  }
  // the 'appId' variable here is expected to have been set via karate-config.js (bootstrap init) and will never change
  out['Authorization'] = 'My_Auth app_id=' + appId + authString;
  return out;
}
```
Assuming the above code is in a file called `my-headers.js`, the next section on [calling other feature files](#calling-other-feature-files) shows how it looks like in action at the beginning of a test script.

Notice how once the `authToken` variable is initialized, it is used by the above function to generate headers for every HTTP call made as part of the test flow.

If a few steps in your flow need to temporarily change (or completely bypass) the currently-set header-manipulation scheme, just update `configure headers` to a new value (or set it to `null`) in the middle of a script. Then use the [`header`](#header) keyword to do a custom 'over-ride' if needed.

The [karate-demo](karate-demo) has an example showing various ways to `configure` or set headers: [`headers.feature`](karate-demo/src/test/java/demo/headers/headers.feature)

# The `karate` object
A JavaScript function or [Karate expression](#karate-expressions) at runtime has access to a utility object in a variable named: `karate`.  This provides the following methods:

Operation | Description
--------- | -----------
<a name="karate-abort"><code>karate.abort()</code></a> | you can prematurely exit a `Scenario` by combining this with [conditional logic](#conditional-logic) like so: `* if (condition) karate.abort()` - please use [sparingly](https://martinfowler.com/articles/nonDeterminism.html) ! and also see [`configure abortedStepsShouldPass`](#configure)
<a name="karate-append"><code>karate.append(... items)</code></a> | useful to create lists out of items (which can be lists as well), see [JSON transforms](#json-transforms)
<a name="karate-appendto"><code>karate.appendTo(name, ... items)</code></a> | useful to append to a list-like variable (that has to exist) in scope, see [JSON transforms](#json-transforms) - the first argument can be a reference to an array-like variable or even the name (string) of an existing variable which is list-like
<a name="karate-call"><code>karate.call(fileName, [arg])</code></a> | invoke a [`*.feature` file](#calling-other-feature-files) or a [JavaScript function](#calling-javascript-functions) the same way that [`call`](#call) works (with an optional solitary argument), see [`call()` vs `read()`](#call-vs-read) for details
<a name="karate-callsingle"><code>karate.callSingle(fileName, [arg])</code></a> | like the above, but guaranteed to run **only once** even across multiple features - see [`karate.callSingle()`](#karatecallsingle)
<a name="karate-configure"><code>karate.configure(key, value)</code></a> | does the same thing as the [`configure`](#configure) keyword, and a very useful example is to do `karate.configure('connectTimeout', 5000);` in [`karate-config.js`](#configuration) - which has the 'global' effect of not wasting time if a connection cannot be established within 5 seconds
<a name="karate-embed"><code>karate.embed(object, mimeType)</code></a> | embeds the object (can be raw bytes or an image) into the JSON report output, see this [example](karate-demo/src/test/java/demo/embed/embed.feature)
<a name="karate-env"><code>karate.env</code></a> | gets the value (read-only) of the environment property 'karate.env', and this is typically used for bootstrapping [configuration](#configuration)
<a name="karate-eval"><code>karate.eval(expression)</code></a> | for really advanced needs, you can programmatically generate a snippet of JavaScript which can be evaluated at run-time, you can find an example [here](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/js-arrays.feature)
<a name="karate-exec"><code>karate.exec(command)</code></a> | convenient way to execute an OS specific command and return the console output e.g. `karate.exec('some.exe -h')` (or `karate.exec(['some.exe', '-h'])`) useful for calling non-Java code (that can even return data) or for starting user-interfaces to be automated, this command will block until the process terminates, also see [`karate.fork()`](#karate-fork)
<a name="karate-extract"><code>karate.extract(text, regex, group)</code></a> | useful to "scrape" text out of non-JSON or non-XML text sources such as HTML, `group` follows the Java [regex rules](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html), see [this example](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/extract.feature)
<a name="karate-extractall"><code>karate.extractAll(text, regex, group)</code></a> | like the above, but returns a list of text-matches
<a name="karate-fail"><code>karate.fail(message)</code></a> | if you want to conditionally stop a test with a descriptive error message, e.g. `* if (condition) karate.fail('we expected something else')`
<a name="karate-filter"><code>karate.filter(list, predicate)</code></a> | functional-style 'filter' operation useful to filter list-like objects (e.g. JSON arrays), see [example](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/js-arrays.feature), the second argument has to be a JS function (item, [index]) that returns a `boolean`
<a name="karate-filterkeys"><code>karate.filterKeys(map, keys)</code></a> | extracts a sub-set of key-value pairs from the first argument, the second argument can be a list (or varargs) of keys - or even another JSON where only the keys would be used for extraction, [example](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/js-arrays.feature)
`karate.forEach(list, function)` | functional-style 'loop' operation useful to traverse list-like (or even map-like) objects (e.g. JSON / arrays), see [example](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/js-arrays.feature), the second argument has to be a JS function (item, [index]) for lists and (key, [value], [index]) for JSON / maps
<a name="karate-fork"><code>karate.fork(map)</code></a> | executes an OS command, but forks a process in parallel and will not block the test like [`karate.exec()`](#karate-exec) e.g. `karate.fork({ args: ['some.exe', '-h'] })` or `karate.fork(['some.exe', '-h'])` - you can use a composite string as `line` (or the solitary argument e.g. `karate.fork('some.exe -h')`) instead of `args`, and an optional `workingDir` string property and `env` JSON / map is also supported - this returns a [`Command`](karate-core/src/main/java/com/intuit/karate/shell/Command.java) object which has operations such as `waitSync()` and `close()` if you need more control
<a name="karate-fromobject"><code>karate.fromObject(any)</code></a> | for advanced conditional logic when object types are dynamic and not known in advance, see [example](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/type-conv.feature)
<a name="karate-fromostring"><code>karate.fromString(string)</code></a> | for advanced conditional logic for e.g. when a string coming from an external process is dynamic - and whether it is JSON or XML is not known in advance, see [example](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/type-conv.feature)
<a name="karate-get"><code>karate.get(name, [default])</code></a> | get the value of a variable by name (or JsonPath expression), if not found - this returns `null` which is easier to handle in JavaScript (than `undefined`), and an optional (literal / constant) second argument can be used to return a "default" value, very useful to set variables in called features that have not been pre-defined
<a name="karate-http"><code>karate.http(url)</code></a> | returns a convenience [`Http`](karate-core/src/main/java/com/intuit/karate/Http.java) request builder class, only recommended for [advanced use](https://github.com/intuit/karate/tree/develop/examples/ui-test#webdriver-tips)
<a name="karate-info"><code>karate.info</code></a> | within a test (or within the [`afterScenario`](#configure) function if configured) you can access metadata such as the `Scenario` name, refer to this example: [`hooks.feature`](karate-demo/src/test/java/demo/hooks/hooks.feature)
<a name="karate-jsonpath"><code>karate.jsonPath(json, expression)</code></a> | brings the power of [JsonPath](https://github.com/json-path/JsonPath) into JavaScript, and you can find an example [here](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/js-arrays.feature).
<a name="karate-keysof"><code>karate.keysOf(object)</code></a> | returns only the keys of a map-like object
<a name="karate-listen"><code>karate.listen(timeout)</code></a> | wait until [`karate.signal(result)`](#karate-signal) has been called or time-out after `timeout` milliseconds, see [async](#async)
<a name="karate-log"><code>karate.log(... args)</code></a> | log to the same logger (and log file) being used by the parent process, logging can be suppressed with [`configure printEnabled`](#configure) set to `false`, and just like [`print`](#print) - use comma-separated values to "pretty print" JSON or XML
<a name="karate-lowercase"><code>karate.lowerCase(object)</code></a> | useful to brute-force all keys and values in a JSON or XML payload to lower-case, useful in some cases, see [example](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/lower-case.feature)
<a name="karate-map"><code>karate.map(list, function)</code></a> | functional-style 'map' operation useful to transform list-like objects (e.g. JSON arrays), see [example](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/js-arrays.feature), the second argument has to be a JS function (item, [index])
<a name="karate-mapwithkey"><code>karate.mapWithKey(list, string)</code></a> | convenient for the common case of transforming an array of primitives into an array of objects, see [JSON transforms](#json-transforms)
<a name="karate-match"><code>karate.match(actual, expected)</code></a> | brings the power of the *fuzzy* [`match`](#match) syntax into Karate-JS, returns a JSON in the form `{ pass: '#boolean', message: '#string' }` and you can find an example [here](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/js-arrays.feature) - you can even place a *full* match expression like this: `karate.match("each foo contains { a: '#number' }")`
<a name="karate-merge"><code>karate.merge(... maps)</code></a> | useful to merge the key-values of two (or more) JSON (or map-like) objects, see [JSON transforms](#json-transforms)
<a name="karate-os"><code>karate.os</code></a> | returns the operating system details as JSON, for e.g. `{ type: 'macosx', name: 'Mac OS X' }` - useful for writing conditional logic, the possible `type`-s being: `macosx`, `windows`, `linux` and `unknown`
<a name="karate-pretty"><code>karate.pretty(value)</code></a> | return a 'pretty-printed', nicely indented string representation of the JSON value, also see: [`print`](#print)
<a name="karate-prettyxml"><code>karate.prettyXml(value)</code></a> | return a 'pretty-printed', nicely indented string representation of the XML value, also see: [`print`](#print)
<a name="karate-prevrequest"><code>karate.prevRequest</code></a> | for advanced users, you can inspect the *actual* HTTP request after it happens, useful if you are writing a framework over Karate, refer to this example: [`request.feature`](karate-demo/src/test/java/demo/request/request.feature)
<a name="karate-properties"><code>karate.properties[key]</code></a> | get the value of any Java system-property by name, useful for [advanced custom configuration](#dynamic-port-numbers)
<a name="karate-read"><code>karate.read(filename)</code></a> | the same [`read()`](#reading-files) function - which is pre-defined even within JS blocks, so there is no need to ever do `karate.read()`, and just `read()` is sufficient
<a name="karate-readasstring"><code>karate.readAsString(filename)</code></a> | [rarely used](#read-file-as-string), behaves exactly like [`read`](#reading-files) - but does *not* auto convert to JSON or XML
<a name="karate-remove"><code>karate.remove(name, path)</code></a> | very rarely used - when needing to perform conditional removal of JSON keys or XML nodes. Behaves the same way as the [`remove`](#remove) keyword.
<a name="karate-repeat"><code>karate.repeat(count, function)</code></a> | useful for building an array with `count` items or doing something `count` times, refer this [example](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/repeat.feature). Also see [loops](#loops).
<a name="karate-set"><code>karate.set(name, value)</code></a> | sets the value of a variable (immediately), which may be needed in case any other routines (such as the [configured headers](#configure-headers)) depend on that variable
<a name="karate-setall"><code>karate.set(object)</code></a> | where the single argument is expected to be a `Map` or JSON-like, and will perform the above `karate.set()` operation for all key-value pairs in one-shot, see [example](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/set.feature)
<a name="karate-setpath"><code>karate.set(name, path, value)</code></a> | only needed when you need to conditionally build payload elements, especially XML. This is best explained via [an example](karate-junit4/src/test/java/com/intuit/karate/junit4/xml/xml.feature#L211), and it behaves the same way as the [`set`](#set) keyword. Also see [`eval`](#eval).
<a name="karate-setxml"><code>karate.setXml(name, xmlString)</code></a> | rarely used, refer to the example above
<a name="karate-signal"><code>karate.signal(result)</code></a> | trigger an event that [`karate.listen(timeout)`](#karate-listen) is waiting for, and pass the data, see [async](#async)
<a name="karate-sizeof"><code>karate.sizeOf(object)</code></a> | returns the size of the map-like or list-like object
<a name="karate-stop"><code>karate.stop(port)</code></a> | will pause the test execution until a socket connection (even HTTP `GET`) is made to the port logged to the console, useful for troubleshooting UI tests without using a [de-bugger](https://twitter.com/KarateDSL/status/1167533484560142336), of course - *NEVER* forget to remove this after use ! 
<a name="karate-target"><code>karate.target(object)</code></a> | currently for web-ui automation only, see [target lifecycle](karate-core#target-lifecycle)
<a name="karate-tags"><code>karate.tags</code></a> | for advanced users - scripts can introspect the tags that apply to the current scope, refer to this example: [`tags.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/tags.feature)
<a name="karate-tagvalues"><code>karate.tagValues</code></a> | for even more advanced users - Karate natively supports tags in a `@name=val1,val2` format, and there is an inheritance mechanism where `Scenario` level tags can over-ride `Feature` level tags, refer to this example: [`tags.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/tags.feature)
<a name="karate-toabsolutepath"><code>karate.toAbsolutePath(relativePath)</code></a> | when you want to get the absolute OS path to the argument which could even have a prefix such as `classpath:`, e.g. `karate.toAbsolutePath('some.json')`
<a name="karate-tobean"><code>karate.toBean(json, className)</code></a> | converts a JSON string or map-like object into a Java object, given the Java class name as the second argument, refer to this [file](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/type-conv.feature) for an example
<a name="karate-tocsv"><code>karate.toCsv(list)</code></a> | converts a JSON array (of objects) or a list-like object into a CSV string, writing this to a file is your responsibility or you could use [`karate.write()`](#karate-write)
<a name="karate-tojson"><code>karate.toJson(object)</code></a> | converts a Java object into JSON, and `karate.toJson(object, true)` will strip all keys that have `null` values from the resulting JSON, convenient for unit-testing Java code, see [example](karate-demo/src/test/java/demo/unit/cat.feature)
<a name="karate-tolist"><code>karate.toList(object)</code></a> | rarely used - when still within a JS block, you need to convert a JSON array into a Java `List`
<a name="karate-tomap"><code>karate.toMap(object)</code></a> | rarely used - when still within a JS block, you need to convert a JSON object into a Java `Map`, or you are setting up [complex global variables](#restrictions-on-global-variables)
<a name="karate-valuesof"><code>karate.valuesOf(object)</code></a> | returns only the values of a map-like object (or itself if a list-like object)
<a name="karate-waitforhttp"><code>karate.waitForHttp(url)</code></a> | will wait until the URL is ready to accept HTTP connections
<a name="karate-waitforport"><code>karate.waitForPort(host, port)</code></a> | will wait until the host:port is ready to accept socket connections
<a name="karate-websocket"><code>karate.webSocket(url, handler)</code></a> | see [websocket](#websocket)
<a name="karate-write"><code>karate.write(object, path)</code></a> | *normally not recommended, please [read this first](https://stackoverflow.com/a/54593057/143475)* - writes the bytes of `object` to a path which will *always* be relative to the "build" directory (typically `target`), see this example: [`embed-pdf.js`](karate-demo/src/test/java/demo/embed/embed-pdf.js) - and this method returns a `java.io.File` reference to the file created / written to
<a name="karate-xmlpath"><code>karate.xmlPath(xml, expression)</code></a> | Just like [`karate.jsonPath()`](#karate-jsonpath) - but for XML, and allows you to use dynamic XPath if needed, see [example](karate-junit4/src/test/java/com/intuit/karate/junit4/xml/xml.feature).

# Code Reuse / Common Routines
## `call`
In any complex testing endeavor, you would find yourself needing 'common' code that needs to be re-used across multiple test scripts. A typical need would be to perform a 'sign in', or create a fresh user as a pre-requisite for the scenarios being tested.

There are two types of code that can be `call`-ed. `*.feature` files and [JavaScript functions](#calling-javascript-functions).

## Calling other `*.feature` files
When you have a sequence of HTTP calls that need to be repeated for multiple test scripts, Karate allows you to treat a `*.feature` file as a re-usable unit. You can also pass parameters into the `*.feature` file being called, and extract variables out of the invocation result.

Here is an example of using the `call` keyword to invoke another feature file, loaded using the [`read`](#reading-files) function:

> If you find this hard to understand at first, try looking at this [set of examples](karate-demo/src/test/java/demo/callfeature/call-feature.feature).

```cucumber
Feature: which makes a 'call' to another re-usable feature

Background:
  * configure headers = read('classpath:my-headers.js')
  * def signIn = call read('classpath:my-signin.feature') { username: 'john', password: 'secret' }
  * def authToken = signIn.authToken

Scenario: some scenario
  # main test steps
```

> Note that [`def`](#def) can be used to *assign* a __feature__ to a variable. For example look at how "`creator`" has been defined in the `Background` in [this example](karate-demo/src/test/java/demo/calldynamic/call-dynamic-json.feature), and used later in a `call` statement. This is very close to how "custom keywords" work in other frameworks. See this other example for more ideas: [`dsl.feature`](karate-demo/src/test/java/demo/dsl/dsl.feature).

The contents of `my-signin.feature` are shown below. A few points to note:
* Karate creates a new 'context' for the feature file being invoked but passes along all variables and configuration. This means that all your [config variables](#configuration) and [`configure` settings](#configure) would be available to use, for example `loginUrlBase` in the example below. 
* When you use [`def`](#def) in the 'called' feature, it will **not** over-write variables in the 'calling' feature (unless you explicitly choose to use [shared scope](#shared-scope)). But note that JSON, XML, Map-like or List-like variables are 'passed by reference' which means that 'called' feature steps can *update* or 'mutate' them using the [`set`](#set) keyword. Use the [`copy`](#copy) keyword to 'clone' a JSON or XML payload if needed, and refer to this example for more details: [`copy-caller.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/copy-caller.feature).
* You can add (or over-ride) variables by passing a call 'argument' as shown above. Only one JSON argument is allowed, but this does not limit you in any way as you can use any complex JSON structure. You can even initialize the JSON in a separate step and pass it by name, especially if it is complex. Observe how using JSON for parameter-passing makes things super-readable. In the 'called' feature, the argument can also be accessed using the built-in variable: [`__arg`](#built-in-variables-for-call).
* Note that any `call` argument will be shown in the HTML reports by default, make sure you are aware of the [Log Masking Caveats](#log-masking-caveats)
* **All** variables that were defined (using [`def`](#def)) in the 'called' script would be returned as 'keys' within a JSON-like object. Note that this includes ['built-in' variables](#special-variables), which means that things like the last value of [`response`](#response) would also be present. In the example above you can see that the JSON 'envelope' returned - is assigned to the variable named `signIn`. And then getting hold of any data that was generated by the 'called' script is as simple as accessing it by name, for example `signIn.authToken` as shown above. This design has the following advantages:
  * 'called' Karate scripts don't need to use any special keywords to 'return' data and can behave like 'normal' Karate tests in 'stand-alone' mode if needed
  * the data 'return' mechanism is 'safe', there is no danger of the 'called' script over-writing any variables in the 'calling' (or parent) script (unless you use [shared scope](#shared-scope))
  * the need to explicitly 'unpack' variables by name from the returned 'envelope' keeps things readable and maintainable in the 'caller' script

> Note that only [variables](#def) and [configuration settings](#configure) will be passed. You can't do things such as `* url 'http://foo.bar'` and expect the URL to be set in the "called" feature. Use a variable in the "called" feature instead, for e.g. `* url myUrl`.

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

The above example actually makes two HTTP requests - the first is a standard 'sign-in' POST and then (for illustrative purposes) another HTTP call (a GET) is made for retrieving a list of projects for the signed-in user, and the first one is 'selected' and added to the returned 'auth token' JSON object.

So you get the picture, any kind of complicated 'sign-in' flow can be scripted and re-used.

> If the second HTTP call above expects headers to be set by `my-headers.js` - which in turn depends on the `authToken` variable being updated, you will need to duplicate the line `* configure headers = read('classpath:my-headers.js')` from the 'caller' feature here as well. The above example does **not** use [shared scope](#shared-scope), which means that the variables in the 'calling' (parent) feature are *not* shared by the 'called' `my-signin.feature`. The above example can be made more simpler with the use of `call` (or [`callonce`](#callonce)) *without* a [`def`](#def)-assignment to a variable, and is the [recommended pattern](#shared-scope) for implementing re-usable authentication setup flows.

Do look at the documentation and example for [`configure headers`](#configure-headers) also as it goes hand-in-hand with `call`. In the above example, the end-result of the `call` to `my-signin.feature` resulted in the `authToken` variable being initialized. Take a look at how the [`configure headers`](#configure-headers) example uses the `authToken` variable.

### Call Tag Selector
You can "select" a single `Scenario` (or `Scenario`-s or `Scenario Outline`-s or even specific `Examples` rows) by appending a "tag selector" at the end of the feature-file you are calling. For example:

```cucumber
call read('classpath:my-signin.feature@name=someScenarioName')
```

While the tag does not need to be in the `@key=value` form, it is recommended for readability when you start getting into the business of giving meaningful names to your `Scenario`-s.

This "tag selection" capability is designed for you to be able to "compose" flows out of existing test-suites when using the [Karate Gatling integration](karate-gatling). Normally we recommend that you keep your "re-usable" features lightweight - by limiting them to just one `Scenario`.

### Data-Driven Features
If the argument passed to the [call of a `*.feature` file](#calling-other-feature-files) is a JSON array, something interesting happens. The feature is invoked for each item in the array. Each array element is expected to be a JSON object, and for each object - the behavior will be as described above.

But this time, the return value from the `call` step will be a JSON array of the same size as the input array. And each element of the returned array will be the 'envelope' of variables that resulted from each iteration where the `*.feature` got invoked.

Here is an example that combines the [`table`](#table) keyword with calling a `*.feature`. Observe how the [`get`](#get) [shortcut](#get-short-cut) is used to 'distill' the result array of variable 'envelopes' into an array consisting only of [`response`](#response) payloads.

```cucumber
* table kittens 
  | name   | age |
  | 'Bob'  |   2 |
  | 'Wild' |   1 |
  | 'Nyan' |   3 |

* def result = call read('cat-create.feature') kittens
* def created = $result[*].response
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

Although it is just a few lines of code, take time to study the above example carefully. It is a great example of how to effectively use the unique combination of Cucumber and JsonPath that Karate provides.

Also look at the [demo examples](karate-demo), especially [`dynamic-params.feature`](karate-demo/src/test/java/demo/search/dynamic-params.feature) - to compare the above approach with how the Cucumber [`Scenario Outline:`](#the-cucumber-way) can be alternatively used for data-driven tests.

### Built-in variables for `call`
Although all properties in the passed JSON-like argument are 'unpacked' into the current scope as separate 'named' variables, it sometimes makes sense to access the whole argument and this can be done via `__arg`. And if being called in a loop, a built-in variable called `__loop` will also be available that will hold the value of the current loop index. So you can do things like this: `* def name = name + __loop` - or you can use the loop index value for looking up other values that may be in scope - in a data-driven style.

Variable  | Refers To
--------- | ------                               
| `__arg`   | the single `call` (or [`callonce`](#callonce)) argument, will be `null` if there was none         
| `__loop`  | the current iteration index (starts from 0) if being called in a loop, will be `-1` if not

Refer to this [demo feature](karate-demo) for an example: [`kitten-create.feature`](karate-demo/src/test/java/demo/calltable/kitten-create.feature)

### Default Values
Some users need "callable" features that are re-usable even when variables have not been defined by the calling feature. Normally an undefined variable results in nasty JavaScript errors. But there is an elegant way you can specify a default value using the [`karate.get()`](#karate-get) API:

```cucumber
# if foo is not defined, it will default to 42
* def foo = karate.get('foo', 42)
```

> A word of caution: we recommend that you should not over-use Karate's capability of being able to re-use features. Re-use can sometimes result in negative benefits - especially when applied to test-automation. Prefer readability over re-use. See this for an [example](https://stackoverflow.com/a/54126724/143475).

### `copy`
For a [`call`](#call) (or [`callonce`](#callonce)) - payload / data structures (JSON, XML, Map-like or List-like) variables are 'passed by reference' which means that steps within the 'called' feature can update or 'mutate' them, for e.g. using the [`set`](#set) keyword. This is actually the intent most of the time and is convenient. If you want to pass a 'clone' to a 'called' feature, you can do so using the rarely used `copy` keyword that works very similar to [type conversion](#type-conversion). This is best explained in the last scenario of this example: [`copy-caller.feature`](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/copy-caller.feature)

## Calling JavaScript Functions
Examples of [defining and using JavaScript functions](#javascript-functions) appear in earlier sections of this document. Being able to define and re-use JavaScript functions is a powerful capability of Karate. For example, you can:
* call re-usable functions that take complex data as an argument and return complex data that can be stored in a variable
* [call and interoperate with Java code](#calling-java) if needed
* share and re-use test [utilities](#commonly-needed-utilities) or 'helper' functionality across your organization

> For an advanced example of how you can build and re-use a common set of JS functions, refer to [this answer on Stack Overflow](https://stackoverflow.com/a/49384760/143475).

In real-life scripts, you would typically also use this capability of Karate to [`configure headers`](#configure-headers) where the specified JavaScript function uses the variables that result from a [sign in](#calling-other-feature-files) to manipulate headers for all subsequent HTTP requests. And it is worth mentioning that the Karate [configuration 'bootstrap'](#configuration) routine is itself a JavaScript function.

> Also refer to the [`eval`](#eval) keyword for a simpler way to execute arbitrary JavaScript that can be useful in some situations.

### JS function argument rules for `call`
When using `call` (or [`callonce`](#callonce)), only one argument is allowed. But this does not limit you in any way, because similar to how you can [call `*.feature files`](#calling-other-feature-files), you can pass a whole JSON object as the argument. In the case of the `call` of a JavaScript function, you can also pass a JSON array or a primitive (string, number, boolean) as the solitary argument, and the function implementation is expected to handle whatever is passed.

Instead of using `call` (or `callonce`) you are always free to call JavaScript functions 'normally' and then you can use more than one argument.

```cucumber
* def adder = function(a, b){ return a + b }
* assert adder(1, 2) == 3
```

### Return types
Naturally, only one value can be returned.  But again, you can return a JSON object. There are two things that can happen to the returned value.

Either - it can be assigned to a variable like so.
```cucumber
* def returnValue = call myFunction
```

Or - if a `call` is made without an assignment, and if the function returns a map-like object, it will add each key-value pair returned as a new variable into the execution context.
```cucumber
# while this looks innocent ...
# ... behind the scenes, it could be creating (or over-writing) a bunch of variables !
* call someFunction
```

While this sounds dangerous and should be used with care (and limits readability), the reason this feature exists is to quickly set (or over-write) a bunch of config variables when needed. In fact, this is the mechanism used when [`karate-config.js`](#configuration) is processed on start-up.

#### Shared Scope
This behavior where all key-value pairs in the returned map-like object get automatically added as variables - applies to the [calling of `*.feature` files](#calling-other-feature-files) as well. In other words, when [`call`](#call) or [`callonce`](#callonce) is used without a [`def`](#def), the 'called' script not only shares all variables (and [`configure`](#configure) settings) but can update the shared execution context. This is very useful to boil-down those 'common' steps that you may have to perform at the start of multiple test-scripts - into one-liners. But use wisely, because called scripts will now over-write variables that may have been already defined.

```cucumber
* def config = { user: 'john', password: 'secret' }
# this next line may perform many steps and result in multiple variables set for the rest of the script
* call read('classpath:common-setup.feature') config
```

You can use [`callonce`](#callonce) instead of `call` within the [`Background`](#script-structure) in case you have multiple `Scenario` sections or [`Examples`](#data-driven-tests). Note the 'inline' use of the [read](#reading-files) function as a short-cut above. This applies to JS functions as well:

```cucumber
* call read('my-function.js')
```

These heavily commented [demo examples](karate-demo) can help you understand 'shared scope' better, and are designed to get you started with creating re-usable 'sign-in' or authentication flows:

| Scope | Caller Feature | Called Feature |
| ----- | -------------- | -------------- |
Isolated | [`call-isolated-headers.feature`](karate-demo/src/test/java/demo/headers/call-isolated-headers.feature) | [`common-multiple.feature`](karate-demo/src/test/java/demo/headers/common-multiple.feature)
Shared | [`call-updates-config.feature`](karate-demo/src/test/java/demo/headers/call-updates-config.feature) | [`common.feature`](karate-demo/src/test/java/demo/headers/common.feature)

> Once you get comfortable with Karate, you can consider moving your authentication flow into a 'global' one-time flow using [`karate.callSingle()`](#karate-callsingle), think of it as '[`callonce`](#callonce) on steroids'.

#### `call` vs `read()`
Since this is a frequently asked question, the different ways of being able to re-use code (or data) are summarized below.

Code | Description
---- | -----------
`* def login = read('login.feature')`<br/>`* call login` | [Shared Scope](#shared-scope), and the <br/>`login` variable can be re-used
`* call read('login.feature')` | short-cut for the above <br/>without needing a variable
`* def credentials = read('credentials.json')`<br/>`* def login = read('login.feature')`<br/>`* call login credentials` | Note how using [`read()`](#reading-files) <br/>for a JSON file returns *data* - <br/>not "callable" code, and here it is <br/>used as the [`call`](#call) argument
`* call read('login.feature') read('credentials.json')` | You *can* do this in theory, <br/>but it is not as readable as the above
`* karate.call('login.feature')` | The [JS API](#karate-call) allows you to do this, <br/>but this will *not* be [Shared Scope](#shared-scope)
`* def result = call read('login.feature')` | [`call`](#call) result assigned to a variable <br/>and *not* [Shared Scope](#shared-scope)
`* def result = karate.call('login.feature')` | exactly equivalent to the above !
`* if (cond) karate.call(true, 'login.feature')` | if you need [conditional logic](#conditional-logic) <br/>*and* [Shared Scope](#shared-scope), add a <br/>boolean `true` first argument
`* def credentials = read('credentials.json')`<br/>`* def result = call read('login.feature') credentials` | like the above, <br/>but with a [`call`](#call) argument
`* def credentials = read('credentials.json')`<br/>`* def result = karate.call('login.feature', credentials)` | like the above, but in [JS API](#karate-call) form, <br/>the advantage of the above form is <br/>that using an in-line argument is less <br/>"cluttered" (see next row)
`* def login = read('login.feature')`<br/>`* def result = call login { user: 'john', password: 'secret' }` | using the `call` keyword makes <br/>passing an in-line JSON argument <br/>more "readable"
`* call read 'credentials.json'` | Since "`read`" happens to be a <br/>[*function*](#calling-javascript-functions) (that takes a single <br/>string argument), this has the effect <br/>of loading *all* keys in the JSON file<br/>into [Shared Scope](#shared-scope) as [variables](#def) ! <br/>This *can* be [sometimes handy](karate-core#locator-lookup).
`* call read ('credentials.json')` | A common mistake. First, there <br/>is no meaning in `call` for JSON. <br/>Second, the space after the "`read`" <br/>makes this equal to the above.

### Calling Java
There are examples of calling JVM classes in the section on [Java Interop](#java-interop) and in the [file-upload demo](karate-demo). Also look at the section on [commonly needed utilities](#commonly-needed-utilities) for more ideas.

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
This is how it can be called from a test-script via [JavaScript](#javascript-functions), and yes, even static methods can be invoked:
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

Another example is [`dogs.feature`](karate-demo/src/test/java/demo/dogs/dogs.feature) -  which actually makes JDBC (database) calls, and since the data returned from the Java code is JSON, the last section of the test is able to use [`match`](#match) *very* effectively for data assertions.

A great example of how you can extend Karate, even bypass the HTTP client but still use Karate's test-automation effectively, is this [gRPC](https://grpc.io) example by [@thinkerou](https://github.com/thinkerou): [`karate-grpc`](https://github.com/thinkerou/karate-grpc). And you can even handle asynchronous flows such as [listening to message-queues](#async).

#### HTTP Basic Authentication Example
This should make it clear why Karate does not provide 'out of the box' support for any particular HTTP authentication scheme. Things are designed so that you can plug-in what you need, without needing to compile Java code. You get to choose how to manage your environment-specific configuration values such as user-names and passwords.

First the JavaScript file, `basic-auth.js`:
```javascript
function fn(creds) {
  var temp = creds.username + ':' + creds.password;
  var Base64 = Java.type('java.util.Base64');
  var encoded = Base64.getEncoder().encodeToString(temp.bytes);
  return 'Basic ' + encoded;
}
```
And here's how it works in a test-script using the [`header`](#header) keyword.

```cucumber
* header Authorization = call read('basic-auth.js') { username: 'john', password: 'secret' }
```

You can set this up for all subsequent requests or dynamically generate headers for each HTTP request if you [`configure headers`](#configure-headers).

## `callonce`
Cucumber has a limitation where [`Background`](#script-structure) steps are re-run for every `Scenario`. And if you have a `Scenario Outline`, this happens for *every* row in the `Examples`. This is a problem especially for expensive, time-consuming HTTP calls, and this has been an [open issue for a long time](https://github.com/cucumber/cucumber-jvm/issues/515). 

Karate's `callonce` keyword behaves exactly like [`call`](#call) but is guaranteed to execute only once. The results of the first call are cached, and any future calls will simply return the cached result instead of executing the JavaScript function (or feature) again and again. 

This does require you to move 'set-up' into a separate `*.feature` (or JavaScript) file. But this totally makes sense for things not part of the 'main' test flow and which typically need to be re-usable anyway.

So when you use the combination of `callonce` in a `Background`, you can indeed get the same effect as using a [`@BeforeClass`](http://junit.sourceforge.net/javadoc/org/junit/BeforeClass.html) annotation, and you can find examples in the [karate-demo](karate-demo), such as this one: [`callonce.feature`](karate-demo/src/test/java/demo/callonce/call-once.feature).

> Recommended only for experienced users - [`karate.callSingle()`](#karate-callsingle) is a way to invoke a feature or function 'globally' only once.

## `eval`
> This is for evaluating arbitrary JavaScript and you are advised to use this only as a last resort ! Conditional logic is not recommended especially within test scripts because [tests should be deterministic](https://martinfowler.com/articles/nonDeterminism.html).

There are a few situations where this comes in handy:
* you *really* don't need to assign a result to a variable
* statements in the `if` form (also see [conditional logic](#conditional-logic))
* 'one-off' logic (or [Java interop](#java-interop)) where you don't need the 'ceremony' of a [re-usable function](#calling-javascript-functions)
* JavaScript / JSON-style mutation of existing [variables](#def) as a dynamic alternative to [`set`](#set) and [`remove`](#remove) - by using [`karate.set()`](#karate-setpath) and [`karate.remove()`](#karate-remove).

```cucumber
# just perform an action, we don't care about saving the result
* eval myJavaScriptFunction()

# do something only if a condition is true
* eval if (zone == 'zone1') karate.set('temp', 'after')
```

As a convenience, you can omit the `eval` keyword and so you can shorten the above to:

```cucumber
* myJavaScriptFunction()
* if (zone == 'zone1') karate.set('temp', 'after')
```

This is __very__ convenient especially if you are calling a method on a variable that has been [defined](#def) such as the [`karate`](#the-karate-object) object, and for general-purpose scripting needs such as [UI automation](karate-core). Note how `karate.set()` and `karate.remove()` below are used directly as a script "statement".

```cucumber
# you can use multiple lines of JavaScript if needed
* eval
  """
  var foo = function(v){ return v * v };
  var nums = [0, 1, 2, 3, 4];
  var squares = [];
  for (var n in nums) {
    squares.push(foo(n));
  }
  karate.set('temp', squares);
  """
* match temp == [0, 1, 4, 9, 16]

* def json = { a: 1 }
* def key = 'b'
# use dynamic path expressions to mutate json
* json[key] = 2
* match json == { a: 1, b: 2 }
* karate.remove('json', '$.' + key)
* match json == { a: 1 }
* karate.set('json', '$.c[]', { d: 'e' })
* match json == { a: 1, c: [{ d: 'e' }] }
```

# Advanced / Tricks
## Polling
The built-in [`retry until`](#retry-until) syntax should suffice for most needs, but if you have some specific needs, this demo example (using JavaScript) should get you up and running: [`polling.feature`](karate-demo/src/test/java/demo/polling/polling.feature).

## Conditional Logic
The keywords [`Given` `When` `Then`](#given-when-then) are only for decoration and should not be thought of as similar to an `if - then - else` statement. And as a testing framework, Karate [discourages tests](https://martinfowler.com/articles/nonDeterminism.html) that give different results on every run.

That said, if you really need to implement 'conditional' checks, this can be one pattern:

```cucumber
* def filename = zone == 'zone1' ? 'test1.feature' : 'test2.feature'
* def result = call read(filename)
```

And this is another, using [`karate.call()`](#karate-call). Here we want to [`call`](#call) a file only if a condition is satisfied:

```cucumber
* def result = responseStatus == 404 ? {} : karate.call('delete-user.feature')
```

Or if we don't care about the result, we can [`eval`](#eval) an `if` statement:

```cucumber
* if (responseStatus == 200) karate.call('delete-user.feature')
```

And this may give you more ideas. You can always use a [JavaScript function](#javascript-functions) or [call Java](#calling-java) for more complex logic.

```cucumber
* def expected = zone == 'zone1' ? { foo: '#string' } : { bar: '#number' }
* match response == expected
```

### JSON Lookup
You can always use a JavaScript [`switch case`](https://www.w3schools.com/js/js_switch.asp) within an [`eval`](#eval) or [function](#javascript-functions) block. But one pattern that you should be aware of is that JSON is actually a great data-structure for looking up data.

```cucumber
* def data =
"""
{
   foo: 'hello',
   bar: 'world'  
}
"""
# in real-life key can be dynamic
* def key = 'bar'
# and used to lookup data
* match (data[key]) == 'world'
```

You can find more details [here](https://stackoverflow.com/a/59162760/143475). Also note how you can wrap the LHS of the [`match`](#match) in parentheses in the rare cases where the parser expects JsonPath by default.

### Abort and Fail

In some rare cases you need to exit a `Scenario` based on some condition. You can use [`karate.abort()`](#karate-abort) like so:

```cucumber
* if (responseStatus == 404) karate.abort()
```

Using `karate.abort()` will *not* fail the test. Conditionally making a test fail is easy with [`karate.fail()`](#karate-fail)

```cucumber
* if (condition) karate.fail('a custom message')
```

But normally a [`match`](#match) statement is preferred unless you want a really descriptive error message.

Also refer to [polling](#polling) for more ideas.

## Commonly Needed Utilities
Since it is so easy to dive into [Java-interop](#calling-java), Karate does not include any random-number functions, uuid generator or date / time utilities out of the box. You simply roll your own. 

Here is an example of how to get the current date, and formatted the way you want:

```cucumber
* def getDate =
  """
  function() {
    var SimpleDateFormat = Java.type('java.text.SimpleDateFormat');
    var sdf = new SimpleDateFormat('yyyy/MM/dd');
    var date = new java.util.Date();
    return sdf.format(date);
  } 
  """

* def temp = getDate()
* print temp
```

And the above will result in something like this being logged: `[print] 2017/10/16`. 

Here below are a few more common examples:

Utility | Recipe
------- | ------                               
| System Time (as a string) | `function(){ return java.lang.System.currentTimeMillis() + '' }`
| UUID  | `function(){ return java.util.UUID.randomUUID() + '' }`
| Random Number (`0` to `max-1`) | `function(max){ return Math.floor(Math.random() * max) }`
| Case Insensitive Comparison | `function(a, b){ return a.equalsIgnoreCase(b) }`
| Sleep or Wait for `pause` milliseconds | `function(pause){ java.lang.Thread.sleep(pause) }`

The first three are good enough for random string generation for most situations. Note that if you need to do a lot of case-insensitive string checks, [`karate.lowerCase()`](#karate-lowercase) is what you are looking for.

### Multiple Functions in One File
If you find yourself needing a complex helper or utility function, we strongly recommend that you [use Java](#calling-java) because it is much easier to maintain and even debug if needed. And if you need multiple functions, you can easily organize them into a single Java class with multiple static methods.

That said, if you want to stick to JavaScript, but find yourself accumulating a lot of helper functions that you need to use in multiple feature files, the following pattern is recommended.

You can organize multiple "common" utilities into a single re-usable feature file as follows e.g. `common.feature`

```cucumber
@ignore
Feature:

Scenario:
  * def hello = function(){ return 'hello' }
  * def world = function(){ return 'world' }
```

And then you have two options. The first option using [shared scope](#shared-scope) should be fine for most projects, but if you want to "name space" your functions, use "isolated scope":

```cucumber
Scenario: function re-use, global / shared scope
    * call read('common.feature')
    * assert hello() == 'hello'
    * assert world() == 'world'

Scenario: function re-use, isolated / name-spaced scope
    * def utils = call read('common.feature')
    * assert utils.hello() == 'hello'
    * assert utils.world() == 'world'
```

You can even move commonly used routines into [`karate-config.js`](#restrictions-on-global-variables) which means that they become "global". But we recommend that you do this only if you are sure that these routines are needed in almost *all* `*.feature` files. Bloating your configuration can lead to loss of performance, and maintainability may suffer.

## Async
The JS API has a couple of methods - [`karate.signal(result)`](#karate-signal) and [`karate.listen(timeout)`](#karate-listen) that are useful for involving asynchronous flows into a test. This is best [explained](https://github.com/intuit/karate/tree/master/karate-netty#consumer-provider-example) in this [example](karate-demo/src/test/java/mock/contract/payment-service.feature) that involves listening to an ActiveMQ / JMS queue. Note how [JS functions](#javascript-functions) defined at run-time can be mixed with custom [Java code](#java-interop) to get things done.

```cucumber
Background:
* def QueueConsumer = Java.type('mock.contract.QueueConsumer')
* def queue = new QueueConsumer(queueName)
* def handler = function(msg){ karate.signal(msg) }
* queue.listen(handler)
* url paymentServiceUrl + '/payments'

Scenario: create, get, update, list and delete payments
    Given request { amount: 5.67, description: 'test one' }
    When method post
    Then status 200
    And match response == { id: '#number', amount: 5.67, description: 'test one' }
    And def id = response.id
    * json shipment = karate.listen(5000)
    * print '### received:', shipment
    * match shipment == { paymentId: '#(id)', status: 'shipped' }
```

## WebSocket
Karate also has built-in support for [websocket](http://www.websocket.org) that is based on the [async](#async) capability. The following method signatures are available on the [`karate` JS object](#the-karate-object) to obtain a websocket reference:

* `karate.webSocket(url)`
* `karate.webSocket(url, handler)`
* `karate.webSocket(url, handler, options)` - where `options` is an optional JSON (or map-like) object that takes the following optional keys:
  * `subProtocol` - in case the server expects it
  * `headers` - another JSON of key-value pairs
  * `maxPayloadSize` - this defaults to 4194304 (bytes, around 4 MB)

These will init a websocket client for the given `url` and optional `subProtocol`. If a `handler` [function](#javascript-functions) (returning a boolean) is provided - it will be used to complete the "wait" of `socket.listen()` if `true` is returned - where `socket` is the reference to the websocket client returned by `karate.webSocket()`. A handler function is needed only if you have to ignore other incoming traffic. If you need custom headers for the websocket handshake, use JSON as the last argument.

Here is an example, where the same websocket connection is used to send as well as receive a message.

```cucumber
* def handler = function(msg){ return msg.startsWith('hello') }
* def socket = karate.webSocket(demoBaseUrl + '/websocket', handler)
* socket.send('Billie')
* def result = socket.listen(5000)
* match result == 'hello Billie !'
```

For handling binary messages, the same `karate.webSocket()` method signatures exist for `karate.webSocketBinary()`. Refer to these examples for more: [`echo.feature`](karate-demo/src/test/java/demo/websocket/echo.feature) | [`websocket.feature`](karate-demo/src/test/java/demo/websocket/websocket.feature). Note that any websocket instances created will be auto-closed at the end of the `Scenario`.

## Tags
Gherkin has a great way to sprinkle meta-data into test-scripts - which gives you some interesting options when running tests in bulk.  The most common use-case would be to partition your tests into 'smoke', 'regression' and the like - which enables being able to selectively execute a sub-set of tests.

The documentation on how to run tests via the [command line](#test-suites) has an example of how to use tags to decide which tests to *not* run (or ignore). Also see [`first.feature`](karate-demo/src/test/java/demo/tags/first.feature) and [`second.feature`](karate-demo/src/test/java/demo/tags/second.feature) in the [demos](karate-demo). If you find yourself juggling multiple tags with logical `AND` and `OR` complexity, refer to this [Stack Overflow answer](https://stackoverflow.com/a/34543352/143475) and this [blog post](https://testingneeds.wordpress.com/2015/09/15/junit-runner-with-cucumberoptions/).

> For advanced users, Karate supports being able to query for tags within a test, and even tags in a `@name=value` form. Refer to [`karate.tags`](#karate-tags) and [`karate.tagValues`](#karate-tagvalues).

### Tags And Examples
A little-known capability of the Cucumber / Gherkin syntax is to be able to tag even specific rows in a bunch of examples ! You have to repeat the `Examples` section for each tag. The example below combines this with the advanced features described above.

```cucumber
Scenario Outline: examples partitioned by tag
* def vals = karate.tagValues
* match vals.region[0] == expected

  @region=US
  Examples:
    | expected |
    | US       |

  @region=GB
  Examples:
    | expected |
    | GB       |
```

## Dynamic Port Numbers
In situations where you start an (embedded) application server as part of the test set-up phase, a typical challenge is that the HTTP port may be determined at run-time. So how can you get this value injected into the Karate configuration ?

It so happens that the [`karate`](#the-karate-object) object has a field called `properties` which can read a Java system-property by name like this: [`karate.properties['myName']`](#karate-properties). Since the `karate` object is injected within [`karate-config.js`](#configuration) on start-up, it is a simple and effective way for other processes within the same JVM to pass configuration values to Karate at run-time. Refer to the 'demo' [`karate-config.js`](karate-demo/src/test/java/karate-config.js) for an example and how the `demo.server.port` system-property is set-up in the test runner: [`TestBase.java`](karate-demo/src/test/java/demo/TestBase.java).

## Java API
It should be clear now that Karate provides a super-simple way to make HTTP requests compared to how you would have done so in Java. It is also possible to invoke a feature file via a Java API which can be very useful in some test-automation situations.

A common use case is to mix API-calls into a larger test-suite, for example a Selenium or WebDriver UI test. So you can use Karate to set-up data via API calls, then run the UI test-automation, and finally again use Karate to assert that the system-state is as expected. Note that you can even include calls to a database from Karate using [Java interop](#calling-java). And [this example](karate-demo/src/test/java/demo/dogs/dogs.feature) may make it clear why using Karate itself to drive even your UI-tests may be a good idea.

There are two static methods in `com.intuit.karate.Runner` (`runFeature()` and `runClasspathFeature()`) which are best explained in this demo unit-test: [`JavaApiTest.java`](karate-demo/src/test/java/demo/java/JavaApiTest.java). 

You can optionally pass in variable values or over-ride config via a `HashMap` or leave the second-last argument as `null`. The variable state after feature execution would be returned as a `Map<String, Object>`. The last `boolean` argument is whether the [`karate-config.js`](#configuration) should be processed or not. Refer to the documentation on [type-conversion](#type-conversion) to make sure you can 'unpack' data returned from Karate correctly, especially when dealing with XML.

## Hooks
If you are looking for [Cucumber 'hooks'](http://toolsqa.com/cucumber/cucumber-hooks/) Karate does not support them, mainly because they depend on Java code, which goes against the Karate Way™. 

Instead, Karate gives you all you need as part of the syntax. Here is a summary:

To Run Some Code | How
---------------- | ---
Before *everything* (or 'globally' once) | See [`karate.callSingle()`](#karatecallsingle)
Before every `Scenario` | Use the [`Background`](#script-structure). Note that [`karate-config.js`](#karate-configjs) is processed before *every* `Scenario` - so you can choose to put "global" config here, for example using [`karate.configure()`](#karate-configure).
Once (or at the start of) every `Feature` | Use a [`callonce`](#callonce) in the [`Background`](#script-structure). The advantage is that you can set up variables (using [`def`](#def) if needed) which can be used in all `Scenario`-s within that `Feature`.
After every `Scenario` | [`configure afterScenario`](#configure) (see [example](karate-demo/src/test/java/demo/hooks/hooks.feature))
At the end of the `Feature` | [`configure afterFeature`](#configure) (see [example](karate-demo/src/test/java/demo/hooks/hooks.feature))

### `karate.callSingle()`
Only recommended for advanced users, but this guarantees a routine is run only once, *even* when [running tests in parallel](#parallel-execution). You can use [`karate.callSingle()`](#karate-callsingle) in [`karate-config.js`](#karate-configjs) like this:

```js
var result = karate.callSingle('classpath:some/package/my.feature');
```

It can take a second JSON argument following the same rules as [`call`](#call). Once you get a result, you typically use it to set global variables.

Refer to this example:
* [`karate-config.js`](karate-demo/src/test/java/karate-config.js)
* [`headers-single.feature`](karate-demo/src/test/java/demo/headers/headers-single.feature)

> You *can* use `karate.callSingle()` directly in a `*.feature` file, but it logically fits better in the global "bootstrap".

#### `configure callSingleCache`
When re-running tests in development mode and when your test suite depends on say an `Authorization` header set by [`karate.callSingle()`](#karatecallsingle), you can cache the results locally to a file, which is very convenient when your "auth token" is valid for a period of a few minutes - which typically is the case. This means that as long as the token "on file" is valid, you can save time by not having to make the one or two HTTP calls needed to "sign-in" or create "throw-away" users in your SSO store.

So in "dev mode" you can easily set this behavior like this. Just ensure that this is "configured" *before* you use `karate.callSingle()`:

```js
if (karate.env == 'local') {
  karate.configure('callSingleCache', { minutes: 15 });
}
```

By default Karate will use `target` (or `build`) as the "cache" folder, which you can over-ride by adding a `dir` key:

```js
  karate.configure('callSingleCache', { minutes: 15, dir: 'some/other/folder' });
```

> This caching behavior will work only if the result of `karate.callSingle()` is a JSON-like object, and any JS functions or Java objects mixed in will be lost.

## Data Driven Tests
### The Cucumber Way
Cucumber has a concept of [Scenario Outlines](https://docs.cucumber.io/gherkin/reference/#scenario-outline) where you can re-use a set of data-driven steps and assertions, and the data can be declared in a very user-friendly fashion. Observe the usage of `Scenario Outline:` instead of `Scenario:`, and the new `Examples:` section.

You should take a minute to compare this with the [exact same example implemented in REST-assured and TestNG](https://github.com/basdijkstra/rest-assured-workshop/blob/d9734da98bfcd8087055bdcd78545581dd23cb77/src/test/java/answers/RestAssuredAnswers2Test.java). Note that this example only does a "string equals" check on *parts* of the JSON, but with Karate you are always encouraged to match the *entire* payload in one step.

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
This is great for testing boundary conditions against a single end-point, with the added bonus that your test becomes even more readable. This approach can certainly enable product-owners or domain-experts who are not programmer-folk, to review, and even collaborate on test-scenarios and scripts.

### Scenario Outline Enhancements
Karate has enhanced the Cucumber `Scenario Outline` as follows:
* __Type Hints__: if the `Examples` column header has a `!` appended, each value will be evaluated as a JavaScript data-type (number, boolean, or *even* in-line JSON) - else it defaults to string.
* __Magic Variables__: `__row` gives you the entire row as a JSON object, and `__num` gives you the row index (the first row is `0`).
* __Auto Variables__: in addition to `__row`, each column key-value will be available as a separate [variable](#def), which greatly simplifies JSON manipulation - especially when you want to re-use JSON [files](#reading-files) containing [embedded expressions](#embedded-expressions).
  * You can disable the "auto variables" behavior by setting the `outlineVariablesAuto` [`configure` setting](#configure) to `false`.
* Any empty cells will result in a `null` value for that column-key

These are best explained with [examples](karate-junit4/src/test/java/com/intuit/karate/junit4/demos/outline.feature). You can choose between the string-placeholder style `<foo>` or *directly* refer to the [variable](#def) `foo` (or even the *whole row* JSON as `__row`) in JSON-friendly [expressions](#karate-expressions).

Note that even the scenario name can accept placeholders - which is very useful in reports. 

```cucumber
Scenario Outline: name is <name> and age is <age>
  * def temp = '<name>'
  * match temp == name
  * match temp == __row.name
  * def expected = __num == 0 ? 'name is Bob and age is 5' : 'name is Nyan and age is 6'
  * match expected == karate.info.scenarioName

  Examples:
    | name | age |
    | Bob  | 5   |
    | Nyan | 6   |

Scenario Outline: magic variables with type hints
  * def expected = [{ name: 'Bob', age: 5 }, { name: 'Nyan', age: 6 }]
  * match __row == expected[__num]

  Examples:
    | name | age! |
    | Bob  | 5    |
    | Nyan | 6    |

Scenario Outline: embedded expressions and type hints
  * match __row == { name: '#(name)', alive: '#boolean' }

  Examples:
    | name | alive! |
    | Bob  | false  |
    | Nyan | true   |

Scenario Outline: inline json
  * match __row == { first: 'hello', second: { a: 1 } }
  * match first == 'hello'
  * match second == { a: 1 }

  Examples:
    | first  | second!  |
    | hello  | { a: 1 } |
```

For another example, see: [`examples.feature`](karate-demo/src/test/java/demo/outline/examples.feature).

### The Karate Way
The limitation of the Cucumber `Scenario Outline:` (seen above) is that the number of rows in the `Examples:` is fixed. But take a look at how Karate can [loop over a `*.feature` file](#data-driven-features) for each object in a JSON array - which gives you dynamic data-driven testing, if you need it. For advanced examples, refer to some of the scenarios within this [demo](karate-demo): [`dynamic-params.feature`](karate-demo/src/test/java/demo/search/dynamic-params.feature#L70).

Also see the option below, where you can data-drive an `Examples:` table using JSON.

### Dynamic Scenario Outline
You can feed an `Examples` table from a JSON array, which is great for those situations where the table-content is dynamically resolved at run-time. This capability is triggered when the table consists of a single "cell", i.e. there is exactly one row and one column in the table.  Here is an example (also see [this video](https://twitter.com/KarateDSL/status/1051433711814627329)):

```cucumber
Feature: scenario outline using a dynamic table

Background:
    * def kittens = read('../callarray/kittens.json')

Scenario Outline: cat name: <name>
    Given url demoBaseUrl
    And path 'cats'
    And request { name: '#(name)' }
    When method post
    Then status 200
    And match response == { id: '#number', name: '#(name)' }

    # the single cell can be any valid karate expression
    # and even reference a variable defined in the Background
    Examples:
    | kittens |
```

The great thing about this approach is that you can set-up the JSON array using the `Background` section. Any [Karate expression](#karate-expressions) can be used in the "cell expression", and you can even use [Java-interop](#calling-java) to use external data-sources such as a database. Note that Karate has built-in support for [CSV files](#csv-files) and here is an example: [`dynamic-csv.feature`](karate-demo/src/test/java/demo/outline/dynamic-csv.feature).
