package karate;

import com.intuit.karate.junit5.Karate;

class SetupDryRunTest {

    @Karate.Test
    Karate testDryRunStaticExamples() {
        return Karate.run("setup-with-dryrun").tags("static").dryRun(true).relativeTo(getClass());
    }
    @Karate.Test
    Karate testDryRunWithDynamicSamplesFromSetup() {
        return Karate.run("setup-with-dryrun").dryRun(true).tags("dynamic").relativeTo(getClass());
    }
 }
