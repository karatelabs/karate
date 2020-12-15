package com.intuit.karate.gatling

import akka.actor.ActorSystem
import com.intuit.karate.http.{HttpUtils, HttpRequest}
import com.intuit.karate.core.ScenarioRuntime
import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.Session

case class MethodPause(val method: String, pause: Int)

class KarateProtocol(val uriPatterns: Map[String, Seq[MethodPause]]) extends Protocol {
  def pathMatches(uri: String): Option[String] = uriPatterns.keys.find(HttpUtils.parseUriPattern(_, uri) != null)
  def pauseFor(requestName: String, method: String) = {
    val methodPause = uriPatterns.getOrElse(requestName, Nil).find(mp => method.equalsIgnoreCase(mp.method))
    if (methodPause.isDefined) methodPause.get.pause else 0
  }
  val defaultNameResolver = (req: HttpRequest, ctx: ScenarioRuntime) => {
    val pathPair = HttpUtils.parseUriIntoUrlBaseAndPath(req.getUrl)
    val matchedUri = pathMatches(pathPair.right)
    if (matchedUri.isDefined) matchedUri.get else pathPair.right
  }
  var nameResolver: (HttpRequest, ScenarioRuntime) => String = (req, ctx) => null
}

object KarateProtocol {
  val KarateProtocolKey = new ProtocolKey[KarateProtocol, KarateComponents] {
    override def defaultProtocolValue(configuration: GatlingConfiguration) = new KarateProtocol(Map.empty)
    override def newComponents(coreComponents: CoreComponents)=
      karateProtocol => KarateComponents(karateProtocol, coreComponents.actorSystem)
    override def protocolClass= classOf[KarateProtocol].asInstanceOf[Class[io.gatling.core.protocol.Protocol]]
  }
}

case class KarateComponents(val protocol: KarateProtocol, val system: ActorSystem) extends ProtocolComponents {
  override def onStart: Session => Session = Session.Identity
  override def onExit: Session => Unit = ProtocolComponents.NoopOnExit
}
