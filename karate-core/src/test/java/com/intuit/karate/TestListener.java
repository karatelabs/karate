package com.intuit.karate;
import org.junit.runner.notification.RunListener;
import org.junit.runner.Result;
import java.util.*;
import com.intuit.karate.core.AdhocCoverageTool;

/**
 *  Prints the result of the adhoc coverage tool.
 */
public class TestListener extends RunListener {
    @Override
    public void testRunFinished(Result result) throws Exception {
        System.out.format("\n--------------Adhoc Coverage Tool Results----------------\n");

        // Find the longest method name in the result.
        int longest_method = 0;
        for(Map.Entry<String, Boolean[]> e : AdhocCoverageTool.m.entrySet()) {
            longest_method = Math.max(longest_method, e.getKey().length());
        }

        // Print each result.
        for(Map.Entry<String, Boolean[]> e : AdhocCoverageTool.m.entrySet()) {
            String method = e.getKey();
            Boolean[] res = e.getValue();

            int coveredBranches = 0;
            for(Boolean b : res) {
                coveredBranches = b != null && b ? coveredBranches+1 : coveredBranches;
            }

            System.out.format(
                    "%-" + (longest_method+1) + "s: %d/%d\n", 
                    method, 
                    coveredBranches, 
                    res.length
                );

        }
    }
}
