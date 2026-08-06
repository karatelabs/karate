/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.profiling;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Finds the knee of {@link LatencyMock} — the concurrency at which it stops being free — using a
 * client cheap enough to be out of the way. This is step B2 of docs/PROFILING.md §10, and it is a
 * precondition for the parity matrix rather than a nice-to-have.
 *
 * <p><b>Why the mock's own numbers are not enough.</b> {@link MockStats} starts its clock at
 * handler entry, so it cannot see the accept queue, the dispatcher thread that owns socket
 * readiness, or the executor handoff. A mock saturated upstream of its own clock reports a flat
 * p99 while clients queue behind it. That failure looks exactly like a clean parity result, which
 * is the most dangerous outcome available. The number that exposes it is measured from out here,
 * <b>per request</b>:</p>
 *
 * <pre>
 *   unowned = client elapsed - the server's own elapsed for THAT SAME request
 * </pre>
 *
 * <p>The pairing is the point, and it is why the mock returns its elapsed time in a response
 * header. An earlier version subtracted aggregates — the client's p50 minus the server's p50 —
 * which has no per-request meaning at all: {@code median(X - Y) != median(X) - median(Y)}, and the
 * request at the client's median is not the request at the server's. It was also read through a
 * histogram bucket <i>bound</i>, so the quantisation was comparable to the signal. Aggregate this
 * per-request figure with the <b>mean</b>, which is exact by linearity of expectation and includes
 * the tail where queueing actually lives.</p>
 *
 * <p>Flat across the ramp means the server is keeping up. Climbing means requests are waiting
 * somewhere neither clock owns, and every client comparison run past that point is measuring the
 * mock.</p>
 *
 * <p><b>Both connection modes matter, and this is not symmetry for its own sake.</b> Karate builds
 * an HTTP client per execution, so the Karate variant presents roughly one TCP connection per
 * iteration where plain Gatling holds keep-alive connections per user. Connection establishment
 * through a single dispatcher thread is this server's softest spot — so the two clients lean
 * hardest on the mock exactly where they differ most, and a knee found only in keep-alive mode
 * would not be the knee that matters.</p>
 *
 * <pre>
 *   java -cp ... io.karatelabs.profiling.MockCalibrator --url http://127.0.0.1:PORT \
 *        --ramp 1,2,4,8,16,32,64 --per-user 200
 * </pre>
 */
public final class MockCalibrator {

    private static final String BODY = "{ \"name\": \"Billie\", \"age\": 5 }";

    private MockCalibrator() {
    }

    public static void main(String[] args) throws Exception {
        String url = null;
        int[] ramp = {1, 2, 4, 8, 16, 32, 64};
        // PER USER, not a total split across them. A fixed total makes every point a different
        // experiment: at 256 users it is 15 iterations each over ~200 ms, so the points that
        // matter most get the least data, the shortest window, and a sample composition skewed
        // toward first-request-on-a-fresh-connection.
        int perUser = 200;
        // Between points, so the previous point's sockets leave TIME_WAIT before the next one
        // starts opening its own. Without it the churn arm spends the ramp walking into the
        // ephemeral-port ceiling, and every later point is measuring that rather than the mock.
        Duration settle = Duration.ofSeconds(5);
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url" -> url = next(args, i++);
                case "--ramp" -> ramp = Arrays.stream(next(args, i++).split(","))
                        .mapToInt(s -> Integer.parseInt(s.trim())).toArray();
                case "--per-user" -> perUser = Integer.parseInt(next(args, i++));
                case "--settle" -> settle = RunShape.parseDuration(next(args, i++));
                default -> throw new IllegalArgumentException("unknown option: " + args[i]);
            }
        }
        if (url == null || perUser < 1 || ramp.length == 0) {
            throw new IllegalArgumentException(
                    "usage: MockCalibrator --url <url> [--ramp 1,2,4] [--per-user N]");
        }
        for (int users : ramp) {
            if (users < 1) {
                throw new IllegalArgumentException("ramp entries must be >= 1");
            }
        }

        // Unmeasured, and not optional: the first requests pay for class loading, JIT and the
        // server's own first-touch costs. Left in, they land in the tail — which is what the knee
        // is read from — and would put the knee wherever the JIT happened to finish. The baseline
        // repeat at the end of each arm is what says whether this was enough.
        System.out.println("[calibrate] warming up");
        run(url, 4, 500, true);
        run(url, 4, 500, false);

        System.out.printf("%n[calibrate] %s, %d iterations per user per point%n", url, perUser);
        System.out.printf("%-10s %-6s %4s %10s %10s %10s %12s %12s %10s%n",
                "mode", "users", "ko", "tps", "p50 ms", "p99 ms", "unowned mean", "unowned p99", "growth");
        for (boolean keepAlive : new boolean[]{true, false}) {
            String mode = keepAlive ? "keepalive" : "close";
            double baseline = Double.NaN;
            // The baseline point is repeated at the END of each arm. If the two disagree, the
            // arm's baseline was cold and every growth figure measured against it is drift, not
            // queueing — which is what a negative growth column was telling us and nobody read.
            int[] points = new int[ramp.length + 1];
            System.arraycopy(ramp, 0, points, 0, ramp.length);
            points[ramp.length] = ramp[0];
            for (int index = 0; index < points.length; index++) {
                int users = points[index];
                Thread.sleep(settle);
                reset(url);                  // this point's numbers, not the ramp's history
                Result r = run(url, users, perUser, keepAlive);
                if (Double.isNaN(baseline)) {
                    baseline = r.residualMeanMs();
                }
                // Mean, not a difference of percentiles: by linearity of expectation the mean of
                // the paired per-request residuals IS the mean residual, exactly. And it includes
                // the tail, which is where queueing lives and where a p50 is blind.
                System.out.printf("%-10s %-6d %4d %10.0f %10.2f %10.2f %12.3f %12.3f %10.3f%s%n",
                        mode, users, r.failed(), r.tps(), r.p50Ms(), r.p99Ms(),
                        r.residualMeanMs(), r.residualP99Ms(), r.residualMeanMs() - baseline,
                        index == ramp.length ? "   <- baseline repeat" : (r.valid() ? "" : "   INVALID"));
            }
        }
        System.out.println("""

                [calibrate] Read it in this order.
                  1. ko must be 0. A point with failures is not a slower point, it is a point
                     with holes: the failed requests leave the sample and take the slow ones with
                     them, so tps collapses while the percentiles stay clean. Fix the cause and
                     re-run; do not read a knee off an INVALID row.
                  2. The baseline repeat must match the first row. If it does not, the arm was
                     still warming up and every growth figure is drift.
                  3. The knee is where 'unowned mean' departs from its baseline - NOT where tps
                     stops rising. In a closed loop tps is capped by users / iteration time, so
                     neither its rise nor its flattening proves anything.
                'unowned' is per-request: what the client waited minus what the mock says it spent
                on that same request. Its floor is connection setup, the response write and the
                client's own cost; growth above that floor is queueing where neither clock sees.
                Closed loop means the knee is OPTIMISTIC - when the mock stalls these clients stop
                offering load. Run the matrix at half the knee, in whichever mode knees first, and
                re-calibrate open-loop before trusting any constantUsersPerSec cell.""");
    }

    private record Result(int attempted, int completed, int failed, double tps,
                          double meanMs, double p50Ms, double p99Ms,
                          double residualMeanMs, double residualP99Ms) {

        /** A point with any failure is not a slower point, it is a point with holes in it. */
        boolean valid() {
            return failed == 0 && completed == attempted;
        }
    }

    /** One request: what the client waited, and what the server said it spent on that same request. */
    private record Sample(long elapsedNanos, long serverNanos) {
    }

    private static Result run(String url, int users, int perUser, boolean keepAlive) throws Exception {
        int attempted = users * perUser;
        List<Sample>[] collected = new List[users];
        int[] failures = new int[users];
        CountDownLatch done = new CountDownLatch(users);
        long start = System.nanoTime();
        for (int u = 0; u < users; u++) {
            int user = u;
            collected[user] = new ArrayList<>(perUser);
            Thread.ofVirtual().start(() -> {
                // Keep-alive: one client for the whole point, pooling its connection — plain
                // Gatling's shape. Churn: a fresh client per iteration, which is KARATE's shape,
                // since it builds an HTTP client per feature execution and its two calls share it.
                //
                // Not a "Connection: close" header, which was the first attempt and does not
                // work: java.net.http keeps pooling, then picks a socket the server has already
                // closed and fails with "header parser received no bytes". Those KOs are a client
                // artifact and would have been read as a server knee.
                HttpClient shared = keepAlive ? newClient() : null;
                try {
                    for (int i = 0; i < perUser; i++) {
                        HttpClient client = keepAlive ? shared : newClient();
                        try {
                            // The transaction the parity matrix runs, not a POST on its own: the
                            // matrix does POST then GET, and an envelope measured on one traffic
                            // shape does not transfer to another - different handler mix,
                            // different requests per connection, different connection rate.
                            long id = post(client, url, collected[user], failures, user);
                            if (id < 0 || !get(client, url, id, collected[user], failures, user)) {
                                return;
                            }
                        } finally {
                            if (!keepAlive) {
                                client.close();
                            }
                        }
                    }
                } finally {
                    if (shared != null) {
                        shared.close();
                    }
                    done.countDown();
                }
            });
        }
        done.await();
        double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;

        List<Sample> all = new ArrayList<>(attempted * 2);
        for (List<Sample> perUserSamples : collected) {
            all.addAll(perUserSamples);
        }
        int failed = 0;
        for (int f : failures) {
            failed += f;
        }
        List<Long> elapsed = new ArrayList<>(all.size());
        List<Long> residual = new ArrayList<>(all.size());
        double elapsedTotal = 0;
        double residualTotal = 0;
        for (Sample sample : all) {
            elapsed.add(sample.elapsedNanos());
            elapsedTotal += sample.elapsedNanos();
            // Paired, per request, same request on both sides. This is the whole point: an
            // aggregate difference of two percentiles compares two DIFFERENT requests and has no
            // per-request meaning, quite apart from being read through a bucket bound.
            long unowned = sample.elapsedNanos() - sample.serverNanos();
            residual.add(unowned);
            residualTotal += unowned;
        }
        elapsed.sort(null);
        residual.sort(null);
        int completed = all.size() / 2;    // a completed iteration is a POST and its GET
        return new Result(attempted, completed, failed,
                completed / elapsedSeconds,
                elapsedTotal / Math.max(1, all.size()) / 1_000_000.0,
                percentileMs(elapsed, 0.50), percentileMs(elapsed, 0.99),
                residualTotal / Math.max(1, all.size()) / 1_000_000.0,
                percentileMs(residual, 0.99));
    }

    /** @return the created id, or -1 if this user is done because something failed */
    private static HttpClient newClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private static long post(HttpClient client, String url,
                             List<Sample> samples, int[] failures, int user) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url + "/cats"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(BODY));
            long t0 = System.nanoTime();
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long elapsed = System.nanoTime() - t0;
            if (response.statusCode() != 201) {
                return fail(failures, user, "POST returned " + response.statusCode());
            }
            samples.add(new Sample(elapsed, serverNanos(response)));
            return idOf(response.body());
        } catch (Exception e) {
            return fail(failures, user, "POST failed: " + e);
        }
    }

    private static boolean get(HttpClient client, String url, long id,
                               List<Sample> samples, int[] failures, int user) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url + "/cats/" + id));
            long t0 = System.nanoTime();
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long elapsed = System.nanoTime() - t0;
            if (response.statusCode() != 200) {
                return fail(failures, user, "GET returned " + response.statusCode()) >= 0;
            }
            // The mock serving the WRONG cat would otherwise pass here exactly as it would pass
            // the plain Gatling sim, which checks only the name - and every cat is named Billie.
            if (idOf(response.body()) != id) {
                return fail(failures, user, "GET returned the wrong cat") >= 0;
            }
            samples.add(new Sample(elapsed, serverNanos(response)));
            return true;
        } catch (Exception e) {
            return fail(failures, user, "GET failed: " + e) >= 0;
        }
    }

    private static long fail(int[] failures, int user, String message) {
        failures[user]++;
        System.out.println("[calibrate] user " + user + " " + message);
        return -1;
    }

    private static long serverNanos(HttpResponse<String> response) {
        return response.headers().firstValue(LatencyMock.ELAPSED_HEADER).map(Long::parseLong).orElse(0L);
    }

    private static long idOf(String body) {
        int at = body.lastIndexOf("\"id\":");
        if (at < 0) {
            return -1;
        }
        int end = at + 5;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return Long.parseLong(body.substring(at + 5, end));
    }

    private static double percentileMs(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(Math.max(0, index)) / 1_000_000.0;
    }

    private static String next(String[] args, int index) {
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("missing value for " + args[index]);
        }
        return args[index + 1];
    }

    /** Closes the mock's window. A 409 means requests were still in flight — that is a bug here,
     *  not something to retry around, so it fails the run rather than silently measuring across it. */
    private static void reset(String url) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url + "/stats/reset")).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("could not close the mock's window: " + response.body());
        }
    }

}
