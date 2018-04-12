package com.intuit.karate.gatling

import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext

class KarateActionBuilder(requestName: String) extends ActionBuilder{

  override def build(ctx: ScenarioContext, next: Action): Action = {
    new KarateAction(requestName, new KarateProtocol, ctx.coreComponents.statsEngine, next)
  }

}
