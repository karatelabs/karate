/*
 * Test-only fixture: an ext that records the Suite root it was handed in onBoot, so
 * SuiteRootTest can pin that THE root is already final when an ext boots (boot runs
 * inside the Suite constructor, before config discovery and feature resolution).
 * Resolved by name convention (io.karatelabs.ext.rootcapture.RootcaptureExt) from
 * `boot.ext('rootcapture')`.
 */
package io.karatelabs.ext.rootcapture;

import io.karatelabs.core.Ext;
import io.karatelabs.core.Suite;

import java.nio.file.Path;

public class RootcaptureExt implements Ext {

    /** The root seen by the most recent onBoot on this thread. */
    public static final ThreadLocal<Path> SEEN = new ThreadLocal<>();

    /** The working dir seen by the most recent onBoot — null, since it is assigned after boot. */
    public static final ThreadLocal<Path> SEEN_WORKING_DIR = new ThreadLocal<>();

    @Override
    public void onBoot(Suite suite) {
        SEEN.set(suite.getRoot());
        SEEN_WORKING_DIR.set(suite.getWorkingDir());
    }
}
