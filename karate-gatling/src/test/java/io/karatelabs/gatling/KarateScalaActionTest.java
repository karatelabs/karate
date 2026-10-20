package io.karatelabs.gatling;

import io.gatling.commons.stats.KO$;
import io.gatling.commons.stats.Status;
import io.gatling.commons.util.DefaultClock;
import io.gatling.core.session.Session;
import io.gatling.core.session.Session$;
import io.gatling.core.stats.StatsEngine;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives KarateScalaAction the way Gatling does — {@code action ! session} with sessions bound
 * to a small Netty event-loop group — so the invariant is testable without a simulation: a Karate
 * feature never runs on the virtual user's event-loop thread, and the chain continues on that
 * thread whether the feature passed, failed or never started.
 */
class KarateScalaActionTest {

    // A proxy rather than a class: StatsEngine.stop names a type that is private to Gatling's
    // package, which scalac enforces on the Java test sources it type-checks too.
    private static StatsEngine recordingStatsEngine(Queue<Status> statuses) {
        return (StatsEngine) Proxy.newProxyInstance(StatsEngine.class.getClassLoader(),
                new Class<?>[]{StatsEngine.class}, (proxy, method, args) -> {
                    if (method.getName().equals("logResponse")) {
                        statuses.add((Status) args[5]);
                    }
                    return null;
                });
    }

    private static StatsEngine throwingStatsEngine(Error error) {
        return (StatsEngine) Proxy.newProxyInstance(StatsEngine.class.getClassLoader(),
                new Class<?>[]{StatsEngine.class}, (proxy, method, args) -> {
                    if (method.getName().equals("logResponse")) {
                        throw error;
                    }
                    return null;
                });
    }

    private static KarateScalaAction action(String feature, Queue<Status> statuses, RecordingNext next) {
        return new KarateScalaAction(feature, List.of(), null, recordingStatsEngine(statuses), new DefaultClock(), next, false);
    }

    @Test
    void usersAreNotSerializedOnTheEventLoopThreads() throws Exception {
        MultiThreadIoEventLoopGroup loops = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        try {
            int users = 20;
            RecordingNext next = new RecordingNext(users);
            KarateScalaAction a = action("classpath:features/pause.feature", new ConcurrentLinkedQueue<>(), next);
            long start = System.nanoTime();
            for (int i = 1; i <= users; i++) {
                a.$bang(Session$.MODULE$.apply("pause", i, loops.next()));
            }
            // 20 one-second pauses on 2 event-loop threads: 10 s if they block the loops, ~1 s if not
            assertTrue(next.latch().await(6, TimeUnit.SECONDS),
                    "only " + (users - next.latch().getCount()) + " of " + users + " users came back within 6 s");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsedMs < 4000, users + " users pausing 1 s each took " + elapsedMs + " ms");
            assertEquals(0, next.offLoop().get(), "every continuation must run on its own session's event loop");
            assertEquals(users, next.sessions().size());
            assertTrue(next.sessions().stream().noneMatch(Session::isFailed), "a passing feature must not fail the session");
        } finally {
            loops.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void failedFeatureMarksTheSessionFailedAndContinuesTheChain() throws Exception {
        MultiThreadIoEventLoopGroup loops = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            RecordingNext next = new RecordingNext(1);
            Queue<Status> statuses = new ConcurrentLinkedQueue<>();
            KarateScalaAction a = action("classpath:features/pre-http-fail.feature", statuses, next);
            a.$bang(Session$.MODULE$.apply("fail", 1, loops.next()));
            assertTrue(next.latch().await(10, TimeUnit.SECONDS), "the virtual user was lost");
            assertEquals(0, next.offLoop().get());
            assertTrue(next.sessions().peek().isFailed(), "a failed feature must fail the session");
            assertEquals(List.of(KO$.MODULE$), List.copyOf(statuses), "the failure must reach the stats engine");
        } finally {
            loops.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void crashOutsideTheFeatureStillContinuesTheChainMarkedFailed() throws Exception {
        MultiThreadIoEventLoopGroup loops = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            RecordingNext next = new RecordingNext(1);
            KarateScalaAction a = action("classpath:features/does-not-exist.feature", new ConcurrentLinkedQueue<>(), next);
            a.$bang(Session$.MODULE$.apply("crash", 1, loops.next()));
            assertTrue(next.latch().await(10, TimeUnit.SECONDS), "the virtual user was lost");
            assertEquals(0, next.offLoop().get());
            assertTrue(next.sessions().peek().isFailed(), "a crash must fail the session");
        } finally {
            loops.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * A fatal error is not forwarded like a crash: Gatling's ChainableAction logs it and exits
     * the JVM, and so must the action now that the feature no longer runs under ChainableAction.
     * Escaping the virtual thread instead would leave the user stranded and the simulation hung.
     */
    @Test
    void fatalErrorExitsLikeChainableAction() throws Exception {
        MultiThreadIoEventLoopGroup loops = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        BlockingQueue<Integer> exits = new LinkedBlockingQueue<>();
        IntConsumer exit = KarateScalaAction$.MODULE$.exit();
        KarateScalaAction$.MODULE$.exit_$eq(exits::add);
        try {
            RecordingNext next = new RecordingNext(1);
            KarateScalaAction a = new KarateScalaAction("classpath:features/pre-http-fail.feature", List.of(), null,
                    throwingStatsEngine(new LinkageError("injected")), new DefaultClock(), next, false);
            a.$bang(Session$.MODULE$.apply("fatal", 1, loops.next()));
            assertEquals(1, exits.poll(10, TimeUnit.SECONDS), "a fatal error must exit with status 1");
            assertTrue(next.sessions().isEmpty(), "a fatal error must not continue the chain");
        } finally {
            KarateScalaAction$.MODULE$.exit_$eq(exit);
            loops.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
    }

}
