package com.intuit.karate.gatling

import io.gatling.core.session.Session

object PreDef {
  def karateProtocol(uriPatterns: (String, Seq[MethodPause])*) = new KarateProtocol(uriPatterns.toMap)
  def karateFeature(name: String, tags: String *) = new KarateActionBuilder(name, tags)
  def karateAdd(key: String, valueSupplier: Session => AnyRef ) = new KarateContextAddBuilder(key, valueSupplier)
  def pauseFor(list: (String, Int)*) = list.map(mp => MethodPause(mp._1, mp._2))
}
