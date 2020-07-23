package com.intuit.karate.gatling

import java.util.Collections
import java.util.function.Consumer

import akka.actor.ActorSystem
import com.intuit.karate.{Results, Runner}
import com.intuit.karate.core._
import com.intuit.karate.http.HttpRequestBuilder
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.{Duration, MILLISECONDS}

class KarateAction(val name: String, val tags: Seq[String], val protocol: KarateProtocol, val system: ActorSystem,
                   val statsEngine: StatsEngine, val clock: Clock, val next: Action) extends ExitableAction {

  def pause(time: Int) = {
    val duration = Duration(time, MILLISECONDS)
    try {
      Await.result(Future.never, duration)
    } catch {
      // we do all this to achieve a non-blocking "pause"
      // and the timeout exception will ALWAYS be thrown
      case e: Throwable => // do nothing
    }
  }

  override def execute(session: Session) = {

    val executionHook = new ExecutionHook {

      override def beforeScenario(scenario: Scenario, ctx: ScenarioContext) = true

      override def afterScenario(result: ScenarioResult, scenarioContext: ScenarioContext) = {}

      override def beforeFeature(feature: Feature, ctx: ExecutionContext) = true

      override def afterFeature(result: FeatureResult, ctx: ExecutionContext) = {}

      override def beforeAll(results: Results) = {}

      override def afterAll(results: Results) = {}

      override def beforeStep(step: Step, ctx: ScenarioContext) = true

      override def afterStep(result: StepResult, ctx: ScenarioContext) = {}

      override def getPerfEventName(req: HttpRequestBuilder, ctx: ScenarioContext): String = {
        val customName = protocol.nameResolver.apply(req, ctx)
        val finalName = if (customName != null) customName else protocol.defaultNameResolver.apply(req, ctx)
        val pauseTime = protocol.pauseFor(finalName, req.getMethod)
        if (pauseTime > 0) pause(pauseTime)
        return if (customName != null) customName else req.getMethod + " " + finalName
      }

      override def reportPerfEvent(event: PerfEvent): Unit = {
        val okOrNot = if (event.isFailed) KO else OK
        val message = if (event.getMessage == null) None else Option(event.getMessage)
        statsEngine.logResponse(session, event.getName, event.getStartTime, event.getEndTime, okOrNot, Option(event.getStatusCode + ""), message)
      }

    }

    implicit val executor: ExecutionContextExecutor = system.dispatcher
    val asyncSystem: Consumer[Runnable] = r => Future { r.run() }
    val pauseFunction: Consumer[java.lang.Number] = t => pause(t.intValue())
    val asyncNext: Runnable = () => next ! session
    val attribs: Object = (session.attributes + ("userId" -> session.userId) + ("pause" -> pauseFunction))
      .asInstanceOf[Map[String, AnyRef]].asJava
    val arg = Collections.singletonMap("__gatling", attribs)
    Runner.callAsync(name, tags.asJava, arg, executionHook, asyncSystem, asyncNext)

  }

}

