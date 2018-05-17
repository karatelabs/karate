package com.intuit.karate.gatling

import java.io.File
import java.util.function.Consumer

import akka.actor.{Actor, ActorSystem, Props}
import com.intuit.karate.{CallContext, ScriptContext, ScriptValueMap}
import com.intuit.karate.cucumber._
import com.intuit.karate.http.{HttpRequest, HttpUtils}
import gherkin.formatter.model.Step
import io.gatling.commons.stats.{KO, OK}
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings

class KarateActor extends Actor {
  override def receive: Receive = {
    case m: Runnable => m.run()
  }
}

class KarateAction(val name: String, val protocol: KarateProtocol, val system: ActorSystem, val statsEngine: StatsEngine, val next: Action) extends ExitableAction {

  val actorName = new File(name).getName + "-" + protocol.actorCount.incrementAndGet()
  val actor = system.actorOf(Props[KarateActor], actorName)

  override def execute(session: Session) = {

    def logRequestStats(request: HttpRequest, timings: ResponseTimings, pass: Boolean, statusCode: Int, message: Option[String]) = {
      val pathPair = HttpUtils.parseUriIntoUrlBaseAndPath(request.getUri)
      val matchedUri = protocol.pathMatches(pathPair.right)
      val reportUri = if (matchedUri.isDefined) matchedUri.get else pathPair.right
      val key = request.getMethod + " " + reportUri
      val okOrNot = if (pass) OK else KO
      statsEngine.logResponse(session, key, timings, okOrNot, Option(statusCode + ""), message)
    }

    val stepInterceptor = new StepInterceptor {

      var prevRequest: Option[HttpRequest] = None
      var startTime: Long = 0
      var responseTime: Long = 0
      var responseStatus: Int = 0


      def logPrevRequestIfDefined(ctx: ScriptContext, pass: Boolean, message: Option[String]) = {
        if (prevRequest.isDefined) {
          val responseTimings = ResponseTimings(startTime, startTime + responseTime);
          logRequestStats(prevRequest.get, responseTimings, pass, responseStatus, message)
          prevRequest = None
        }
      }

      def handleResultIfFail(feature: String, result: StepResult, step: Step, ctx: ScriptContext) = {
        if (!result.isPass) { // if a step failed, assume that the last http request is a fail
          val fileName = new File(feature).getName
          val message = fileName + ":" + step.getLine + " " + result.getStep.getName
          logPrevRequestIfDefined(ctx, false, Option(message))
        }
      }

      override def beforeStep(step: StepWrapper, backend: KarateBackend) = {
        val isHttpMethod = step.getStep.getName.startsWith("method")
        if (isHttpMethod) {
          val method = step.getStep.getName.substring(6).trim
          val ctx = backend.getStepDefs.getContext
          logPrevRequestIfDefined(ctx, true, None)
          val request = backend.getStepDefs.getRequest
          val pauseTime = protocol.pauseFor(request.getUrlAndPath, method)
          if (pauseTime > 0) {
            Thread.sleep(pauseTime)
          }
        }
      }

      override def afterStep(result: StepResult, backend: KarateBackend): Unit = {
        val isHttpMethod = result.getStep.getName.startsWith("method")
        val ctx = backend.getStepDefs.getContext
        if (isHttpMethod) {
          prevRequest = Option(ctx.getPrevRequest)
          startTime = ctx.getVars.get(ScriptValueMap.VAR_REQUEST_TIME_STAMP).getValue(classOf[Long])
          responseTime = ctx.getVars.get(ScriptValueMap.VAR_RESPONSE_TIME).getValue(classOf[Long])
          responseStatus = ctx.getVars.get(ScriptValueMap.VAR_RESPONSE_STATUS).getValue(classOf[Int])
        }
        handleResultIfFail(backend.getFeaturePath, result, result.getStep, ctx)
      }

      override def afterScenario(scenario: ScenarioWrapper, backend: KarateBackend): Unit = {
        logPrevRequestIfDefined(backend.getStepDefs.getContext, true, None)
      }

    }

    val asyncSystem: Consumer[Runnable] = r => actor ! r
    val asyncNext: Runnable = () => next ! session
    val callContext = new CallContext(null, 0, null, -1, false, true, null, asyncSystem, asyncNext, stepInterceptor)

    CucumberUtils.callAsync(name, callContext)

  }

}


