package com.intuit.karate.gatling

import akka.actor.{ActorSystem, Props}
import com.intuit.karate.cucumber.StepResult
import com.intuit.karate.{CallContext, FileUtils, ScriptValueMap, StepInterceptor}
import gherkin.formatter.model.Step
import io.gatling.commons.stats.OK
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings

class KarateAction(val name: String, protocol: KarateProtocol, val statsEngine: StatsEngine, val next: Action) extends ExitableAction {

  val file = FileUtils.getFeatureFile(name)

  override def execute(session: Session) = {

    val stepInterceptor = new StepInterceptor {
      var start: Long = 0
      override def before(s: String, i: Int, step: Step, scriptValueMap: ScriptValueMap): Unit = {
        if (step.getName.startsWith("method")) {
          start = System.currentTimeMillis
        } else {
          start = 0
        }
      }
      override def after(s: String, i: Int, stepResult: StepResult, scriptValueMap: ScriptValueMap): Unit = {
        if (start != 0) {
          val end = System.currentTimeMillis
          val timings = ResponseTimings(start, end)
          statsEngine.logResponse(session, name, timings, OK, None, None)
        }
      }
    }

    val cc = new CallContext(null, 0, null, -1, false, false, null, stepInterceptor)

    val system = ActorSystem("foo")
    val act = system.actorOf(Props[KarateActor], name = "bar")
    act ! new KarateMessage(file, cc, session, next)

  }

}


