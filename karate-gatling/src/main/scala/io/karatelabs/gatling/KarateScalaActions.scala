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
    protocol => new KarateComponents(protocol)
  }
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
    featurePaths: Seq[String],
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
      featurePaths.asJava,
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
    featurePaths: java.util.List[String],
    tags: java.util.List[String],
    protocol: KarateProtocol,
    override val statsEngine: StatsEngine,
    override val clock: Clock,
    override val next: Action,
    silent: Boolean
) extends ExitableAction with NameGen {

  override val name: String = genName("karateFeature")

  // Create Java executor with all the business logic
  private val executor = new KarateExecutor(featurePaths, tags, protocol, silent)

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
      if (!k.startsWith("gatling.") && k != KarateProtocol.KARATE_KEY) {
        gatlingVars.put(k, v.asInstanceOf[Object])
      }
    }

    // Get previous Karate variables
    val karateVars: java.util.Map[String, Object] = session.attributes.get(KarateProtocol.KARATE_KEY) match {
      case Some(m: Map[_, _]) => new java.util.HashMap[String, Object](m.asInstanceOf[Map[String, Object]].asJava)
      case _ => new java.util.HashMap[String, Object]()
    }

    // Execute using Java executor
    val result = executor.execute(gatlingVars, karateVars, statsReporter, session.scenario, session.groups)

    // Update session - use mutable map view to avoid hashCode computation on JS objects
    // (JS objects may have circular references that would cause StackOverflowError)
    val updatedKarate: scala.collection.mutable.Map[String, Any] = result.karateVars.asScala
    val updatedSession = if (result.success) {
      session.set(KarateProtocol.KARATE_KEY, updatedKarate)
    } else {
      session.markAsFailed.set(KarateProtocol.KARATE_KEY, updatedKarate)
    }

    next ! updatedSession
  }
}
