package com.intuit.karate.gatling

import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext

class KarateActionBuilder(requestName: String) extends ActionBuilder {
  val pos = requestName.indexOf('@')
  val name = if (pos == -1) requestName else requestName.substring(0, pos)
  val callTag = if (pos == -1) null else requestName.substring(pos)
  override def build(ctx: ScenarioContext, next: Action): Action = {
    val karateComponents = ctx.protocolComponentsRegistry.components(KarateProtocol.KarateProtocolKey)
    new KarateAction(name, callTag, karateComponents.protocol, karateComponents.system, ctx.coreComponents.statsEngine, next)
  }
}
