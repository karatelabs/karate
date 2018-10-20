package com.intuit.karate.gatling

import java.util.Collections
import java.util.function.Consumer

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.intuit.karate.Runner
import com.intuit.karate.core._
import com.intuit.karate.http.HttpRequestBuilder
import io.gatling.commons.stats.{KO, OK}
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class KarateActor extends Actor {
  override def receive: Receive = {
    case m: Runnable => {
      m.run()
      context.stop(self)
    }
    case m: FiniteDuration => {
      val waiter = sender()
      val task: Runnable = () => waiter ! true
      context.system.scheduler.scheduleOnce(m, self, task)
    }
  }
}

class KarateAction(val name: String, val protocol: KarateProtocol, val system: ActorSystem, val statsEngine: StatsEngine, val next: Action) extends ExitableAction {

  def getActor(): ActorRef = {
    val actorName = "karate-" + protocol.actorCount.incrementAndGet()
    system.actorOf(Props[KarateActor], actorName)
  }

  override def execute(session: Session) = {

    val executionHook = new ExecutionHook {

      override def beforeScenario(scenario: Scenario, ctx: ScenarioContext): Boolean = true

      override def getPerfEventName(req: HttpRequestBuilder, ctx: ScenarioContext): String = {
        val customName = protocol.nameResolver.apply(req, ctx)
        val finalName = if (customName != null) customName else protocol.defaultNameResolver.apply(req, ctx)
        val pauseTime = protocol.pauseFor(finalName, req.getMethod)
        if (pauseTime > 0) {
          val duration = Duration(pauseTime, MILLISECONDS)
          implicit val timeout = Timeout(Duration(pauseTime + 5000, MILLISECONDS))
          val future = getActor() ? duration
          Await.result(future, Duration.Inf)
        }
        return if (customName != null) customName else req.getMethod + " " + finalName
      }

      override def reportPerfEvent(event: PerfEvent): Unit = {
        val okOrNot = if (event.isFailed) KO else OK
        val timings = ResponseTimings(event.getStartTime, event.getEndTime);
        val message = if (event.getMessage == null) None else Option(event.getMessage)
        statsEngine.logResponse(session, event.getName, timings, okOrNot, Option(event.getStatusCode + ""), message)
      }

    }

    val asyncSystem: Consumer[Runnable] = r => getActor() ! r
    val asyncNext: Runnable = () => next ! session
    val attribs: Object = (session.attributes + ("userId" -> session.userId)).asInstanceOf[Map[String, AnyRef]].asJava
    val arg = Collections.singletonMap("__gatling", attribs);
    Runner.callAsync(name, arg, executionHook, asyncSystem, asyncNext)

  }

}

