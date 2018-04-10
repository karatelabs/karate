package com.intuit.karate.gatling

object PreDef {
  val karateProtocol = new KarateProtocol
  def feature(name: String) = new KarateActionBuilder(name)
}
