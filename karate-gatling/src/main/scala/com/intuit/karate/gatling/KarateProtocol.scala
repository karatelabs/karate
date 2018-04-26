package com.intuit.karate.gatling

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import com.intuit.karate.http.HttpUtils
import io.gatling.core.{CoreComponents, protocol}
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.Session

class KarateProtocol(val pauseTime: Int, uriPatterns: Seq[String]) extends Protocol {
  def pathMatches(uri: String): Option[String] = uriPatterns.find(HttpUtils.parseUriPattern(_, uri) != null)
  val actorCount = new AtomicInteger()
}

object KarateProtocol {
  val KarateProtocolKey = new ProtocolKey {
    type Protocol = KarateProtocol
    type Components = KarateComponents
    override def defaultProtocolValue(configuration: GatlingConfiguration) = new KarateProtocol(0, Nil)
    override def newComponents(system: ActorSystem, coreComponents: CoreComponents)=
      karateProtocol => KarateComponents(karateProtocol, system)
    override def protocolClass= classOf[KarateProtocol].asInstanceOf[Class[io.gatling.core.protocol.Protocol]]
  }
}

case class KarateComponents(val protocol: KarateProtocol, val system: ActorSystem) extends ProtocolComponents {
  def onStart: Option[Session => Session] = None
  def onExit: Option[Session => Unit] = None
}
