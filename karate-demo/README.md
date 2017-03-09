# Karate Demo
This is a sample [Spring Boot](http://projects.spring.io/spring-boot/) web-application that exposes some functionality as web-service end-points. And includes a set of Karate examples that test these services
as well as demonstrate various Karate features and best-practices.

| Example | Demonstrates
----------| --------
[`greeting.feature`](src/test/java/demo/greeting/greeting.feature) | Simple GET requests and multiple scenarios in a test.
[`cats.feature`](src/test/java/demo/cats/cats.feature) | Great example of [embedded-expressions](https://github.com/intuit/karate#embedded-expressions) (or JSON / XML templating). Also shows how to set the `Accept` [header](https://github.com/intuit/karate#header) for getting XML from the server.
[`kittens.feature`](src/test/java/demo/cats/kittens.feature) | Reading a complex payload expected response [from a file](https://github.com/intuit/karate#reading-files). You can do the same for request payloads as well. Observe how [JSON templating](https://github.com/intuit/karate#embedded-expressions) makes creating dynamic JSON super-easy, look at [line #24](src/test/java/demo/cats/kittens.feature#L24) for example.
[`upload.feature`](src/test/java/demo/upload/upload.feature) | [Multi-part](https://github.com/intuit/karate#multipart-field) file-upload example, as well as comparing the binary content of a download. Also shows how to assert for expected response [headers](https://github.com/intuit/karate#match-header).
[`caller.feature`](src/test/java/demo/callfeature/caller.feature) | How you can re-use a sequence of HTTP calls in a `*.feature` file from other test scripts. This is hugely useful for those common authentication or 'set up' flows that create users, etc. Refer to the [main documentation](https://github.com/intuit/karate#calling-other-feature-files) on how you can pass parameters in and get data back from the 'called' script.

## Best Practices
| File | Demonstrates
----------| --------
[`karate-config.js`](src/test/java/karate-config.js) | Shows how the `demoBaseUrl` property is injected into all the test scripts on startup. Notice how JavaScript allows you to perform simple conditional logic and string manipulation, while still being a 'devops-friendly' plain-text file.
[`TestBase.java`](src/test/java/demo/TestBase.java#L22) | This is specific to Spring Boot, but this code takes care of starting the embedded app-server and dynamically chooses a free port. The chosen port value is passed to the above config routine via a Java `System.setProperty()` call.
[`DemoTest.java`](src/test/java/demo/DemoTest.java) | This Java class is strategically placed at the root of the directory structure containing `*.feature` files. Note how the [`@CucumberOptions`](https://cucumber.io/docs/reference/jvm#configuration) annotation generates reports so that you don't have to specify these options on the command line. And if you have any `*.feature` files you need to skip, you can add `@ignore` to the start of those files.
[`pom.xml`](pom.xml#L66) | Line 66 shows how the [`maven-surefire-plugin`](http://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html) can be configured to point to what is basically your 'test-suite'. You actually may not even need to do this if you follow the [recommended naming conventions and folder structure](https://github.com/intuit/karate#naming-conventions), and then Maven defaults would work as you would expect.


