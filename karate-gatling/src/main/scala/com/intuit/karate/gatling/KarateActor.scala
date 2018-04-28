package com.intuit.karate.gatling

import java.io.File

import akka.actor.Actor
import com.intuit.karate.CallContext
import com.intuit.karate.cucumber.CucumberRunner
import io.gatling.core.action.Action
import io.gatling.core.session.Session

case class KarateFeatureMessage(val file: File, val cc: CallContext, val session: Session, val next: Action)

class KarateActor extends Actor {
  override def receive: Receive = {
    case m: KarateFeatureMessage =>
      CucumberRunner.runFeature(m.file, m.cc)
      m.next ! m.session
  }
}
