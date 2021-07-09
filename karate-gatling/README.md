# Karate Gatling
## API Perf-Testing Made `Simple.`

# Index

<table>
<tr>
  <th>Start</th>
  <td>
      <a href="#maven">Maven</a>
    | <a href="#gradle">Gradle</a>
    | <a href="#logging">Logging</a>
    | <a href="#limitations">Limitations</a>
    | <a href="#usage">Usage</a>    
  </td>
</tr>
<tr>
  <th>API</th>
  <td>
      <a href="#karateprotocol"><code>karateProtocol()</code></a>
    | <a href="#nameresolver"><code>nameResolver</code></a>
    | <a href="#pausefor"><code>pauseFor()</code></a>
    | <a href="#runner"><code>runner</code></a>
    | <a href="#karatefeature"><code>karateFeature()</code></a>
    | <a href="#karateset"><code>karateSet()</code></a>
    | <a href="#tag-selector">Tag Selector</a>
    | <a href="#ignore-tags">Ignore Tags</a>
  </td>
</tr>
<tr>
  <th>Advanced</th>
  <td>
      <a href="#gatling-session">Session</a>
    | <a href="#feeders">Feeders</a>
    | <a href="#chaining">Chaining</a>
    | <a href="#karatecallsingle"><code>karate.callSingle()</code></a>
    | <a href="#detecting-gatling-at-run-time">Detecting Gatling At Run Time</a>
    | <a href="#think-time">Think Time</a>
    | <a href="#configure-localaddress"><code>configure localAddress</code></a>
    | <a href="#custom">Profiling Custom Java Code</a>
    | <a href="#captureperfevent"><code>PerfContext.capturePerfEvent()</code></a>
    | <a href="#increasing-thread-pool-size">Increasing Thread Pool Size</a>
    | <a href="#distributed-testing">Distributed Testing</a>   
  </td>
</tr>
</table>

### Capabilities
* Re-use Karate tests as performance tests executed by [Gatling](https://gatling.io)
* Use Gatling (and Scala) only for defining the load-model, everything else can be in Karate
* Karate assertion failures appear in Gatling report, along with the line-numbers that failed
* Leverage Karate's powerful assertion capabilities to check that server responses are as expected under load - which is much harder to do in Gatling and other performance testing tools
* API invocation sequences that represent end-user workflows are much easier to express in Karate
* [*Anything*](#custom) that can be written in Java can be performance tested !
* Option to scale out by [distributing a test](#distributed-testing) over multiple hardware nodes or Docker containers

## Demo Video
Refer: https://twitter.com/ptrthomas/status/986463717465391104


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

You will also need the [Gatling Maven Plugin](https://github.com/gatling/gatling-maven-plugin), refer to the [sample project](../examples/gatling) for how to use this for a typical Karate project where feature files are in `src/test/java`. For convenience we recommend you keep even the Gatling simulation files in the same folder hierarchy, even though they are technically files with a `*.scala` extension.

```xml
  <plugin>
      <groupId>io.gatling</groupId>
      <artifactId>gatling-maven-plugin</artifactId>
      <version>${gatling.plugin.version}</version>
      <configuration>
          <simulationsFolder>src/test/java</simulationsFolder>
          <includes>
              <include>mock.CatsKarateSimulation</include>
          </includes>
      </configuration>
      <executions>
          <execution>
              <phase>test</phase>
              <goals>
                  <goal>test</goal>
              </goals>
          </execution>
      </executions>                
  </plugin>
```

Because the `<execution>` phase is defined, just running `mvn clean test` will work. If you don't want to run Gatling tests as part of the normal Maven "test" lifecycle, you can avoid the `<executions>` section and instead manually invoke the Gatling plugin from the command-line.

```
mvn clean test-compile gatling:test
```

And in case you have multiple Gatling simulation files and you want to choose only one to run:

```
mvn clean test-compile gatling:test -Dgatling.simulationClass=mock.CatsKarateSimulation
```

It is worth calling out that in the sample project, we are perf-testing [Karate test-doubles](https://hackernoon.com/api-consumer-contract-tests-and-test-doubles-with-karate-72c30ea25c18) ! A truly self-contained demo.

### Gradle

For those who use [Gradle](https://gradle.org), this sample [`build.gradle`](../examples/gatling/build.gradle) provides a `gatlingRun` task that executes the Gatling test of the `karate-netty` project - which you can use as a reference. The approach is fairly simple, and does not require the use of any Gradle Gatling plugins.

Most problems when using Karate with Gradle occur when "test-resources" are not configured properly. So make sure that all your `*.js` and `*.feature` files are copied to the "resources" folder - when you build the project.

## Limitations
Karate introduces a different threading model for the HTTP Client, so if you have to attain a high RPS (Requests Per Second) value, you need to introduce a config-file that is normally not required for "vanilla" Gatling. We have found that by default an RPS of around 30 is suppported, but to go higher - please see [Increasing Thread Pool Size](#increasing-thread-pool-size).

As of now the Gatling concept of ["throttle" and related syntax](https://gatling.io/docs/2.3/general/simulation_setup/#simulation-setup-throttling) is not supported. Most teams don't need this, but you can declare "pause" times in Karate, see [`pauseFor()`](#pausefor).

## Logging
Once you have your performance tests working, you may want to tune the logging config. Note that there are options to [reduce or completely suppress](https://github.com/intuit/karate#logging) the console logging.

Also note that the [`logback-test.xml`](../examples/gatling/src/test/java/logback-test.xml) in the examples project uses [`<immediateFlush>false</immediateFlush>`](http://logback.qos.ch/manual/appenders.html#OutputStreamAppender).

## Usage

Let's look at an [example](src/test/scala/mock/CatsSimulation.scala):

```scala
package mock

import com.intuit.karate.gatling.PreDef._
import io.gatling.core.Predef._
import scala.concurrent.duration._

class CatsSimulation extends Simulation {

  val protocol = karateProtocol(
    "/cats/{id}" -> Nil,
    "/cats" -> pauseFor("get" -> 15, "post" -> 25)
  )

  protocol.nameResolver = (req, ctx) => req.getHeader("karate-name")
  protocol.runner.karateEnv("perf")

  val create = scenario("create").exec(karateFeature("classpath:mock/cats-create.feature"))
  val delete = scenario("delete").exec(karateFeature("classpath:mock/cats-delete.feature@name=delete"))

  setUp(
    create.inject(rampUsers(10) during (5 seconds)).protocols(protocol),
    delete.inject(rampUsers(5) during (5 seconds)).protocols(protocol)
  )

}
```
### `karateProtocol()`
This piece is needed because Karate is responsible for making HTTP requests while Gatling is only measuring the timings and managing threads. In order for HTTP requests to "aggregate" correctly in the Gatling report, you need to declare the URL patterns involved in your test. For example, in the example above, the `{id}` would be random - and Gatling would by default report each one as a different request.

#### `nameResolver`
This is optional, and is useful for teams that need more control over the "segregation" of requests described above. This is especially needed for GraphQL and SOAP - where the URI and request-paths remain constant and only the payload changes. You can supply a function that takes 2 Karate core-objects as arguments. The first argument [`HttpRequestBuilder`](../karate-core/src/main/java/com/intuit/karate/http/HttpRequestBuilder.java) is all you would typically need, and gives you ways to access the HTTP request such as `getUrlAndPath()`, `getHeader(name)` and `getParameter(name)`. The example below over-rides the "request name" with the value of a custom-header:

```scala
 protocol.nameResolver = (req, ctx) => req.getHeader("karate-name")
```

For convenience, if the `nameResolver` returns `null`, Karate will fall-back to  the default strategy. And `HttpRequestBuilder.getHeader(name)` happens to return `null` if the header does not exist.

So any HTTP request where a `karate-name` header is present can be "collected" in the Gatling report under a different name. This is how it could look like in a Karate feature ([example](src/test/scala/mock/cats-delete-one.feature)):

```cucumber
Given path id
And header karate-name = 'cats-get-404'
When method get
```

#### `pauseFor()`
You can also set pause times (in milliseconds) per URL pattern *and* HTTP method (`get`, `post` etc.) if needed (see [limitations](#limitations)). If non-zero, this pause will be applied *before* the invocation of the matching HTTP request.

We recommend you set that to `0` for everything unless you really need to artifically limit the requests per second. Note how you can use `Nil` to default to `0` for all HTTP methods for a URL pattern. Make sure you wire up the `protocol` in the Gatling `setUp`. If you use a [`nameResolver`](#nameresolver), even those names can be used in the `pauseFor` lookup (instead of a URL pattern).

Also see how to [`pause()`](#think-time) without blocking threads if you really need to do it *within* a Karate feature, for e.g. to simulate user "think time" - in more detail.

#### `runner`
Which feature to call and what tags to use are driven by the [`karateFeature()`](#karatefeature) syntax as described in later sections. Most of the time this would be sufficient. But in cases where you have custom configuration, you will need a way to replicate what you may be doing using the [`Runner.Builder`](https://github.com/intuit/karate#parallel-execution) methods. Most of the time this would be setting the `karate.env`.

To enable this, a `Runner.Builder` instance is made available on the `protocol` in a variable called `runner` and all the builder methods such as `karateEnv()`, `configDir()` and `systemProperty()` can be configured.

Note that tags are typically set by the use of `karateFeature()`. If you call `tags()` on the `runner` instance, they will be inherited by all `karateFeature()` calls, where you can add more tags, but you can't *remove* any that were set on the `runner`.

Here is an example of setting the `karate.env` to `perf` which means that `karate-config-perf.js` will be used in addition to `karate-config.js` for [bootstrapping the config](https://github.com/intuit/karate#configuration) for each `Scenario`.

```scala
  protocol.runner.karateEnv("perf")
```

But the alternate mechanism of setting a Java system-property `karate.env` via the command-line is always an option, so using the `runner` can be avoided in most cases.

### `karateFeature()`
This declares a whole Karate feature as a "flow". Note how you can have concurrent flows in the same Gatling simulation.

#### Tag Selector
In the code above, note how a single `Scenario` (or multiple) can be "chosen" by appending the [tag](https://github.com/intuit/karate#tags) name to the `Feature` path. This allows you to re-use only selected tests out of your existing functional or regression test suites for composing a performance test-suite.

If multiple `Scenario`-s have the tag on them, they will all be executed. The order of execution will be the order in which they appear in the `Feature`.

> The tag does not need to be in the `@key=value` form and you can use the plain "`@foo`" form if you want to. But using the pattern `@name=someName` is arguably more readable when it comes to giving multiple `Scenario`-s meaningful names.

#### Ignore Tags
The above [Tag Selector](#tag-selector) approach is designed for simple cases where you have to pick and run only one `Scenario` out of many. Sometimes you will need the full flexibility of [tag combinations](https://github.com/intuit/karate#tags) and "ignore". The `karateFeature()` method takes an optional (vararg) set of Strings after the first feature-path argument. For example you can do this:

```scala
  val delete = scenario("delete").exec(karateFeature("classpath:mock/cats-delete.feature", "@name=delete"))
```

To exclude (note that `@ignore` is skipped by default):

```scala
  val delete = scenario("delete").exec(karateFeature("classpath:mock/cats-delete.feature", "~@skipme"))
```

To run scenarios tagged `foo` OR `bar`

```scala
  val delete = scenario("delete").exec(karateFeature("classpath:mock/cats-delete.feature", "@foo,@bar"))
```

And to run scenarios tagged `foo` AND `bar`

```scala
  val delete = scenario("delete").exec(karateFeature("classpath:mock/cats-delete.feature", "@foo", "@bar"))
```

### Karate Variables
On the Scala side, after a `scenario` involving a [`karateFeature()`](#karatefeature) completes, the Karate variables that were part of the feature will be added to the [Gatling session](#gatling-session).

This is rarely needed - but useful when you want to pass data across feature files or do some assertions on the Gatling side. Here is an [example](src/test/scala/mock/CatsSimulation.scala):

```scala
val create = scenario("create").exec(karateFeature("classpath:mock/cats-create.feature")).exec(session => {
    println("*** id in gatling: " + session("id").as[String])
    println("*** session status in gatling: " + session.status)
    session
  })
```

Here above, the variable `id` that was defined (using `def`) in the [Karate feature](src/test/scala/mock/cats-create.feature) - is being retrieved on the Gatling side using the Scala API.

On the karate side, after scenario involving a [`karateFeature()`](#karatefeature) completes, the variables  are passed onto [`karateFeature()`](#karatefeature) invocations as indicated in the [simulation example](src/test/scala/mock/CatsCreateReadSimulation.scala) - in the read `id` from the one defined post create.

Also see [chaining](#chaining) - where Karate variables created in one `Scenario` can flow into others in advanced Gatling set-ups.

### Gatling Session
The [Gatling session](https://gatling.io/docs/current/session/session_api/) attributes and `userId` would be available in a Karate variable under the name-space `__gatling`. So you can refer to the user-id for the thread as follows:

```cucumber
* print 'gatling userId:', __gatling.userId
```

This is useful as an alternative to using a random UUID where you want to create unique users, and makes it easy to co-relate values to your test-run in some situations.

For advanced Gatling simulations -  the state of Karate variables at the end of a `Feature` execution will be automatically injected into the Gatling session and available to other `karateFeature()` executions within the same Gatling "scenario" (note that the terminology "scenario" here is specific to Gatling). See [Chaining](#chaining) for more.

So there are two ways to pass data from Gatling to a Karate `Feature`. The first is the use of the `__gatling` "special" variable. But when you want to minimize conditional logic in your Karate scripts, you can use [`karateSet()`](#karateset) to set-up variables directly. So prefer the second approach, as the goal should be to re-use Karate functional-tests as performance-tests without making any changes to the `Feature` files.

### Feeders
Because of the above mechanism which allows Karate to "see" Gatling session data, you can use [feeders](https://gatling.io/docs/current/session/feeder) effectively. For example:

```scala
val feeder = Iterator.continually(Map("catName" -> MockUtils.getNextCatName, "someKey" -> "someValue"))

val create = scenario("create").feed(feeder).exec(karateFeature("classpath:mock/cats-create.feature"))
```

There is some [Java code behind the scenes](../examples/gatling/src/test/java/mock/MockUtils.java) that takes care of dispensing a new `catName` every time `getNextCatName()` is invoked:

```java
private static final AtomicInteger counter = new AtomicInteger();

public static String getNextCatName() {
    return catNames.get(counter.getAndIncrement() % catNames.size());
}
```

The `List` of `catNames` above is actually initialized (only once) by a [Java API call](https://github.com/intuit/karate#java-api) to another Karate feature (see below). If you use `true` instead of `false`, the `karate-config.js` will be honored. You could also pass custom config via the second `Map` argument to `Runner.runFeature()`. This is just to demonstrate some possibilities, and you can use any combination of Java or [Scala](https://gatling.io/docs/current/session/feeder) (even without Karate) - to set up feeders.

```java
List<String> catNames = (List) Runner.runFeature("classpath:mock/feeder.feature", null, false).get("names");
```

And now in the feature file you can do this:

```cucumber
* print __gatling.catName
```

### Chaining
Normally we recommend that `Scenario`-s should be self-contained and that you should model any "flows" of calls made to API-s or code within a single `Scenario` itself or by using [`call`](https://github.com/intuit/karate#call).

But for advanced load-modelling, you may want to compose `Scenario`-s along with Gatling [feeders](#feeders) and have variables "flow" from one `Scenario` into another. This way you will be able to use all of Gatling's features such as [grouping](https://gatling.io/docs/current/general/scenario/#groups-definition), [assertions](https://gatling.io/docs/current/general/assertions/) and control over each [sub-execution](https://gatling.io/docs/current/general/scenario/#exec).

So the rule is that any variable created within the `exec()` of a [`karateFeature()`](#karatefeature) would return back to the Gatling session, and be automatically injected into any subsequent Karate feature within the same Gatling scenario.

And if you have a need to create new variables on the "Gatling side" and inject them into Karate features, you can use `karateSet()`, explained below.

#### `karateSet()`
[This example](src/test/scala/mock/CatsChainedSimulation.scala) shows how you can use the `karateSet()` Gatling action to pipe data from the Gatling session (typically a feeder) into variables that Karate can access.

#### `karate.callSingle()`
A common need is to run a routine, typically a sign-in and setting up of an `Authorization` header only *once* - for all `Feature` invocations. Keep in mind that when you use Gatling, what used to be a single `Feature` in "normal" Karate will now be multiplied by the number of users you define. But [`callonce`](https://github.com/intuit/karate#callonce) is designed so that it will be "once for everything" when Karate is running in Gatling "perf" mode.

You can also use [`karate.callSingle()`](https://github.com/intuit/karate#hooks) in these situations. Ideally you should use [Feeders](#feeders) since `callonce` and `karate.callSingle()` will lock all threads - which may not play very well with Gatling. But when you want to quickly re-use existing Karate tests as performance tests, this will work nicely.

#### Detecting Gatling At Run Time
You would typically want your feature file to be usable when not being run via Gatling, so you can use this pattern, since [`karate.get()`](https://github.com/intuit/karate#karate-get) has an optional second argument to use as a "default" value if the variable does not exist or is `null`.

```cucumber
* def name = karate.get('__gatling.catName', 'Billie')
```

For a full, working, stand-alone example, refer to the [`karate-gatling-demo`](../examples/gatling/src/test/java/mock).

#### Think Time
Gatling provides a way to [`pause()`](https://gatling.io/docs/current/general/scenario/#scenario-pause) between HTTP requests, to simulate user "think time". But when you have all your requests in a Karate feature file, this can be difficult to simulate - and you may think that adding `java.lang.Thread.sleep()` here and there will do the trick. But no, what a `Thread.sleep()` will do is *block threads* - which is a very bad thing in a load simulation. This will get in the way of Gatling, which is specialized to generate load in a non-blocking fashion.

The [`karate.pause()`](https://github.com/intuit/karate#karate-pause) function is specially designed to use the Gatling session if applicable - or do nothing.

```cucumber
* karate.pause(5000)
```

You normally don't want to slow down your functional tests. But you can use the [`configure pauseIfNotPerf`](https://github.com/intuit/karate#configure) flag (default `false`) to have `karate.pause()` work even in "normal" mode.

## `configure localAddress`
> This is implemented only for the `karate-apache` HTTP client. Note that the IP address needs to be [*physically assigned* to the local machine](https://www.blazemeter.com/blog/how-to-send-jmeter-requests-from-different-ips/).

Gatling has a way to bind the HTTP "protocol" to [use a specific "local address"](https://gatling.io/docs/3.2/http/http_protocol/#local-address), which is useful when you want to use an IP range to avoid triggering rate-limiting on the server under test etc. But since Karate makes the HTTP requests, you can use the [`configure`](https://github.com/intuit/karate#configure) keyword, and this can actually be done *any* time within a Karate script or `*.feature` file.

```cucumber
* configure localAddress = '123.45.67.89'
```

One easy way to achieve a "round-robin" effect is to write a simple Java static method that will return a random IP out of a pool. See [feeders](#feeders) for example code. Note that you can "conditionally" perform a `configure` by using the JavaScript API on the `karate` object:

```cucumber
* if (__gatling) karate.configure('localAddress', MyUtil.getIp())
```

Since you can [use Java code](https://github.com/intuit/karate#calling-java), any kind of logic or strategy should be possible, and you can refer to [config or variables](https://github.com/intuit/karate#configuration) if needed.

## Custom
You can even include any custom code you write in Java into a performance test, complete with full Gatling reporting.

What this means is that you can easily script performance tests for database-access, [gRPC](https://grpc.io), proprietary non-HTTP protocols or pretty much *anything*, really.

Just use a single Karate interface called [`PerfContext`](../karate-core/src/main/java/com/intuit/karate/PerfContext.java). Here is an [example](src/test/scala/mock/MockUtils.java):

 ```java
public static Map<String, Object> myRpc(Map<String, Object> map, PerfContext context) {
    long startTime = System.currentTimeMillis();
    // this is just an example, you can put any kind of code here
    int sleepTime = (Integer) map.get("sleep");
    try {
        Thread.sleep(sleepTime);
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
    long endTime = System.currentTimeMillis();
    // and here is where you send the performance data to the reporting engine
    context.capturePerfEvent("myRpc-" + sleepTime, startTime, endTime);
    return Collections.singletonMap("success", true);
}
 ```

### `capturePerfEvent()`
The `PerfContext.capturePerfEvent()` method takes these arguments:
* `eventName` - string, which will show up in the Gatling report
* `startTime` - long
* `endTime` - long

### `PerfContext`
To get a reference to the current `PerfContext`, just pass the built-in `karate` JavaScript object from the "Karate side" to the "Java side". For [example](src/test/scala/mock/custom-rpc.feature):

```cucumber
Background:
  * def Utils = Java.type('mock.MockUtils')

Scenario: fifty
  * def payload = { sleep: 50 }
  * def response = Utils.myRpc(payload, karate)
  * match response == { success: true }
```

The `karate` object happens to implement the `PerfContext` interface and keeps your code simple. Note how the `myRpc` method has been [implemented to accept a `Map`](https://github.com/intuit/karate#calling-java) (auto-converted from JSON) and the `PerfContext` as arguments. 

Like the built-in HTTP support, any test failures are automatically linked to the previous "perf event" captured.

## Increasing Thread Pool Size
The defaults should suffice most of the time, but if you see odd behavior such as freezing of a test, you can change the settings for the underlying Akka engine. A typical situation is when one of your responses takes a very long time to respond (30-60 seconds) and the system is stuck waiting for threads to be freed.

Add a file called [`gatling-akka.conf`](src/test/resources/gatling-akka.conf) to the root of the classpath (typically `src/test/resources`). Here is an example:

```
akka {
  actor {
    default-dispatcher {
      type = Dispatcher
      executor = "thread-pool-executor"
      thread-pool-executor {
        fixed-pool-size = 100
      }
      throughput = 1
    }
  }
}
```

So now the system can go up to 100 threads waiting for responses. You can experiment with more settings as [described here](https://doc.akka.io/docs/akka/current/typed/dispatchers.html). Of course a lot will depend on the compute resources (CPU, RAM) available on the machine on which you are running a test.

## Distributed Testing
See wiki: [Distributed Testing](https://github.com/intuit/karate/wiki/Distributed-Testing#gatling)
