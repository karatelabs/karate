package com.intuit.karate.gatling

import java.util.function.Consumer

import akka.actor.ActorSystem
import com.intuit.karate.core._
import com.intuit.karate.http.HttpRequest
import com.intuit.karate.{PerfHook, Runner}
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

import java.util
import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration, MILLISECONDS}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object KarateAction {
  val GLOBAL_CACHE = new java.util.HashMap[String, AnyRef]
}

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

    implicit val executor: ExecutionContextExecutor = system.dispatcher

    val perfHook = new PerfHook {

      override def getPerfEventName(req: HttpRequest, sr: ScenarioRuntime): String = {
        val customName = protocol.nameResolver.apply(req, sr)
        val finalName = if (customName != null) customName else protocol.defaultNameResolver.apply(req, sr)
        val pauseTime = protocol.pauseFor(finalName, req.getMethod)
        if (pauseTime > 0) pause(pauseTime)
        return if (customName != null) customName else req.getMethod + " " + finalName
      }

      override def reportPerfEvent(event: PerfEvent): Unit = {
        val okOrNot = if (event.isFailed) KO else OK
        val message = if (event.getMessage == null) None else Option(event.getMessage)
        statsEngine.logResponse(session.scenario, session.groups, event.getName, event.getStartTime, event.getEndTime, okOrNot, Option(event.getStatusCode.toString), message)
      }

      override def submit(r: Runnable): Unit = Future {
        r.run()
      }

      override def afterFeature(fr: FeatureResult): Unit = {
        val vars:java.util.Map[String, Object] = fr.getVariables
        val attributes = if (vars == null) Map.empty else {
          // Removing the gatling specific attributes.
          vars.remove("__gatling")
          vars.asScala
        }
        // Using __karate as karate specific attributes.
        if (fr.isEmpty || fr.isFailed) {
          next ! session.markAsFailed.set("__karate", attributes).setAll(attributes)
        } else {
          next ! session.set("__karate", attributes).setAll(attributes)
        }
      }

      override def getGlobalCache = KarateAction.GLOBAL_CACHE

    }

    val pauseFunction: Consumer[java.lang.Number] = t => pause(t.intValue())

    val attribs: java.util.Map[String, AnyRef] = (session.attributes + ("userId" -> session.userId) + ("pause" -> pauseFunction))
      .asInstanceOf[Map[String, AnyRef]].asJava

    val arg:java.util.Map[String, Object] = new util.HashMap[String, Object](attribs)

    if (arg.containsKey("__karate")) {
      // unwrapping karate attributes and removing it from attribs.
      val karateAttribs = arg.remove("__karate").asInstanceOf[scala.collection.mutable.Map[String, AnyRef]].asJava
      arg.putAll(karateAttribs)
    }

    arg.put("__gatling", attribs)

    Runner.callAsync(name, tags.asJava, arg, perfHook)

  }

}
