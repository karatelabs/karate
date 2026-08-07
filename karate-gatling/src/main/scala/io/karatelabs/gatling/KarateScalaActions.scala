/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.gatling

import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.core.CoreComponents
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.actor.ActorSystem
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen

import scala.jdk.CollectionConverters.*

/**
 * Gatling ProtocolKey for registering KarateProtocol with Gatling's protocol registry.
 * This allows the protocol to be retrieved in ActionBuilder.build() via ScenarioContext.
 */
object KarateProtocolKey extends ProtocolKey[KarateProtocol, KarateComponents] {
  override def protocolClass: Class[Protocol] = classOf[KarateProtocol].asInstanceOf[Class[Protocol]]

  override def defaultProtocolValue(configuration: GatlingConfiguration): KarateProtocol = {
    // Return empty protocol if none configured
    new KarateProtocol(java.util.Collections.emptyMap())
  }

  override def newComponents(coreComponents: CoreComponents): KarateProtocol => KarateComponents = {
    protocol =>
      // Anything the protocol owns for the whole simulation is closed here. Gatling has no
      // protocol-level teardown callback -- ProtocolComponents.onExit runs once per virtual user,
      // not once at the end -- and this is the hook Gatling's own HttpEngine uses to dispose
      // itself, so a pooled connection manager is released the same way and at the same time.
      val closeable = protocol.getCloseAtSimulationEnd
      if (closeable != null) {
        KarateTerminationSupport.registerClose(coreComponents.actorSystem, closeable)
      }
      new KarateComponents(protocol)
  }
}

/**
 * The one place that talks to Gatling's termination hook.
 *
 * <p>It exists because `registerOnTermination` takes a BY-NAME argument, and the obvious call --
 * `registerOnTermination(() => closeable.close())` -- compiles, type-checks and does nothing:
 * the lambda becomes the captured expression, so termination evaluates it to a function object
 * and never invokes it. The resource is then never closed and nothing says so. Keeping exactly
 * one call site means that trap can be got wrong once, not once per caller.
 */
object KarateTerminationSupport {
  def registerClose(system: ActorSystem, closeable: AutoCloseable): Unit =
    system.registerOnTermination(closeable.close())
}

/**
 * Gatling ProtocolComponents wrapper for KarateProtocol.
 */
class KarateComponents(val protocol: KarateProtocol) extends ProtocolComponents {
  override def onStart: Session => Session = Session.Identity
  override def onExit: Session => Unit = ProtocolComponents.NoopOnExit
}

/**
 * Minimal Scala bridge to Gatling's ActionBuilder.
 * All business logic is in Java (KarateExecutor).
 */
class KarateScalaActionBuilder(
    featurePath: String,
    tags: Seq[String],
    protocolFromBuilder: KarateProtocol,  // May be null if not set on builder
    silent: Boolean
) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    // Get protocol from Gatling's registry (set via .protocols() on setUp)
    // Falls back to the one passed from builder, or empty protocol
    val protocol = if (protocolFromBuilder != null) {
      protocolFromBuilder
    } else {
      ctx.protocolComponentsRegistry.components(KarateProtocolKey).protocol
    }

    new KarateScalaAction(
      featurePath,
      tags.asJava,
      protocol,
      ctx.coreComponents.statsEngine,
      ctx.coreComponents.clock,
      next,
      silent
    )
  }
}

/**
 * Minimal Scala Action that delegates to Java KarateExecutor.
 */
class KarateScalaAction(
    featurePath: String,
    tags: java.util.List[String],
    protocol: KarateProtocol,
    override val statsEngine: StatsEngine,
    override val clock: Clock,
    override val next: Action,
    silent: Boolean
) extends ExitableAction with NameGen {

  override val name: String = genName("karateFeature")

  // Create Java executor with all the business logic
  private val executor = new KarateExecutor(featurePath, tags, protocol, silent)

  // Create stats reporter that bridges to Gatling's StatsEngine
  private val statsReporter: GatlingStatsReporter = (
      scenario: String,
      groups: scala.collection.immutable.List[String],
      requestName: String,
      startTime: Long,
      endTime: Long,
      ok: Boolean,
      statusCode: Int,
      errorMessage: String
  ) => {
    val status = if (ok) OK else KO
    val code = Option(statusCode.toString)
    val msg = Option(errorMessage)
    statsEngine.logResponse(scenario, groups, requestName, startTime, endTime, status, code, msg)
  }

  override def execute(session: Session): Unit = {
    // Extract Gatling session variables
    val gatlingVars = new java.util.HashMap[String, Object]()
    session.attributes.foreach { case (k, v) =>
      // KARATE_KEY and LOG_KEY are bridge state, not feeder variables — a feature must not see
      // its own chained vars, or the retained log text, as a __gatling entry
      if (!k.startsWith("gatling.") && k != KarateProtocol.KARATE_KEY && k != KarateProtocol.LOG_KEY) {
        gatlingVars.put(k, v.asInstanceOf[Object])
      }
    }

    // Get previous Karate variables. The value was stored below as a
    // scala.collection.mutable.Map, so match scala.collection.Map (the common supertype of
    // mutable and immutable) — matching the bare `Map` (= immutable.Map) silently dropped the
    // mutable map and broke feature chaining (__karate.* came back empty). Keep a
    // java.util.Map fallback in case a value is ever stored in that form.
    val karateVars: java.util.Map[String, Object] = session.attributes.get(KarateProtocol.KARATE_KEY) match {
      case Some(m: scala.collection.Map[_, _]) =>
        new java.util.HashMap[String, Object](m.asInstanceOf[scala.collection.Map[String, Object]].asJava)
      case Some(m: java.util.Map[_, _]) =>
        new java.util.HashMap[String, Object](m.asInstanceOf[java.util.Map[String, Object]])
      case _ => new java.util.HashMap[String, Object]()
    }

    // Karate output retained from the features already run by this virtual user. Session-carried,
    // not thread-local: Gatling is free to run a later exec() of the same scenario on another thread.
    val logBuffer: LogReplayer.Buffer = session.attributes.get(KarateProtocol.LOG_KEY) match {
      case Some(b: LogReplayer.Buffer) => b
      case _ => null
    }

    // Execute using Java executor
    val result = executor.execute(gatlingVars, karateVars, logBuffer, statsReporter, session.scenario, session.groups)

    // Update session - use mutable map view to avoid hashCode computation on JS objects
    // (JS objects may have circular references that would cause StackOverflowError)
    val updatedKarate: scala.collection.mutable.Map[String, Any] = result.karateVars.asScala
    val withKarate = if (result.success) {
      session.set(KarateProtocol.KARATE_KEY, updatedKarate)
    } else {
      session.markAsFailed.set(KarateProtocol.KARATE_KEY, updatedKarate)
    }
    val updatedSession = if (result.logBuffer == null) {
      withKarate
    } else {
      withKarate.set(KarateProtocol.LOG_KEY, result.logBuffer)
    }

    next ! updatedSession
  }
}
