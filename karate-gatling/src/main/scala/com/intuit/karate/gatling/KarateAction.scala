package com.intuit.karate.gatling

import java.io.File

import akka.actor.{ActorSystem, Props}
import com.intuit.karate.{CallContext, FileUtils, ScriptContext, ScriptValueMap}
import com.intuit.karate.cucumber.{KarateBackend, StepInterceptor, StepResult}
import com.intuit.karate.http.{HttpRequest, HttpUtils}
import gherkin.I18n
import gherkin.formatter.model.Step
import io.gatling.commons.stats.{KO, OK}
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings

//import scala.concurrent.{Await, Future}
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.duration._

class KarateAction(val name: String, val protocol: KarateProtocol, val system: ActorSystem, val statsEngine: StatsEngine, val next: Action) extends ExitableAction {

  val file = FileUtils.getFeatureFile(name)
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

      def logPrevRequestIfDefined(ctx: ScriptContext, pass: Boolean, message: Option[String]) = {
        if (prevRequest.isDefined) {
          val startTime = ctx.getVars.get(ScriptValueMap.VAR_REQUEST_TIME_STAMP).getValue(classOf[Long])
          val responseTime = ctx.getVars.get(ScriptValueMap.VAR_RESPONSE_TIME).getValue(classOf[Long])
          val responseStatus = ctx.getVars.get(ScriptValueMap.VAR_RESPONSE_STATUS).getValue(classOf[Int])
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

      override def proceed(feature: String, step: Step, i18n: I18n, backend: KarateBackend): StepResult = {
        val ctx = backend.getStepDefs.getContext
        val isHttpMethod = step.getName.startsWith("method")
        if (isHttpMethod) {
          logPrevRequestIfDefined(ctx, true, None)
          // val future = Future[StepResult] {
            Thread.sleep(protocol.pauseTime)
            val result = super.proceed(feature, step, i18n, backend)
            prevRequest = Option(ctx.getPrevRequest)
            handleResultIfFail(feature, result, step, ctx)
            return result
          // }
          // Await.result(future, protocol.pauseTime + 250 milliseconds)
        } else {
          val result = super.proceed(feature, step, i18n, backend)
          handleResultIfFail(feature, result, step, ctx)
          return result
        }
      }

      override def afterScenario(feature: String, ctx: ScriptContext): Unit = {
        logPrevRequestIfDefined(ctx, true, None)
      }

    }

    val cc = new CallContext(null, 0, null, -1, false, true, null, stepInterceptor)
    actor ! new KarateFeatureMessage(file, cc, session, next)

  }

}


