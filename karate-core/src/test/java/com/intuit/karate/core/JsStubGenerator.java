package com.intuit.karate.core;

import com.intuit.karate.StringUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javassist.Modifier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JsStubGenerator {

    static final Logger logger = LoggerFactory.getLogger(JsStubGenerator.class);

    @Test
    void testGenerateKarateStub() {
        StringBuilder sb = new StringBuilder();
        sb.append("function karate() {}\n");
        Class clazz = ScenarioBridge.class;
        Comparator<Method> comparator = (Method o1, Method o2) -> {
            int nameResult = o1.getName().compareTo(o2.getName());
            if (nameResult != 0) {
                return nameResult;
            }
            return o1.getParameterCount() - o2.getParameterCount();            
        };
        List<Method> methods = new ArrayList();
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                methods.add(method);
            }            
        }
        Collections.sort(methods, comparator);
        for (Method method : methods) {
            String name = method.getName();
            if (name.startsWith("get") && name.length() > 3) {
                name = name.substring(3, 4).toLowerCase() + name.substring(4);
                sb.append("karate.").append(name).append(" = ").append("{};\n");
            } else {
                List<String> params = new ArrayList();
                for (Parameter p : method.getParameters()) {
                    params.add(p.getName());
                }
                sb.append("karate.").append(name).append(" = function(").append(StringUtils.join(params, ",")).append(") {};\n");
            }

        }
        logger.debug("js:\n{}", sb);
    }

}
