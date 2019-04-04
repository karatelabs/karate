package com.intuit.karate.gatling

import java.util.Collections
import java.util.function.Consumer

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.intuit.karate.Runner
import com.intuit.karate.core._
import com.intuit.karate.http.HttpRequestBuilder
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

import scala.collection.JavaConverters._

class KarateActor extends Actor {
  override def receive: Receive = {
    case m: Runnable => {
      m.run()
      context.stop(self)
    }
  }
}

class KarateAction(val name: String, val protocol: KarateProtocol, val system: ActorSystem, val statsEngine: StatsEngine, val clock: Clock, val next: Action) extends ExitableAction {

  def getActor(): ActorRef = {
    val actorName = "karate-" + protocol.actorCount.incrementAndGet()
    system.actorOf(Props[KarateActor], actorName)
  }

  override def execute(session: Session) = {

    val executionHook = new ExecutionHook {

      override def beforeScenario(scenario: Scenario, ctx: ScenarioContext): Boolean = true

      override def afterScenario(scenarioResult: ScenarioResult, scenarioContext: ScenarioContext) = {}

      override def getPerfEventName(req: HttpRequestBuilder, ctx: ScenarioContext): String = {
        val customName = protocol.nameResolver.apply(req, ctx)
        val finalName = if (customName != null) customName else protocol.defaultNameResolver.apply(req, ctx)
        val pauseTime = protocol.pauseFor(finalName, req.getMethod)
        if (pauseTime > 0) {
          // this is probably bad scala / akka practice
          // TODO proper throttling strategy
          Thread.sleep(pauseTime)
        }
        return if (customName != null) customName else req.getMethod + " " + finalName
      }

      override def reportPerfEvent(event: PerfEvent): Unit = {
        val okOrNot = if (event.isFailed) KO else OK
        val message = if (event.getMessage == null) None else Option(event.getMessage)
        statsEngine.logResponse(session, event.getName, event.getStartTime, event.getEndTime, okOrNot, Option(event.getStatusCode + ""), message)
      }

    }

    val asyncSystem: Consumer[Runnable] = r => getActor() ! r
    val asyncNext: Runnable = () => next ! session
    val attribs: Object = (session.attributes + ("userId" -> session.userId)).asInstanceOf[Map[String, AnyRef]].asJava
    val arg = Collections.singletonMap("__gatling", attribs)
    Runner.callAsync(name, arg, executionHook, asyncSystem, asyncNext)

  }

}

