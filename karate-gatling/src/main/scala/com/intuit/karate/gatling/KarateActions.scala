package com.intuit.karate.gatling

import java.util.function.Consumer

import akka.actor.ActorSystem
import com.intuit.karate.core._
import com.intuit.karate.http.HttpRequest
import com.intuit.karate.{PerfHook, Runner}
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration, MILLISECONDS}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

class KarateFeatureAction(val name: String, val tags: Seq[String], val protocol: KarateProtocol, val system: ActorSystem,
                          val statsEngine: StatsEngine, val clock: Clock, val next: Action) extends ExitableAction {

  override def execute(session: Session) = {

    implicit val executor: ExecutionContextExecutor = system.dispatcher

    def pauseInternal(time: Int) = {
      val duration = Duration(time, MILLISECONDS)
      try {
        Await.result(Future.never, duration)
      } catch {
        // we do all this to achieve a non-blocking "pause"
        // and the timeout exception will ALWAYS be thrown
        case e: Throwable => // do nothing
      }
    }

    val pauseFunction: Consumer[java.lang.Number] = t => pauseInternal(t.intValue())

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
        val vars: java.util.Map[String, Object] = fr.getVariables
        val attributes: Map[String, AnyRef] = if (vars == null) Map.empty else {
          vars.remove(KarateProtocol.GATLING_KEY)
          vars.asScala.toMap
        }
        if (fr.isEmpty || fr.isFailed) {
          next ! session.markAsFailed.set(KarateProtocol.KARATE_KEY, attributes).setAll(attributes)
        } else {
          next ! session.set(KarateProtocol.KARATE_KEY, attributes).setAll(attributes)
        }
      }

      override def pause(millis: java.lang.Number): Unit = pauseFunction.accept(millis)

    }

    val gatlingSessionMap: java.util.Map[String, Any] = new java.util.HashMap(session.attributes.asInstanceOf[Map[String, Any]].asJava)
    val callArg: java.util.Map[String, Any] = {
      if (gatlingSessionMap.containsKey(KarateProtocol.KARATE_KEY)) {
        val incomingData = gatlingSessionMap.remove(KarateProtocol.KARATE_KEY).asInstanceOf[Map[String, Any]].asJava
        new java.util.HashMap[String, Any](incomingData)
      } else {
        new java.util.HashMap[String, Any](1)
      }
    }
    gatlingSessionMap.put("userId", session.userId)
    gatlingSessionMap.put("pause", pauseFunction)
    callArg.put(KarateProtocol.GATLING_KEY, gatlingSessionMap)

    val runner = protocol.runner.copy()
    runner.callSingleCache(protocol.callSingleCache)
    runner.callOnceCache(protocol.callOnceCache)
    runner.tags(tags.asJava)

    Runner.callAsync(protocol.runner, name, callArg, perfHook)

  }

}

class KarateFeatureActionBuilder(name: String, tags: Seq[String]) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    val karateComponents = ctx.protocolComponentsRegistry.components(KarateProtocol.KarateProtocolKey)
    new KarateFeatureAction(name, tags, karateComponents.protocol, karateComponents.system, ctx.coreComponents.statsEngine, ctx.coreComponents.clock, next)
  }

}

class KarateSetAction(key: String, valueSupplier: Session => AnyRef,
                      val statsEngine: StatsEngine, val clock: Clock, val next: Action) extends ExitableAction with NameGen {

  override val name: String = genName("karateSet")

  override def execute(session: Session): Unit = {
    val karateContext = session(KarateProtocol.KARATE_KEY).asOption[Map[String, AnyRef]].getOrElse(Map.empty)
    next ! session.set(KarateProtocol.KARATE_KEY, karateContext + (key -> valueSupplier(session)))
  }

}

class KarateSetActionBuilder(key: String, valueSupplier: Session => AnyRef) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    new KarateSetAction(key, valueSupplier, ctx.coreComponents.statsEngine, ctx.coreComponents.clock, next)
  }

}
