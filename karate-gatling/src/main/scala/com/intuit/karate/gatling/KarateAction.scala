package com.intuit.karate.gatling

import akka.actor.{ActorSystem, Props}
import com.intuit.karate.cucumber.StepResult
import com.intuit.karate._
import com.intuit.karate.http.{HttpRequest, HttpUtils}
import gherkin.formatter.model.Step
import io.gatling.commons.stats.{KO, OK}
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings

class KarateAction(val name: String, protocol: KarateProtocol, val statsEngine: StatsEngine, val next: Action) extends ExitableAction {

  val file = FileUtils.getFeatureFile(name)

  override def execute(session: Session) = {

    def logRequestStats(request: HttpRequest, timings: ResponseTimings, pass: Boolean) = {
      val pathPair = HttpUtils.parseUriIntoUrlBaseAndPath(request.getUri)
      val matchedUri = protocol.pathMatches(pathPair.right)
      val reportUri = if (matchedUri.isDefined) matchedUri.get else pathPair.right
      val key = request.getMethod + " " + reportUri
      val okOrNot = if (pass) OK else KO
      statsEngine.logResponse(session, key, timings, okOrNot, None, None)
    }

    val stepInterceptor = new StepInterceptor {

      var prevRequest: Option[HttpRequest] = None
      var start: Long = 0

      def logPrevRequestIfDefined(ctx: ScriptContext, pass: Boolean) = {
        if (prevRequest.isDefined) {
          val responseTime = ctx.getVars.get("responseTime").getValue(classOf[Long])
          val responseTimings = ResponseTimings(start, start + responseTime);
          logRequestStats(prevRequest.get, responseTimings, pass)
          prevRequest = None
        }
      }

      override def beforeStep(feature: String, line: Int, step: Step, ctx: ScriptContext): Unit = {
        if (step.getName.startsWith("method")) {
          logPrevRequestIfDefined(ctx, true)
          start = System.currentTimeMillis()
        }
      }

      override def afterStep(feature: String, line: Int, stepResult: StepResult, ctx: ScriptContext): Unit = {
        if (stepResult.getStep.getName.startsWith("method")) {
          prevRequest = Option(ctx.getPrevRequest)
        }
        if (!stepResult.isPass) { // if a step failed, assume that the last http request is a fail
          System.out.println("*** fail", stepResult.getStep)
          logPrevRequestIfDefined(ctx, false)
        }
      }

      override def afterScenario(feature: String, ctx: ScriptContext): Unit = {
        logPrevRequestIfDefined(ctx, true)
      }

    }

    val cc = new CallContext(null, 0, null, -1, false, true, null, stepInterceptor)
    val system = ActorSystem("karate-actor-system")
    val act = system.actorOf(Props[KarateActor], name = "karate-actor")
    act ! new KarateMessage(file, cc, session, next)

  }

}


