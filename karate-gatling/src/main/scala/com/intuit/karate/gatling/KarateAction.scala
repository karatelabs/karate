package com.intuit.karate.gatling

import java.io.File

import akka.actor.{ActorSystem, Props}
import com.intuit.karate.{CallContext, FileUtils, ScriptContext, StepInterceptor}
import com.intuit.karate.cucumber.StepResult
import com.intuit.karate.http.{HttpRequest, HttpUtils}
import gherkin.formatter.model.Step
import io.gatling.commons.stats.{KO, OK}
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings

class KarateAction(val name: String, val protocol: KarateProtocol, val system: ActorSystem, val statsEngine: StatsEngine, val next: Action) extends ExitableAction {

  val file = FileUtils.getFeatureFile(name)

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
      var start: Long = 0

      def logPrevRequestIfDefined(ctx: ScriptContext, pass: Boolean, message: Option[String]) = {
        if (prevRequest.isDefined) {
          val responseTime = ctx.getVars.get("responseTime").getValue(classOf[Long])
          val responseStatus = ctx.getVars.get("responseStatus").getValue(classOf[Int])
          val responseTimings = ResponseTimings(start, start + responseTime);
          logRequestStats(prevRequest.get, responseTimings, pass, responseStatus, message)
          prevRequest = None
        }
      }

      override def beforeStep(feature: String, line: Int, step: Step, ctx: ScriptContext): Unit = {
        if (step.getName.startsWith("method")) {
          logPrevRequestIfDefined(ctx, true, None)
          start = System.currentTimeMillis()
        }
      }

      override def afterStep(feature: String, line: Int, stepResult: StepResult, ctx: ScriptContext): Unit = {
        if (stepResult.getStep.getName.startsWith("method")) {
          prevRequest = Option(ctx.getPrevRequest)
        }
        if (!stepResult.isPass) { // if a step failed, assume that the last http request is a fail
          val fileName = new File(feature).getName
          val message = fileName + ":" + line + " " + stepResult.getStep.getName
          logPrevRequestIfDefined(ctx, false, Option(message))
        }
      }

      override def afterScenario(feature: String, ctx: ScriptContext): Unit = {
        logPrevRequestIfDefined(ctx, true, None)
      }

    }

    val cc = new CallContext(null, 0, null, -1, false, true, null, stepInterceptor)
    val actorName = new File(name).getName + "-" + protocol.actorCount.incrementAndGet()
    val act = system.actorOf(Props[KarateActor], actorName)
    act ! new KarateMessage(file, cc, session, next)

  }

}


