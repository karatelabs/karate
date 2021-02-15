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
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public abstract class IUIAutomationBase extends ComRef {

    protected final ComInterface INTERFACE;

    public IUIAutomationBase() {
        this(new PointerByReference());
    }

    public IUIAutomationBase(PointerByReference ref) {
        super(ref);
        String interfaceName = getClass().getSimpleName();
        INTERFACE = ComUtils.LIBRARY.interfaces.get(interfaceName);
        if (INTERFACE == null) {
            throw new RuntimeException("could not resolve interface: " + interfaceName);
        }
    }

    protected static int enumValue(String name, String key) {
        return ComUtils.enumValue(name, key);
    }
    
    protected static String enumKey(String name, int value) {
        return ComUtils.enumKey(name, value);
    }    

    public int invoke(int offset, Object... args) {
        Function function = INTERFACE.getFunction(offset, REF.getValue());
        return invoke("offset: " + offset, function, args);
    }

    public int invoke(String name, Object... args) {
        Function function = INTERFACE.getFunction(name, REF.getValue());
        return invoke(name, function, args);
    }

    public int invoke(String name, Function function, Object... args) {
        int res = -1;
        List<ComAllocated> toFree = new ArrayList(args.length);
        ComRef lastArg = null;
        try {
            Object[] refs = new Object[args.length + 1];
            refs[0] = REF.getValue();
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                Object val;
                if (arg instanceof ComRef) {
                    ComRef ref = (ComRef) arg;
                    if (i == args.length - 1) { // if last arg
                        val = ref.REF; // reference to pointer
                        lastArg = ref;
                    } else {
                        val = ref.REF.getValue(); // pointer
                    }
                } else if (arg instanceof ComAllocated) {
                    ComAllocated ca = (ComAllocated) arg;
                    toFree.add(ca);
                    val = ca.value();
                } else {
                    val = arg;
                }
                refs[i + 1] = val;
            }
            res = function.invokeInt(refs);
            if (res != 0) {
                logger.warn("{}.{} returned non-zero: {}", INTERFACE.name, name, res);
                if (lastArg != null) {
                    lastArg.setValid(false);
                }
            }
            if (lastArg != null && lastArg.isNull() && logger.isTraceEnabled()) {
                logger.trace("{}.{} returned null: {}", INTERFACE.name, name, lastArg.REF);
            }
        } catch (Exception e) {
            String message = INTERFACE.name + "." + name + " failed with exception: " + e.getMessage();
            logger.error(message);
            throw new RuntimeException(e);
        } finally {
            toFree.forEach(ComAllocated::free);
        }
        return res;
    }

    public <T> T invoke(Class<T> clazz, String name, Object... args) {
        T ref;
        try {
            ref = (T) clazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Object[] refs = new Object[args.length + 1];
        System.arraycopy(args, 0, refs, 0, args.length);
        refs[args.length] = ref;
        invoke(name, refs);
        return ref;
    }

    public IUIAutomationElement invokeForElement(String name, Object... args) {
        return invoke(IUIAutomationElement.class, name, args);
    }

    public IUIAutomationCondition invokeForCondition(String name, Object... args) {
        return invoke(IUIAutomationCondition.class, name, args);
    }

    public String invokeForString(String name) {
        ComRef ref = new ComRef();
        invoke(name, ref);        
        return ref.isNull() ? "" : ref.asString();
    }

    public int invokeForInt(String name) {
        IntByReference ref = new IntByReference();
        invoke(name, ref);
        return ref.getValue();
    }
    
    public boolean invokeForBool(String name) {
        return invokeForInt(name) != 0;
    }    

}
