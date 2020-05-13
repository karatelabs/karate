package win;

import com.intuit.karate.junit5.Karate;

/**
 *
 * @author pthomas3
 */
class CalcRunner {
    
    @Karate.Test
    Karate testCalc() {
        return Karate.run("classpath:win/calc.feature");
    }      
    
}
