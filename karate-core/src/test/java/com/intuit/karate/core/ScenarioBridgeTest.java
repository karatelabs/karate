package com.intuit.karate.core;

import com.intuit.karate.TestUtils;
import com.intuit.karate.graal.JsMap;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ScenarioBridgeTest {
    private static final Logger logger        = LoggerFactory.getLogger(ScenarioBridgeTest.class);

    private static final String csDATA         = "data";
    private static final String csFEATUREFILE  = "callSingleFeature.feature";
    private static final String csKEY          = "receivedParam";
    private static final String csNOTHING      = "Nothing";
    private static final String csSCENARIOFILE = "callSingleScenario.feature@storeValue";

    private ScenarioBridge  moTest;
    private ScenarioEngine  moEngine;

    @BeforeEach
    void beforeEach() {
        moEngine = TestUtils.engine();
        moEngine.init();
        moTest = new ScenarioBridge(moEngine);
    }

    /**
     * Verify the answer
     * @param inoToCheck Object to check
     * @param insValue  Value to check
     * @return true if the answer is correct
     */
    private boolean verifyAnswer(JsMap inoToCheck, String insValue) {
        assertNotNull(inoToCheck);
        assertEquals(insValue, inoToCheck.get(csKEY));
        return true;
    }

    @Test
    void testCallSingle() throws Exception {
        JsMap oFound = (JsMap) moTest.callSingle(csFEATUREFILE);

        assertTrue(verifyAnswer(oFound, csNOTHING));
        JsMap oFound1 = (JsMap) moTest.callSingle(csFEATUREFILE);

        assertTrue(verifyAnswer(oFound1, csNOTHING));

        oFound1 = (JsMap) moTest.callSingle(csFEATUREFILE + "?testWithoutParam");
        assertTrue(verifyAnswer(oFound1, csNOTHING));
    }

    @Test
    void testCallSingleWithParam() throws Exception {
        final Map<String, Object>   mapValue = new LinkedHashMap(3);
        final Value                 oParam = Value.asValue(mapValue);
        JsMap                       oFound = (JsMap) moTest.callSingle(csFEATUREFILE, oParam);
        String                      sParam = "first";

        assertTrue(verifyAnswer(oFound, csNOTHING));
        JsMap oFound1 = (JsMap) moTest.callSingle(csFEATUREFILE, oParam);

        assertTrue(verifyAnswer(oFound1, csNOTHING));

        oFound1 = (JsMap) moTest.callSingle(csFEATUREFILE + "?test", oParam);
        assertTrue(verifyAnswer(oFound1, csNOTHING));

        mapValue.put(csDATA, sParam);
        oFound1 = (JsMap) moTest.callSingle(csFEATUREFILE + "?test", oParam);
        assertTrue(verifyAnswer(oFound1, csNOTHING));

        oFound1 = (JsMap) moTest.callSingle(csFEATUREFILE + "?first", oParam);
        assertTrue(verifyAnswer(oFound1, sParam));

        mapValue.clear();
        oFound1 = (JsMap) moTest.callSingle(csFEATUREFILE + "?first", oParam);
        assertTrue(verifyAnswer(oFound1, sParam));
    }

    @Test
    void testCallSingleScenarioWithParam() throws Exception {
        final Map<String, Object>   mapValue = new LinkedHashMap(3);
        final Value                 oParam = Value.asValue(mapValue);
        JsMap                       oFound = (JsMap) moTest.callSingle(csSCENARIOFILE, oParam);
        String                      sParam = "two";;

        assertTrue(verifyAnswer(oFound, csNOTHING));
        JsMap oFound1 = (JsMap) moTest.callSingle(csSCENARIOFILE, oParam);

        assertTrue(verifyAnswer(oFound1, csNOTHING));

        oFound1 = (JsMap) moTest.callSingle(csSCENARIOFILE + "?test1", oParam);
        assertTrue(verifyAnswer(oFound1, csNOTHING));

        mapValue.put(csDATA, sParam);
        oFound1 = (JsMap) moTest.callSingle(csSCENARIOFILE + "?test1", oParam);
        assertTrue(verifyAnswer(oFound1, csNOTHING));

        oFound1 = (JsMap) moTest.callSingle(csSCENARIOFILE + "?two", oParam);
        assertTrue(verifyAnswer(oFound1, sParam));

        mapValue.clear();
        oFound1 = (JsMap) moTest.callSingle(csSCENARIOFILE + "?two", oParam);
        assertTrue(verifyAnswer(oFound1, sParam));
    }

}
