package com.intuit.karate.gatling

import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext

class KarateActionBuilder(name: String, tags: Seq[String]) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action = {
    val karateComponents = ctx.protocolComponentsRegistry.components(KarateProtocol.KarateProtocolKey)
    new KarateAction(name, tags, karateComponents.protocol, karateComponents.system, ctx.coreComponents.statsEngine, ctx.coreComponents.clock, next)
  }
}
