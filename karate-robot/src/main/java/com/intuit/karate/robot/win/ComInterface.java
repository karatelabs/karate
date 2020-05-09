/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.robot.win;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ComInterface {

    public final String name;
    public final String implementing;
    public final String guid;
    public final Map<String, ComFunction> functions = new LinkedHashMap();

    public ComInterface(String name, String implementing, String guid) {
        this.name = name;
        this.implementing = implementing;
        this.guid = guid;
    }

    public void add(ComFunction function) {
        functions.put(function.name, function);
    }

    public Function getFunction(String functionName, Pointer p) {
        ComFunction cf = functions.get(functionName);
        if (cf == null) {
           throw new RuntimeException("no such function: " + functionName + " in: " + name);
        }
        Pointer tableRef = p.getPointer(0);
        Pointer functionRef = tableRef.getPointer(cf.vtableId);
        return Function.getFunction(functionRef, Function.ALT_CONVENTION);
    }

    public Function getFunction(int offset, Pointer p) {
        Pointer tableRef = p.getPointer(0);
        Pointer functionRef = tableRef.getPointer(offset * Native.POINTER_SIZE);
        return Function.getFunction(functionRef, Function.ALT_CONVENTION);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" [").append(implementing).append("] ").append(guid);
        for (ComFunction cf : functions.values()) {
            sb.append('\n').append(cf);
        }
        return sb.toString();
    }

}
