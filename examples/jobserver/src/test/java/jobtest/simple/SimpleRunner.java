package jobtest.simple;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import common.ReportUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class SimpleRunner {
    
    @Test
    void test() {
        Results results = Runner.path("classpath:jobtest/simple").tags("~@ignore").parallel(1);
        ReportUtils.generateReport(results.getReportDir());
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }      
    
}
