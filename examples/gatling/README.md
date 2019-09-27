# karate-gatling-demo
demo sample project for karate [test-doubles](https://github.com/intuit/karate/tree/master/karate-netty) and [gatling integration](https://github.com/intuit/karate/tree/master/karate-gatling)

## Instructions

```
mvn clean test
```

The above works because the `gatling-maven-plugin` has been configured to run as part of the Maven `test` phase automatically in the [`pom.xml`](pom.xml).

The file location of the Gatling HTML report should appear towards the end of the console log. Copy and paste it into your browser address-bar.

Here's a video of what to expect: https://twitter.com/ptrthomas/status/986463717465391104
