package robot.core;

import com.intuit.karate.junit5.Karate;

/**
 *
 * @author peter
 */
class CoreRunner {

    @Karate.Test
    Karate testCalc() {
        return Karate.run("calc").relativeTo(getClass());
    }

    @Karate.Test
    Karate testCaller() {
        return Karate.run("caller").relativeTo(getClass());
    }

    @Karate.Test
    Karate testChrome() {
        return Karate.run("chrome").relativeTo(getClass());
    }

    @Karate.Test
    Karate testIphone() {
        return Karate.run("iphone").relativeTo(getClass());
    }

    @Karate.Test
    Karate testUpload() {
        return Karate.run("upload").relativeTo(getClass());
    }

    @Karate.Test
    Karate testWordpad() {
        return Karate.run("wordpad").relativeTo(getClass());
    }

}
