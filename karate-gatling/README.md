# Karate Gatling
## API Perf-Testing Made `Simple.`

### Capabilities
* Re-use Karate tests as performance tests executed by [Gatling](https://gatling.io)
* Use Gatling (and Scala) only for defining the load-model, everything else can be in Karate
* Karate assertion failures appear in Gatling report, along with the line-numbers that failed
* Leverage Karate's powerful assertion capabilities to check that server responses are as expected under load - which is hard to do in Gatling and other performance testing tools
* API invocation sequences that represent end-user workflows are much easier to express in Karate

## Demo Video
Refer: https://twitter.com/ptrthomas/status/986463717465391104

## Sample Project:
Refer: https://github.com/ptrthomas/karate-gatling-demo

### Maven
```xml
<dependency>
    <groupId>com.intuit.karate</groupId>
    <artifactId>karate-gatling</artifactId>
    <version>${karate.version}</version>
    <scope>test</scope>
</dependency>  
```

Since the above does *not* include the [`karate-apache` (or `karate-jersey`)]((https://github.com/intuit/karate#maven)) dependency you will need to include that as well.

> The demo project happens to depend on `karate-netty` which already depends on `karate-apache`.

You will also need the [Gatling Maven Plugin](https://github.com/gatling/gatling-maven-plugin), refer to the above [sample project](https://github.com/ptrthomas/karate-gatling-demo) for how to use this for a typical Karate project where feature files are in `src/test/java`. For convenience we recommend you keep even the Gatling simulation files in the same folder hierarchy, even though they are technically files with a `*.scala` extension.

