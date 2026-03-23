package io.karatelabs.js;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

class EvalBase {

    static final Logger logger = LoggerFactory.getLogger(EvalTest.class);

    Engine engine;

    Object eval(String text) {
        return eval(text, null);
    }

    Object eval(String text, String vars) {
        engine = new Engine();
        if (vars != null) {
            Map<String, Object> map = NodeUtils.fromJson(vars);
            map.forEach(engine::put);
        }
        return engine.eval(text);
    }

    void matchEval(String text, String expected) {
        matchEval(text, expected, null);
    }

    void matchEval(String text, String expected, String vars) {
        match(eval(text, vars), expected);
    }

    void match(Object actual, String expected) {
        NodeUtils.match(actual, expected);
    }

    Object get(String varName) {
        return engine.getBindings().get(varName);
    }

}
