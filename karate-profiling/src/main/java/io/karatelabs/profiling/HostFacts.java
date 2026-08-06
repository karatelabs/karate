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
package io.karatelabs.profiling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The host limits that decide whether a connection-churning run was measuring Karate or the kernel.
 *
 * <p><b>The one hard ceiling is ephemeral ports.</b> The karate arm builds an HTTP client per
 * execution — measured, not assumed: {@code distinctPeerPorts} equals the iteration count in every
 * parity cell, against 8 for plain Gatling's keep-alive connections. Each of those lands in
 * TIME_WAIT on close, so sustained connection-per-iteration load has a ceiling of roughly
 * {@code ports / timeWait} per second, and above it the arm stalls waiting for the range to
 * recycle. docs/PROFILING.md §10 spells out what that looks like when it happens: <b>"it reads as
 * 'Karate is slower'"</b>, and notes the matrix has never actually tested the failure mode. Nothing
 * enforces the margin — the inter-run gap is a convention in an operator's shell script — so the
 * least this harness can do is record the numbers and say when a run went over.
 *
 * <p><b>None of the published figures generalise off macOS, and the next phase is not on macOS.</b>
 * §10's "~550 connections/second" is 16,384 ports over a 30-second TIME_WAIT — the macOS default,
 * where the range starts at 49152 and TIME_WAIT is twice a 15-second MSL. Linux ships a different
 * range and a TIME_WAIT that is compiled in at 60 seconds rather than tunable, so the same
 * arithmetic gives a different answer, and {@code tcp_tw_reuse} changes it again by letting the
 * client reuse a TIME_WAIT socket outright. Reading these per run means a digest carries its own
 * ceiling instead of inheriting one from a document written on another machine.
 *
 * <p>Everything here degrades to "unknown" rather than guessing. A missing value prints as such and
 * the derived ceiling is simply not claimed — an instrument that invents a limit is worse than one
 * that admits it does not know.
 */
public final class HostFacts {

    private HostFacts() {
    }

    /**
     * @param portRangeSize    ephemeral ports available for outbound connections, or -1
     * @param timeWaitSeconds  how long a closed port is unusable, or -1
     * @param somaxconn        the kernel's cap on a listen backlog, or -1
     * @param reuseNote        platform-specific note on what softens the ceiling, or null
     */
    public record Network(long portRangeSize, double timeWaitSeconds, long somaxconn, String reuseNote) {

        /**
         * Connections per second this host can sustain indefinitely. A <i>sustained</i> rate — a
         * short burst beats it until the range fills, which is exactly how §10's 0 ms tier passed
         * at roughly eight times this number while opening 8000 connections in 1.9 s.
         */
        public double sustainableConnectionsPerSecond() {
            return portRangeSize <= 0 || timeWaitSeconds <= 0 ? -1 : portRangeSize / timeWaitSeconds;
        }

        public String describe() {
            if (portRangeSize <= 0) {
                return "unknown on this platform";
            }
            String text = portRangeSize + " ephemeral ports, TIME_WAIT "
                    + (timeWaitSeconds <= 0 ? "?" : fixed(timeWaitSeconds, 0) + "s")
                    + " → **~" + fixed(sustainableConnectionsPerSecond(), 0) + " connections/s sustained**"
                    + (somaxconn > 0 ? ", somaxconn " + somaxconn : "");
            return reuseNote == null ? text : text + " (" + reuseNote + ")";
        }
    }

    public static Network read() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) {
            return linux();
        }
        if (os.contains("mac")) {
            return macos();
        }
        return new Network(-1, -1, -1, null);
    }

    private static Network linux() {
        long size = -1;
        String range = procFile("/proc/sys/net/ipv4/ip_local_port_range");
        if (range != null) {
            String[] parts = range.trim().split("\\s+");
            if (parts.length == 2) {
                size = number(parts[1]) - number(parts[0]) + 1;
            }
        }
        // Linux does not expose TIME_WAIT as a tunable: it is TCP_TIMEWAIT_LEN, compiled in at
        // 60 seconds. tcp_fin_timeout is a different state (FIN_WAIT_2) and is the usual thing
        // mistaken for it, which is why the constant is named here rather than read from a file.
        // 0 = off, 1 = on for all outbound, 2 = **loopback only** — and 2 is the default on
        // modern kernels. Reading 2 as "on" would be exactly wrong for the two-host phase, where
        // the mock is remote: the ceiling is NOT lifted for the traffic that matters, and the
        // misreport would fire precisely when the operator skipped the sysctl.
        long reuse = number(procFile("/proc/sys/net/ipv4/tcp_tw_reuse"));
        String note = switch ((int) reuse) {
            case 1 -> "tcp_tw_reuse=1, so outbound connections may reuse a TIME_WAIT socket and the "
                    + "practical ceiling is higher";
            case 2 -> "tcp_tw_reuse=2 — **loopback only**, so a mock on another host gets no relief; "
                    + "`sysctl -w net.ipv4.tcp_tw_reuse=1` covers remote targets too";
            case 0 -> "tcp_tw_reuse is off — `sysctl -w net.ipv4.tcp_tw_reuse=1` raises the ceiling";
            default -> "tcp_tw_reuse unreadable";
        };
        return new Network(size, 60, number(procFile("/proc/sys/net/core/somaxconn")), note);
    }

    private static Network macos() {
        long first = sysctl("net.inet.ip.portrange.first");
        long last = sysctl("net.inet.ip.portrange.last");
        long msl = sysctl("net.inet.tcp.msl");            // milliseconds; TIME_WAIT is 2 x MSL
        return new Network(first > 0 && last >= first ? last - first + 1 : -1,
                msl > 0 ? 2 * msl / 1000.0 : -1,
                sysctl("kern.ipc.somaxconn"),
                "`sudo sysctl -w net.inet.ip.portrange.first=32768 net.inet.tcp.msl=5000` raises the "
                        + "ceiling and reverts on reboot");
    }

    private static String procFile(String path) {
        try {
            Path file = Path.of(path);
            return Files.exists(file) ? Files.readString(file).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** One sysctl value, or -1. Forks a process, so this runs once per run and never during one. */
    private static long sysctl(String key) {
        try {
            Process process = new ProcessBuilder("sysctl", "-n", key)
                    .redirectErrorStream(true).start();
            List<String> lines = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())).lines().toList();
            process.waitFor();
            return lines.isEmpty() ? -1 : number(lines.get(0).trim());
        } catch (InterruptedException e) {
            // Only a real interrupt restores the flag; an IOException from ProcessBuilder is not
            // one, and setting it there would abort an unrelated sleep or join later in the parent.
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static long number(String text) {
        try {
            return text == null ? -1 : Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Length of the {@code yyyy-MM-dd-HHmmss} stamp every run directory name ends with. */
    private static final int STAMP_LENGTH = 17;

    /** The trailing {@code yyyy-MM-dd-HHmmss} of a run directory name, or "" if it has none. */
    public static String stampOf(String runDirName) {
        int at = runDirName.length() - STAMP_LENGTH;
        return at > 0 && runDirName.charAt(at - 1) == '-' ? runDirName.substring(at) : "";
    }

    static String fixed(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

}
