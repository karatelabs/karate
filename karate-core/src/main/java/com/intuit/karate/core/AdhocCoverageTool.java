package com.intuit.karate.core;

import java.util.*;

/**
 * Class that hold public variable for the adhoc coverage tool.
 */
public class AdhocCoverageTool {
    public static HashMap<String, Boolean[]> m = new HashMap<>();
    static {
        // Initialize boolean arrays here:
        // Ex: m.put("ParseUriPattern", new Boolean[10]);
        m.put("stepHtml", new Boolean[22]);
    }
}
