package com.intuit.karate.gatling

import akka.actor.ActorSystem
import com.intuit.karate.http.HttpUtils
import io.gatling.core.{CoreComponents, protocol}
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.Session

class KarateProtocol(uriPatterns: Seq[String]) extends Protocol {
  def pathMatches(uri: String): Option[String] = {
    uriPatterns.find(HttpUtils.parseUriPattern(_, uri) != null)
  }
}

object KarateProtocol {
  val KarateProtocolKey = new ProtocolKey {
    type Protocol = KarateProtocol
    type Components = KarateComponents
    override def defaultProtocolValue(configuration: GatlingConfiguration) = new KarateProtocol(Nil)
    override def newComponents(system: ActorSystem, coreComponents: CoreComponents)= karateProtocol => KarateComponents(karateProtocol)
    override def protocolClass= classOf[KarateProtocol].asInstanceOf[Class[io.gatling.core.protocol.Protocol]]
  }
}

case class KarateComponents(protocol: KarateProtocol) extends ProtocolComponents {
  def onStart: Option[Session => Session] = None
  def onExit: Option[Session => Unit] = None
}
