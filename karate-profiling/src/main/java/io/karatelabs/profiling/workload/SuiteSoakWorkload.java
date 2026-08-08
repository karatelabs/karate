/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.profiling.workload;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.profiling.JvmConfig;
import io.karatelabs.profiling.ReportCostListener;
import io.karatelabs.profiling.ReportMode;
import io.karatelabs.profiling.RunShape;
import io.karatelabs.profiling.Workload;
import io.karatelabs.profiling.WorkloadContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * A long-running <em>Runner</em> suite shaped like an enterprise regression suite: many small
 * features, reports on, HTTP over TLS, a config that computes, a shared-feature call and a JS
 * helper per scenario.
 *
 * <p><b>Why this exists, when three other workloads already soak.</b> None of them can answer
 * the question a user asks as "does karate leak". The pooled Gatling soak runs with per-step
 * capture gated off and one shared connection pool; {@code scope-capture-*} makes no HTTP call
 * at all; {@code call-accumulation} and {@code feature-spread} write reports but are sized by
 * iteration count rather than a window and never open a socket. What is left unmeasured is
 * precisely the intersection: <b>reports on, over hours, over TLS, with a client per
 * scenario</b> — and the client-per-scenario path is where the original lifecycle defect lived.
 *
 * <p><b>Three open questions, one run</b> (docs/PROFILING.md §9, E1):
 * <ul>
 *   <li><b>Reports-on retention.</b> A suite retains a result skeleton per scenario until suite
 *       end <em>by design</em>, and with per-step capture on that skeleton holds the rendered
 *       request and response text. So "no leak" here is <b>not</b> "flat" — it is "the live set
 *       rises at the rate the design predicts and no faster". Predict the slope from a
 *       rehearsal, then read the soak against the prediction.</li>
 *   <li><b>The unpooled client lifecycle.</b> Every scenario builds its own client, uses it over
 *       TLS and hands it back; so does every <em>called</em> feature, because a call mints a
 *       fresh runtime with a fresh client. In pooled mode a missed release cannot abandon a
 *       socket — the pool owns it. Here it can, and it shows up as a widening gap between
 *       {@code fds} and {@code fdsAfterGc} in the soak's live-set panel.</li>
 *   <li><b>Cross-suite retention.</b> With {@code -Dprofiling.soak.suites=N} the same features
 *       run as N consecutive suites in one JVM. <b>Nothing at all should survive a suite's
 *       end</b>, which makes this the sharper discriminator: designed per-scenario retention
 *       resets at every boundary, a leak does not. It is also the shape long-lived processes
 *       ({@code karate serve}, the IDE plugin) actually have.</li>
 * </ul>
 *
 * <p><b>Iteration-bounded, deliberately.</b> This workload owns its suite, so it cannot close a
 * window on request and {@code --duration} is refused for it. Size the scenario count from a
 * rehearsal's measured rate and <b>pass {@code --timeout} explicitly</b> — an iteration-bounded
 * run defaults to a flat one hour, which a two-hour soak sits on the wrong side of.
 *
 * <pre>
 *   # rehearsal: ~10 minutes, a probe every 20s instead of every 5 minutes
 *   etc/run.sh suite-soak --iterations 2000 --threads 4 --soak --timeout 30m \
 *       --mock-url https://&lt;mock&gt;:8443 -Dkarate.profiling.reports=all \
 *       -Dkarate.profiling.liveSetSeconds=20
 *
 *   # the soak itself, sized from what the rehearsal measured
 *   etc/run.sh suite-soak --iterations 40000 --threads 4 --soak --timeout 3h --xmx 8g \
 *       --mock-url https://&lt;mock&gt;:8443 -Dkarate.profiling.reports=all
 * </pre>
 *
 * <p>Knobs, all system properties on the child JVM:
 * <ul>
 *   <li>{@code profiling.soak.scenarios} — scenarios per feature (default 10). Feature count is
 *       the per-suite scenario count divided by this. Small features on purpose: one giant
 *       outline is known-unbounded by design and would swamp the signal.</li>
 *   <li>{@code profiling.soak.suites} — consecutive suites in one JVM (default 1). The total
 *       {@code --iterations} is split across them.</li>
 *   <li>{@code profiling.soak.allowedFailures} — scenario failures tolerated before the run is
 *       abandoned (default 0). See below.</li>
 *   <li>{@code karate.profiling.reports} — {@code all} for this experiment; reporting is the
 *       thing under test.</li>
 *   <li>{@code karate.profiling.reportCost} — attach {@link ReportCostListener}.</li>
 * </ul>
 *
 * <p><b>Failures stop the run, and are printed where the digest can see them.</b> A Runner suite
 * reports a failed scenario in its result rather than by throwing, so a soak whose every
 * scenario failed would otherwise reach {@code PROFILING-SUMMARY errors=0}, exit 0 and produce a
 * clean-looking digest of a run that measured nothing — the exact failure mode docs/PROFILING.md
 * §10 was written around. So this emits {@link #SUITE_PREFIX} after every suite (cumulative, so
 * a killed run still carries the counts up to its last completed suite), refuses to start the
 * next suite once failures exceed the allowance, and throws at the end so the child exits
 * non-zero.
 */
public class SuiteSoakWorkload implements Workload {

    /**
     * Cumulative suite counts, one line per completed suite, read by the digest.
     *
     * <p>Cumulative rather than per-suite because the consumer is
     * {@code JfrDigest.childLine}, which takes the <em>last</em> matching line: a run killed by
     * its timeout still yields totals up to the last boundary it crossed, instead of the counts
     * of one suite in the middle.
     */
    public static final String SUITE_PREFIX = "PROFILING-SUITE ";

    /**
     * Requests one passing scenario makes: the auth call's {@code GET /ping}, then the
     * {@code POST /cats} and {@code GET /cats/{id}}.
     *
     * <p>Used only to state what the mock <em>should</em> have served, so the digest can
     * reconcile the two counts the way it does for the Gatling lane. It is exact for passing
     * scenarios and unknown for failed ones — which is why the digest reconciles against
     * {@code passed}, and why a run with failures is refused above.
     */
    static final int REQUESTS_PER_SCENARIO = 3;

    private WorkloadContext context;
    private Path generatedDir;
    private Path featuresDir;

    @Override
    public String name() {
        return "suite-soak";
    }

    @Override
    public String describe() {
        return "A long Runner suite shaped like an enterprise regression suite: many small features, "
                + "every report format on, config JS, a shared-feature call and a JS helper per "
                + "scenario, and POST + GET over TLS with a client built per scenario. The only "
                + "workload that soaks reporting and the per-scenario client lifecycle together; "
                + "-Dprofiling.soak.suites=N repeats the suite in one JVM, where nothing should survive.";
    }

    @Override
    public boolean drivesOwnConcurrency() {
        return true;
    }

    @Override
    public boolean needsMock() {
        return true;
    }

    @Override
    public JvmConfig jvm() {
        // Bigger than the memory workloads' 768m on purpose: this one retains a result skeleton
        // per scenario until suite end BY DESIGN, so a heap that forces an OOM would be measuring
        // the design rather than a leak. The soak's own sizing comes from the rehearsal — raise it
        // with --xmx, the bench hosts have 32 GB.
        return new JvmConfig("2g", JvmConfig.Gc.G1);
    }

    @Override
    public RunShape shape() {
        // Modest defaults: this workload is only meaningful when sized from a rehearsal, and a
        // default large enough to be interesting would be large enough to be expensive by accident.
        return new RunShape(4, 2000, null, Duration.ZERO, Duration.ofMinutes(30));
    }

    @Override
    public void setup(WorkloadContext context) {
        this.context = context;
        String mockUrl = context.requireMockUrl();
        long totalScenarios = context.iterations() > 0 ? context.iterations() : 1000;
        int suites = suites();
        int perFeature = Integer.getInteger("profiling.soak.scenarios", 10);
        long perSuite = Math.max(perFeature, totalScenarios / suites);
        int features = (int) Math.max(1, perSuite / perFeature);
        try {
            generatedDir = Files.createTempDirectory("karate-suite-soak-");
            featuresDir = Files.createDirectory(generatedDir.resolve("features"));
            Files.writeString(generatedDir.resolve("karate-config.js"), configText(mockUrl));
            // The callee sits beside the features it is called from, so `read('auth.feature')`
            // resolves relative to the caller with no path arithmetic. @ignore keeps it out of the
            // suite's own scenario count while leaving it callable — a called feature is not
            // filtered by tags.
            Files.writeString(featuresDir.resolve("auth.feature"), authFeatureText());
            for (int f = 0; f < features; f++) {
                Files.writeString(featuresDir.resolve("soak_" + f + ".feature"),
                        featureText(f, perFeature));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("[workload] generated " + features + " features x " + perFeature
                + " scenarios in " + featuresDir + ", " + suites + " suite(s), mock " + mockUrl);
    }

    private int suites() {
        int suites = Integer.getInteger("profiling.soak.suites", 1);
        if (suites < 1) {
            throw new IllegalArgumentException("profiling.soak.suites must be at least 1");
        }
        return suites;
    }

    /**
     * A config that does work, because an enterprise one does.
     *
     * <p>Deliberately NOT the harness's shared {@code karate-config.js} — that file reads an
     * absent property on purpose, as the only regression guard on the missed-read path, and
     * pointing this suite at its own directory leaves it untouched. The mock URL is baked in as a
     * literal rather than read from a property for the same reason: nothing here should depend on
     * the shared file's contents.
     *
     * <p>The two functions are invoked per scenario, so config evaluation is real work in the
     * measured window rather than a constant map — which is the difference between this workload
     * and every parity cell measured so far.
     */
    private static String configText(String mockUrl) {
        return """
                function fn() {
                  var env = karate.env || 'soak';
                  var config = {
                    mockUrl: '%s',
                    env: env,
                    makeName: function(i) { return 'cat_' + env + '_' + i; },
                    payloadFor: function(i) {
                      return { name: config.makeName(i), age: i %% 20, tag: env + '-soak' };
                    }
                  };
                  return config;
                }
                """.formatted(mockUrl);
    }

    /**
     * The auth-shaped shared callee — one HTTP round trip and a token handed back.
     *
     * <p>It makes a real call rather than computing a token locally because a called feature gets
     * its <em>own</em> runtime and therefore its own HTTP client: exercising it is the only way
     * this workload covers the release path for a call-scoped client, which is a distinct site
     * from the scenario's own and just as able to abandon a socket.
     */
    private static String authFeatureText() {
        return """
                @ignore
                Feature: the shared auth-shaped callee

                  Called once per scenario, as an enterprise suite calls a login or token feature.
                  Its own HTTP client is a second per-scenario client, which is the point.

                  Scenario: fetch a token
                    Given url baseUrl
                    And path 'ping'
                    When method get
                    Then status 200
                    * def token = 'Bearer ' + response.success + '-' + env
                """;
    }

    /**
     * One generated feature: config helpers, a shared-feature call, then POST + GET with a closed
     * match. The match is written with embedded expressions rather than a merge because that is
     * how suites are written, and it keeps {@code processEmbeddedExpressions} in the measured path.
     */
    private static String featureText(int index, int scenarios) {
        StringBuilder sb = new StringBuilder();
        sb.append("Feature: suite soak feature ").append(index).append('\n');
        for (int s = 0; s < scenarios; s++) {
            sb.append("\n  Scenario: soak ").append(index).append(" scenario ").append(s).append('\n');
            sb.append("    * def auth = call read('auth.feature') { baseUrl: '#(mockUrl)', env: '#(env)' }\n");
            sb.append("    * def payload = payloadFor(").append(s).append(")\n");
            sb.append("    Given url mockUrl\n");
            sb.append("    And path 'cats'\n");
            sb.append("    And header Authorization = auth.token\n");
            sb.append("    And request payload\n");
            sb.append("    When method post\n");
            sb.append("    Then status 201\n");
            sb.append("    * def catId = response.id\n");
            sb.append("    Given url mockUrl\n");
            sb.append("    And path 'cats', catId\n");
            sb.append("    And header Authorization = auth.token\n");
            sb.append("    When method get\n");
            sb.append("    Then status 200\n");
            sb.append("    And match response == { id: '#(catId)', name: '#(payload.name)',")
                    .append(" age: '#(payload.age)', tag: '#(payload.tag)' }\n");
        }
        return sb.toString();
    }

    @Override
    public void iterate(int vu, long iteration) {
        ReportMode reports = ReportMode.current();
        boolean reportCost = Boolean.getBoolean("karate.profiling.reportCost");
        int suites = suites();
        long allowedFailures = Long.getLong("profiling.soak.allowedFailures", 0);
        System.out.println("[workload] reports=" + reports + " reportCost=" + reportCost
                + " suites=" + suites + " threads=" + context.threads());
        long start = System.nanoTime();
        long scenarios = 0;
        long passed = 0;
        long failed = 0;
        int completed = 0;
        for (int suite = 1; suite <= suites; suite++) {
            SuiteResult result = runSuite(suite, reports, reportCost);
            scenarios += result.getScenarioCount();
            passed += result.getScenarioPassedCount();
            failed += result.getScenarioFailedCount();
            completed = suite;
            System.out.println(SUITE_PREFIX
                    + "suites=" + completed + "/" + suites
                    + " scenarios=" + scenarios
                    + " passed=" + passed
                    + " failed=" + failed
                    + " requests=" + passed * REQUESTS_PER_SCENARIO
                    + " elapsedMs=" + (System.nanoTime() - start) / 1_000_000);
            if (failed > allowedFailures) {
                System.out.println("[workload] !! " + failed + " scenario(s) failed, over an allowance of "
                        + allowedFailures + " — abandoning after suite " + suite + " of " + suites
                        + ". A soak whose scenarios fail is not a slower soak, it is a soak with holes:"
                        + " the retention being measured is the retention of work that did not happen.");
                break;
            }
        }
        if (failed > allowedFailures) {
            // Thrown rather than printed: the child counts a thrown iteration as an error and exits
            // non-zero, which is what stops a failed soak from reading as a completed one to
            // whatever is scripting it.
            throw new IllegalStateException(failed + " of " + scenarios + " scenarios failed"
                    + " — see the suite output above; the digest's suite panel carries the counts");
        }
    }

    private SuiteResult runSuite(int suite, ReportMode reports, boolean reportCost) {
        // A directory per suite, so a repeated-suites run measures the disk the reports actually
        // cost rather than overwriting itself — the report volume is a pre-registered budget for
        // this experiment, not an afterthought. The karate-reports* prefix keeps it inside the
        // sweep of the disk-hygiene commands in docs/PROFILING.md.
        Path outputDir = Path.of("target", "karate-reports-suite-soak", "suite-" + suite);
        Runner.Builder builder = reports.applyTo(Runner.path(featuresDir.toString()))
                .configDir(generatedDir.toAbsolutePath().toString())
                .karateEnv("soak")
                .outputDir(outputDir)
                .outputConsoleSummary(false);
        ReportCostListener costListener = null;
        if (reportCost) {
            costListener = new ReportCostListener();
            builder.resultListener(costListener);
        }
        long started = System.nanoTime();
        SuiteResult result = builder.parallel(context.threads());
        System.out.println("[workload] suite " + suite + ": scenarios=" + result.getScenarioCount()
                + " passed=" + result.getScenarioPassedCount()
                + " failed=" + result.getScenarioFailedCount()
                + " ms=" + (System.nanoTime() - started) / 1_000_000);
        if (costListener != null) {
            costListener.print(result.getDurationMillis(), context.threads());
        }
        return result;
    }

    @Override
    public void teardown() {
        if (generatedDir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(generatedDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort — a leftover temp dir is noise, not a failed measurement
                }
            });
        } catch (IOException ignored) {
            // as above
        }
    }

}
