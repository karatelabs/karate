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
 * is the most dangerous outcome available. The number that exposes it is measured from out here:</p>
 *
 * <pre>
 *   queueing residue = client p99 - (injected latency + mock handler p99)
 * </pre>
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
 *        --ramp 1,2,4,8,16,32,64 --requests 2000 --latency 10ms
 * </pre>
 */
public final class MockCalibrator {

    private static final String BODY = "{ \"name\": \"Billie\", \"age\": 5 }";

    private MockCalibrator() {
    }

    public static void main(String[] args) throws Exception {
        // java.net.http refuses "Connection" as a restricted header, and connection-per-request is
        // half of what this tool exists to measure. Must be set before HttpClient initialises,
        // hence the first line. The alternative — a fresh HttpClient per request — would model
        // Karate's per-execution client faithfully but load the CLIENT with the cost, which is
        // exactly what a calibration client must not do.
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "connection");
        String url = null;
        int[] ramp = {1, 2, 4, 8, 16, 32, 64};
        int requests = 2000;
        Duration latency = Duration.ZERO;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--url" -> url = args[++i];
                case "--ramp" -> ramp = Arrays.stream(args[++i].split(",")).mapToInt(s -> Integer.parseInt(s.trim())).toArray();
                case "--requests" -> requests = Integer.parseInt(args[++i]);
                case "--latency" -> latency = RunShape.parseDuration(args[++i]);
                default -> {
                }
            }
        }
        if (url == null) {
            throw new IllegalArgumentException("usage: MockCalibrator --url <url> [--ramp 1,2,4] [--requests N] [--latency 10ms]");
        }

        // Unmeasured, and not optional: the first requests pay for class loading, JIT and the
        // server's own first-touch costs. Left in, they land in p99 - which is the number the
        // knee is read from - and would put the knee wherever the JIT happened to finish.
        System.out.println("[calibrate] warming up");
        run(url, 4, 500, true);
        run(url, 4, 500, false);

        System.out.printf("%n[calibrate] %s, %d requests per point, injected latency %s (nominal)%n",
                url, requests, latency.toMillis() + "ms");
        System.out.printf("%-10s %-6s %10s %10s %10s %10s %10s %10s%n",
                "mode", "users", "tps", "p50 ms", "p99 ms", "held p50", "unowned", "growth");
        for (boolean keepAlive : new boolean[]{true, false}) {
            double baseline = Double.NaN;
            for (int users : ramp) {
                get(url + "/stats/reset");   // this point's numbers, not the ramp's history
                Result r = run(url, users, requests, keepAlive);
                String stats = get(url + "/stats");
                // Held = service + the sleep the mock ACTUALLY took, so nothing here assumes the
                // nominal knob value. Compared p50 to p50: subtracting the mock's p99 from the
                // client's p99 compares two different requests' tails, which is not a queue
                // measurement and reads backwards as concurrency rises.
                double heldP50Ms = jsonLong(stats, "heldMicrosP50") / 1000.0;
                double unowned = r.p50Ms - heldP50Ms;
                if (Double.isNaN(baseline)) {
                    baseline = unowned;
                }
                // The absolute figure is a floor - connection setup, syscalls, the client's own
                // cost - and says nothing on its own. Its GROWTH over the single-user baseline is
                // the queueing signal, because that floor is the one thing that should not change
                // with concurrency.
                System.out.printf("%-10s %-6d %10.0f %10.2f %10.2f %10.2f %10.2f %10.2f%n",
                        keepAlive ? "keepalive" : "close", users, r.tps, r.p50Ms, r.p99Ms,
                        heldP50Ms, unowned, unowned - baseline);
            }
        }
        System.out.println("""

                [calibrate] The knee is where 'growth' departs from ~0 - NOT where tps stops
                rising. tps flattens for legitimate reasons in a closed loop (users / iteration
                time is a hard ceiling), so a flat tps column is not saturation. 'unowned' is
                client p50 minus what the mock can account for: a floor at one user, and any
                rise above that floor is time spent queued where neither clock can see it.
                Run the parity matrix strictly below that point, in whichever mode knees first.""");
    }

    private record Result(double tps, double p50Ms, double p99Ms) {
    }

    private static Result run(String url, int users, int totalRequests, boolean keepAlive) throws Exception {
        int perUser = Math.max(1, totalRequests / users);
        long[][] samples = new long[users][perUser];
        CountDownLatch done = new CountDownLatch(users);
        long start = System.nanoTime();
        for (int u = 0; u < users; u++) {
            int user = u;
            Thread.ofVirtual().start(() -> {
                // A client per user in keep-alive mode (one pooled connection each); in close mode
                // the Connection: close header is what forces a fresh connection per request.
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                try {
                    for (int i = 0; i < perUser; i++) {
                        long t0 = System.nanoTime();
                        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url + "/cats"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(BODY));
                        if (!keepAlive) {
                            builder.header("Connection", "close");
                        }
                        client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                        samples[user][i] = System.nanoTime() - t0;
                    }
                } catch (Exception e) {
                    // A failed point is worth seeing rather than silently averaging away — at the
                    // 0 ms tier in close mode this is where ephemeral-port exhaustion shows up.
                    System.out.println("[calibrate] user " + user + " failed: " + e);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;

        List<Long> all = new ArrayList<>(users * perUser);
        for (long[] perUserSamples : samples) {
            for (long sample : perUserSamples) {
                if (sample > 0) {
                    all.add(sample);
                }
            }
        }
        all.sort(null);
        return new Result(all.size() / elapsedSeconds, percentileMs(all, 0.50), percentileMs(all, 0.99));
    }

    private static double percentileMs(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(Math.max(0, index)) / 1_000_000.0;
    }

    private static String get(String url) throws Exception {
        return HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .body();
    }

    /** Enough JSON for a flat object of numbers — this reads the mock's own stats, nothing else. */
    private static long jsonLong(String json, String key) {
        int at = json.indexOf('"' + key + '"');
        if (at < 0) {
            return 0;
        }
        int colon = json.indexOf(':', at);
        int end = colon + 1;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(json.substring(colon + 1, end).trim());
    }

}
