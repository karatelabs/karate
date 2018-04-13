package com.intuit.karate.gatling

import akka.actor.Actor
import com.intuit.karate.cucumber.CucumberRunner

class KarateActor extends Actor {
  override def receive: Receive = {
    case m: KarateMessage =>
      CucumberRunner.runFeature(m.file, m.cc)
      m.next ! m.session
  }
}
