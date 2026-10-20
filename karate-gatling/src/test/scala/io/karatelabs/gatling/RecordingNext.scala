package io.karatelabs.gatling

import io.gatling.core.action.Action
import io.gatling.core.session.Session

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}

/**
 * The `next` of an action under test. Scala because Action mixes in StrictLogging, whose
 * trait-field setter a Java class cannot implement.
 */
final class RecordingNext(expected: Int) extends Action {
  override val name: String = "recording"
  val latch = new CountDownLatch(expected)
  val sessions = new ConcurrentLinkedQueue[Session]()
  /** Continuations that did not arrive on their own session's event loop. */
  val offLoop = new AtomicInteger()

  override def execute(session: Session): Unit = {
    if (!session.eventLoop.inEventLoop) offLoop.incrementAndGet()
    sessions.add(session)
    latch.countDown()
  }
}
