# Karate Demo
This is a sample [Spring Boot](http://projects.spring.io/spring-boot/) web-application that exposes some functionality as web-service end-points. And includes a set of Karate examples that test these services
as well as demonstrate various Karate features and best-practices.

| Example | Demonstrates
----------| --------
[`greeting.feature`](src/test/java/demo/greeting/greeting.feature) | Simple GET requests and multiple scenarios in a test.
[`cats.feature`](src/test/java/demo/cats/cats.feature) | Great example of [embedded-expressions](https://github.com/intuit/karate#embedded-expressions) (or JSON / XML templating). Also shows how to set the `Accept` header for getting XML from the server.
[`kittens.feature`](src/test/java/demo/cats/kittens.feature) | Reading a complex payload expected response [from a file](https://github.com/intuit/karate#reading-files). You can do the same for request payloads as well. Observe how [JSON templating](https://github.com/intuit/karate#embedded-expressions) makes creating dynamic JSON super-easy, look at [line #24](src/test/java/demo/cats/kittens.feature#L24) for example.
[`upload.feature`](src/test/java/demo/upload/upload.feature) | Multi-part file-upload example, as well as comparing the binary content of a download. Also shows how to assert for expected response headers.

## Best Practices
| File | Demonstrates
----------| --------
[`karate-config.js`](src/test/java/karate-config.js) | Shows how the `demoBaseUrl` property is injected into all the test scripts on startup. Notice how JavaScript allows you to perform simple conditional logic and string manipulation, while still being a 'devops-friendly' plain-text file.
[`BaseTest.java`](src/test/java/demo/BaseTest.java#L22) | This is specific to Spring Boot, but this code takes care of starting the embedded app-server and dynamically chooses a free port. The chosen port value is passed to the above config routine via a Java `System.setProperty()` call.
[`TestAll.java`](src/test/java/demo/TestAll.java) | This Java class is strategically placed at the root of the directory structure containing `*.feature` files. The reason will be apparent in the next line.
[`pom.xml`](pom.xml#L66) | Line 66 shows how the [`maven-surefire-plugin`](http://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html) can be configured to point to what is basically your 'test-suite'. Refer to the Karate documentation on how you could choose to select a sub-set of tests using [tags](https://github.com/intuit/karate#cucumber-tags)


