package com.intuit.karate.graal;

import com.intuit.karate.core.AutoDef;

/**
 *
 * @author pthomas3
 */
public class SimpleAutoDef {

    @AutoDef
    public String hello(String name) {
        return "hello " + name + " !";
    }

    @AutoDef
    public String hello(String name1, String name2) {
        return name1 + name2;
    }

    @AutoDef
    public String hello(String name1, String name2, String name3) {
        return name1 + name2 + name3;
    }

}
