# Karate Demo
This is a sample [Spring Boot](http://projects.spring.io/spring-boot/) web-application that exposes some functionality as web-service end-points. And includes a set of Karate examples that test these services
as well as demonstrate various Karate features and best-practices.

| Example | Demonstrates
----------| --------
[`greeting.feature`](src/test/java/demo/greeting/greeting.feature) | Simple GET requests and multiple scenarios in a test.
[`headers.feature`](src/test/java/demo/headers/headers.feature)  | A simple example of header management using a JS file ([`classpath:headers.js`](src/test/java/headers.js)), and also shows how cookies can be accessed and how path and query parameters can be specified.
[`cats.feature`](src/test/java/demo/cats/cats.feature) | Great example of [embedded-expressions](https://github.com/intuit/karate#embedded-expressions) (or JSON / XML templating). Also shows how to set the `Accept` [header](https://github.com/intuit/karate#header) for getting XML from the server.
[`kittens.feature`](src/test/java/demo/cats/kittens.feature) | Reading a complex payload expected response [from a file](https://github.com/intuit/karate#reading-files). You can do the same for request payloads as well. Observe how [JSON templating](https://github.com/intuit/karate#embedded-expressions) makes creating dynamic JSON super-easy, look at [line #24](src/test/java/demo/cats/kittens.feature#L24) for example.
[`upload.feature`](src/test/java/demo/upload/upload.feature) | [Multi-part](https://github.com/intuit/karate#multipart-field) file-upload example, as well as comparing the binary content of a download. Also shows how to assert for expected response [headers](https://github.com/intuit/karate#match-header). Plus an example of how to call [custom Java code](https://github.com/intuit/karate#calling-java) from Karate, you can extend this approach to make JDBC calls or do pretty much anything Java can.
[`call-feature.feature`](src/test/java/demo/callfeature/call-feature.feature) | How you can re-use a sequence of HTTP calls in a `*.feature` file from other test scripts. This is hugely useful for those common authentication or 'set up' flows that create users, etc. Refer to the [main documentation](https://github.com/intuit/karate#calling-other-feature-files) on how you can pass parameters in and get data back from the 'called' script.
[`call-json-array.feature`](src/test/java/demo/callarray/call-json-array.feature) | This example loads JSON data from a file and uses it to call a `*.feature` file in a loop. This approach can enable very dynamic data-driven tests, since there are a variety of ways by which you can create the JSON data, for example by calling custom Java code.
[`call-table.feature`](src/test/java/demo/calltable/call-table.feature) | This is a great example of how Karate combines with Cucumber and JsonPath to give you an extremely readable data-driven test. Karate's [`table`](https://github.com/intuit/karate#table) keyword is a super-elegant and readable way to create JSON arrays, perfect for setting up all manner of data-driven tests.


## Best Practices
| File | Demonstrates
----------| --------
[`karate-config.js`](src/test/java/karate-config.js) | Shows how the `demoBaseUrl` property is injected into all the test scripts on startup. Notice how JavaScript allows you to perform simple conditional logic and string manipulation, while still being a 'devops-friendly' plain-text file. It is good practice to set the `connectTimeout` and `readTimeout` so that your tests 'fail fast' if servers don't respond. Refer to the [main documentation](https://github.com/intuit/karate#configuration) for more on configuration.
[`TestBase.java`](src/test/java/demo/TestBase.java#L22) | This is specific to Spring Boot, but this code takes care of starting the embedded app-server and dynamically chooses a free port. The chosen port value is passed to the above config routine via a Java `System.setProperty()` call.
[`DemoTest.java`](src/test/java/demo/DemoTest.java) | This Java class is strategically placed at the root of the directory structure containing `*.feature` files. Note how the [`@CucumberOptions`](https://cucumber.io/docs/reference/jvm#configuration) annotation allows you to skip any `*.feature` files if they have `@ignore` at the start. The `plugin` option specifies the reports and formats desired, which can be over-ridden on the command-line by the maven config described below.
[`pom.xml`](pom.xml#L60) | Line 60 shows how the [`maven-surefire-plugin`](http://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html) can be configured to point to what is basically your 'test-suite'. You may not even need to do this if you follow the [recommended naming conventions and folder structure](https://github.com/intuit/karate#naming-conventions), and then Maven defaults would work as you would expect.


