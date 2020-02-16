package com.intuit.karate.gatling

object PreDef {
  def karateProtocol(uriPatterns: (String, Seq[MethodPause])*) = new KarateProtocol(uriPatterns.toMap)
  def karateFeature(name: String, tags: String *) = new KarateActionBuilder(name, tags)
  def pauseFor(list: (String, Int)*) = list.map(mp => MethodPause(mp._1, mp._2))
}
