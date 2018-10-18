# Karate Gatling
## API Perf-Testing Made `Simple.`
### Capabilities
* Re-use Karate tests as performance tests executed by [Gatling](https://gatling.io)
* Use Gatling (and Scala) only for defining the load-model, everything else can be in Karate
* Karate assertion failures appear in Gatling report, along with the line-numbers that failed
* Leverage Karate's powerful assertion capabilities to check that server responses are as expected under load - which is much harder to do in Gatling and other performance testing tools
* API invocation sequences that represent end-user workflows are much easier to express in Karate
* [*Anything*](#custom) that can be written in Java can be performance tested !

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

You will also need the [Gatling Maven Plugin](https://github.com/gatling/gatling-maven-plugin), refer to the below [sample project](https://github.com/ptrthomas/karate-gatling-demo) for how to use this for a typical Karate project where feature files are in `src/test/java`. For convenience we recommend you keep even the Gatling simulation files in the same folder hierarchy, even though they are technically files with a `*.scala` extension.

## Sample Project:
Refer: https://github.com/ptrthomas/karate-gatling-demo

It is worth calling out that we are perf-testing [Karate test-doubles](https://hackernoon.com/api-consumer-contract-tests-and-test-doubles-with-karate-72c30ea25c18) here ! A truly self-contained demo.

## Limitations
As of now the Gatling concept of ["throttle" and related syntax](https://gatling.io/docs/2.3/general/simulation_setup/#simulation-setup-throttling) is not supported. Most teams don't need this, but you can declare "pause" times in Karate, see [`pauseFor()`](#pausefor).

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

  val create = scenario("create").exec(karateFeature("classpath:mock/cats-create.feature"))
  val delete = scenario("delete").exec(karateFeature("classpath:mock/cats-delete.feature@name=delete"))

  setUp(
    create.inject(rampUsers(10) over (5 seconds)).protocols(protocol),
    delete.inject(rampUsers(5) over (5 seconds)).protocols(protocol)
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

You can also set pause times (in milliseconds) per URL pattern *and* HTTP method (`get`, `post` etc.) if needed (see [limitations](#limitations)). 

We recommend you set that to `0` for everything unless you really need to artifically limit the requests per second. Note how you can use `Nil` to default to `0` for all HTTP methods for a URL pattern. Make sure you wire up the `protocol` in the Gatling `setUp`. If you use a [`nameResolver`](#nameresolver), even those names can be used in the `pauseFor` lookup (instead of a URL pattern).

### `karateFeature()`
This declares a whole Karate feature as a "flow". Note how you can have concurrent flows in the same Gatling simulation.

#### Tag Selector
In the code above, note how a single `Scenario` (or multiple) can be "chosen" by appending the [tag](https://github.com/intuit/karate#cucumber-tags) name to the `Feature` path. This allows you to re-use only selected tests out of your existing functional or regression test suites for composing a performance test-suite.

If multiple `Scenario`-s have the tag on them, they will all be executed. The order of execution will be the order in which they appear in the `Feature`.

> The tag does not need to be in the `@key=value` form and you can use the plain "`@foo`" form if you want to. But using the pattern `@name=someName` is arguably more readable when it comes to giving multiple `Scenario`-s meaningful names.

### Gatling Session
The Gatling session attributes and `userId` would be available in a Karate variable under the name-space `__gatling`. So you can refer to the user-id for the thread as follows:

```cucumber
* print 'gatling userId:', __gatling.userId
```

This is useful as an alternative to using a random UUID where you want to create unique users, and makes it easy to co-relate values to your test-run in some situations.

### Feeders
Because of the above mechanism which allows Karate to "see" Gatling session data, you can use [feeders](https://gatling.io/docs/current/session/feeder/) effectively. For example:

```scala
import scala.util.Random
val feeder = Iterator.continually(Map("email" -> (Random.alphanumeric.take(20).mkString + "@foo.com")))

feed(feeder).exec(karateFeature("classpath:mock/cats-create.feature"))
```

And now in the feature file you can do this:

```cucumber
* print __gatling.email
```

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

The `karate` object happens to implement the `PerfContext` interface and keeps your code simple. Note how the `myRpc` method has been implemented to accept a `Map` (auto-converted from JSON) and the `PerfContext` as arguments. 

Like the built-in HTTP support, any test failures are automatically linked to the previous "perf event" captured.
