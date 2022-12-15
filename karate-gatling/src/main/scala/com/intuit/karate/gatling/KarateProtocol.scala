/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.gatling

import akka.actor.ActorSystem
import com.intuit.karate.Runner
import com.intuit.karate.http.{HttpRequest, HttpUtils}
import com.intuit.karate.core.{ScenarioCall, ScenarioRuntime}
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
  var runner = new Runner.Builder
  val callSingleCache = new java.util.HashMap[String, AnyRef]
  val callOnceCache = new java.util.HashMap[String, ScenarioCall.Result]
}

object KarateProtocol {
  val KARATE_KEY = "__karate"
  val GATLING_KEY = "__gatling"
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
