package com.intuit.karate.gatling

object PreDef {
  def karateProtocol(uriPatterns: String*) = new KarateProtocol(uriPatterns)
  def karateFeature(name: String) = new KarateActionBuilder(name)
}
