package com.intuit.karate.gatling

import java.io.File

import com.intuit.karate.CallContext
import io.gatling.core.action.Action
import io.gatling.core.session.Session

case class KarateMessage(val file: File, val cc: CallContext, val session: Session, val next: Action) {

}
