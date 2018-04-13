package com.intuit.karate.gatling

object PreDef {
  val karateProtocol = new KarateProtocol
  def karateFeature(name: String) = new KarateActionBuilder(name)
}
