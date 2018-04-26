package com.intuit.karate.gatling

object PreDef {
  def karateProtocol(pauseTime: Int, uriPatterns: String*) = new KarateProtocol(pauseTime, uriPatterns)
  def karateFeature(name: String) = new KarateActionBuilder(name)
}
