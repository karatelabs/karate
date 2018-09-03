# Karate Gatling
## API Perf-Testing Made `Simple.`
### Capabilities
* Re-use Karate tests as performance tests executed by [Gatling](https://gatling.io)
* Use Gatling (and Scala) only for defining the load-model, everything else can be in Karate
* Karate assertion failures appear in Gatling report, along with the line-numbers that failed
* Leverage Karate's powerful assertion capabilities to check that server responses are as expected under load - which is much harder to do in Gatling and other performance testing tools
* API invocation sequences that represent end-user workflows are much easier to express in Karate

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

This demo project happens to depend on [`karate-netty`](../karate-netty) which already depends on `karate-apache`. It is worth calling out that we are perf-testing [Karate test-doubles](https://hackernoon.com/api-consumer-contract-tests-and-test-doubles-with-karate-72c30ea25c18) here ! A truly self-contained demo.

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

#### `pauseFor()`

You can also set pause times (in milliseconds) per URL pattern *and* HTTP method (`get`, `post` etc.) if needed (see [limitations](#limitations)). 

We recommend you set that to `0` for everything unless you really need to artifically limit the requests per second. Note how you can use `Nil` to default to `0` for all HTTP methods for a URL pattern. Make sure you wire up the `protocol` in the Gatling `setUp`.

### `karateFeature()`
This declares a whole Karate feature as a "flow". Note how you can have concurrent flows in the same Gatling simulation.

#### Tag Selector
In the code above, note how a single `Scenario` (or multiple) can be "chosen" by appending the [tag](https://github.com/intuit/karate#cucumber-tags) name to the `Feature` path. This allows you to re-use only selected tests out of your existing functional or regression test suites for composing a performance test-suite.

If multiple `Scenario`-s have the tag on them, they will all be executed. The order of execution will be the order in which they appear in the `Feature`.

> The tag does not need to be in the `@key=value` form and you can use the plain "`@foo`" form if you want to. But using the pattern `@name=someName` is arguably more readable when it comes to giving multiple `Scenario`-s meaningful names.

