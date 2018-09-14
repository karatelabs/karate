package com.intuit.karate.gatling

import java.io.File
import java.util.function.Consumer

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.intuit.karate.{CallContext, ScenarioContext, ScriptValueMap}
import com.intuit.karate.core._
import com.intuit.karate.cucumber.CucumberRunner
import com.intuit.karate.http.{HttpRequest, HttpRequestBuilder, HttpUtils}
import io.gatling.commons.stats.{KO, OK}
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings

class KarateActor extends Actor {
  override def receive: Receive = {
    case m: Runnable => {
      m.run()
      context.stop(self)
    }
  }
}

class KarateAction(val name: String, val protocol: KarateProtocol, val system: ActorSystem, val statsEngine: StatsEngine, val next: Action) extends ExitableAction {

  def getActor(): ActorRef = {
    val actorName = "karate-" + protocol.actorCount.incrementAndGet()
    system.actorOf(Props[KarateActor], actorName)
  }

  override def execute(session: Session) = {

    def logRequestStats(requestName: String, request: HttpRequest, timings: ResponseTimings, pass: Boolean, statusCode: Int, message: Option[String]) = {
      val key = request.getMethod + " " + requestName
      val okOrNot = if (pass) OK else KO
      statsEngine.logResponse(session, key, timings, okOrNot, Option(statusCode + ""), message)
    }

    val executionHook = new ExecutionHook {

      var prevRequest: Option[HttpRequest] = None
      var startTime: Long = 0
      var responseTime: Long = 0
      var responseStatus: Int = 0
      var prevRequestName: String = null

      def logPrevRequestIfDefined(ctx: ScenarioContext, pass: Boolean, message: Option[String]) = {
        if (prevRequest.isDefined) {
          val responseTimings = ResponseTimings(startTime, startTime + responseTime);
          logRequestStats(prevRequestName, prevRequest.get, responseTimings, pass, responseStatus, message)
          prevRequest = None
        }
      }

      def handleResultIfFail(feature: String, result: StepResult, step: Step, ctx: ScenarioContext) = {
        if (result.getResult.isFailed) { // if a step failed, assume that the last http request is a fail
          val message = feature + ":" + step.getLine + " " + result.getStep.getText
          logPrevRequestIfDefined(ctx, false, Option(message))
        }
      }

      override def beforeStep(step: Step, ctx: ScenarioContext): Unit = Unit

      override def beforeHttpRequest(req: HttpRequestBuilder, ctx: ScenarioContext): Unit = {
        logPrevRequestIfDefined(ctx, true, None)
        prevRequestName = protocol.resolveName(req, ctx)
        val pauseTime = protocol.pauseFor(prevRequestName, req.getMethod)
        if (pauseTime > 0) {
          Thread.sleep(pauseTime) // TODO use actors here as well
        }
      }

      override def afterStep(result: StepResult, ctx: ScenarioContext): Unit = {
        val stepText = result.getStep.getText;
        val isHttpMethod = stepText.startsWith("method")
        if (isHttpMethod) {
          prevRequest = Option(ctx.getPrevRequest)
          startTime = ctx.getVars.get(ScriptValueMap.VAR_REQUEST_TIME_STAMP).getValue(classOf[Long])
          responseTime = ctx.getVars.get(ScriptValueMap.VAR_RESPONSE_TIME).getValue(classOf[Long])
          responseStatus = ctx.getVars.get(ScriptValueMap.VAR_RESPONSE_STATUS).getValue(classOf[Int])
        }
        val featureName = ctx.getFeatureContext.feature.getRelativePath
        handleResultIfFail(featureName, result, result.getStep, ctx)
      }

      override def afterScenario(scenarioResult: ScenarioResult, ctx: ScenarioContext): Unit = {
        logPrevRequestIfDefined(ctx, true, None)
      }

      override def beforeScenario(scenario: Scenario, ctx: ScenarioContext): Boolean = true

    }

    val asyncSystem: Consumer[Runnable] = r => getActor() ! r
    val asyncNext: Runnable = () => next ! session
    val callContext = new CallContext(false, executionHook)

    CucumberRunner.callAsync(name, callContext, asyncSystem, asyncNext)

  }

}


