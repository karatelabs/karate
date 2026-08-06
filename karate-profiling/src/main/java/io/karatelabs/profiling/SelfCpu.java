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

import java.lang.management.ManagementFactory;

/**
 * A process's own CPU time, over a window it declares itself.
 *
 * <p><b>This is the acceptance leg that had no evidence.</b> A parity cell claims two clients
 * achieved the same throughput <i>and</i> that neither was the bottleneck, and the second half is
 * what makes the first half mean anything — two saturated clients queued behind one server also
 * report identical numbers. docs/PROFILING.md §10 admitted the gap in writing: "Headroom, injector
 * side: not recorded anywhere. Not in the digests, not in the table." This is that number.
 *
 * <p><b>Why the process measures itself rather than the parent measuring it.</b> The obvious design
 * — have the parent poll {@code ProcessHandle.info().totalCpuDuration()} on the child it forked —
 * is not portable, and fails on the machine this harness is developed on. Measured on macOS 25.5,
 * JDK 24: a process reads its <i>own</i> total happily, and reading a <i>child's</i> returns
 * {@code Optional.empty} every time. An instrument that only works on the machine it is not
 * developed on cannot be trusted when it finally reports something, so the measurement lives where
 * every platform allows it.
 *
 * <p><b>The window is the real reason, though.</b> A parent sees a process lifetime: JVM startup
 * and JIT at the front, an idle tail at the back while pipes drain and reports are written. On a
 * short cell that dilution is most of the run, and no amount of sliding-window guesswork recovers
 * the boundary. The process itself knows exactly when its measured window opened — after warmup,
 * at {@code [child] measuring} — so it can report CPU for that window and nothing else. Exact beats
 * inferred, and here it is also less code.
 *
 * <p>{@code getProcessCpuTime()} is the whole process across all threads, which is what
 * "headroom" needs: virtual threads, carrier threads, GC threads and JIT compiler threads all
 * count against the same cores. Read {@link Window#cores()} against the machine's processor count —
 * near 1.0 per available core means the run measured the machine rather than the client.
 */
public final class SelfCpu {

    /** Emitted into the child's summary line and the mock's stats JSON; the digest reads both. */
    public static final String CPU_NANOS = "cpuNanos";
    public static final String WALL_NANOS = "wallNanos";

    private static final com.sun.management.OperatingSystemMXBean OS = osBean();

    private SelfCpu() {
    }

    private static com.sun.management.OperatingSystemMXBean osBean() {
        // The com.sun bean is where getProcessCpuTime lives; the platform interface has no such
        // method. Every JDK this harness runs on provides it, but a cast that could fail silently
        // in some future JVM is worth guarding rather than debugging.
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        return bean instanceof com.sun.management.OperatingSystemMXBean sun ? sun : null;
    }

    /** This process's CPU across all threads, or -1 where the JVM declines to say. */
    public static long nanos() {
        return OS == null ? -1 : OS.getProcessCpuTime();
    }

    /** Open a measurement window at this instant. */
    public static Window open() {
        return new Window(nanos(), System.nanoTime());
    }

    /**
     * A window opened at construction and read at {@link #close()}. Deliberately a value rather
     * than something closeable: it is read at a point the caller chooses, sometimes more than once,
     * and it holds no resource.
     */
    public record Window(long cpuNanosAtOpen, long wallNanosAtOpen) {

        /** CPU consumed since the window opened, or -1 if unavailable. */
        public long cpuNanos() {
            long now = nanos();
            return now < 0 || cpuNanosAtOpen < 0 ? -1 : now - cpuNanosAtOpen;
        }

        public long wallNanos() {
            return System.nanoTime() - wallNanosAtOpen;
        }

        /** Mean cores busy over the window — CPU seconds per wall second. */
        public double cores() {
            long cpu = cpuNanos();
            long wall = wallNanos();
            return cpu < 0 || wall <= 0 ? -1 : cpu / (double) wall;
        }

        /** The two raw numbers, as key=value pairs for a summary line. Derived values stay derived. */
        public String describe() {
            return CPU_NANOS + "=" + cpuNanos() + " " + WALL_NANOS + "=" + wallNanos();
        }
    }

}
