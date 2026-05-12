/*
 * The MIT License
 *
 * Copyright 2024 Karate Labs Inc.
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
package io.karatelabs.js;

import java.lang.reflect.*;
import java.util.*;

public class JavaUtils {

    static String jsTypeName(Class<?> clazz) {
        if (clazz == null) return "Object";
        if (Map.class.isAssignableFrom(clazz)) return "Object";
        if (List.class.isAssignableFrom(clazz)) return "Array";
        if (clazz.isArray()) return "Array";
        if (clazz == String.class) return "String";
        if (Number.class.isAssignableFrom(clazz) || clazz == int.class
                || clazz == double.class || clazz == long.class
                || clazz == float.class || clazz == short.class
                || clazz == byte.class) return "Number";
        if (clazz == Boolean.class || clazz == boolean.class) return "Boolean";
        if (clazz == Character.class || clazz == char.class) return "String";
        if (Set.class.isAssignableFrom(clazz)) return "Set";
        return clazz.getSimpleName();
    }

    static Object construct(Class<?> clazz, Object[] args) {
        try {
            Constructor<?> constructor = findConstructor(clazz, args);
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw JsErrorException.typeError(jsTypeName(clazz) + " is not a constructor");
        }
    }

    static Object invokeStatic(Class<?> clazz, String name, Object[] args) {
        Method method = findMethod(clazz, name, args);
        if (method == null) {
            throw JsErrorException.typeError("." + name + " is not a function (called on " + jsTypeName(clazz) + ")");
        }
        try {
            return invoke(null, method, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(cause == null ? e.getMessage() : cause.getMessage(), cause);
        } catch (IllegalAccessException e) {
            throw JsErrorException.typeError("." + name + " is not accessible (on " + jsTypeName(clazz) + ")");
        }
    }

    static Object invoke(Object object, String name, Object[] args) {
        Method method = findMethod(object.getClass(), name, args);
        if (method == null) {
            throw JsErrorException.typeError("." + name + " is not a function (called on " + jsTypeName(object.getClass()) + ")");
        }
        try {
            return invoke(object, method, args);
        } catch (InvocationTargetException e) {
            // Unwrap so the underlying Java exception (and its message) reaches the caller
            // and can become a JS-catchable JsError at the host-function boundary.
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(cause == null ? e.getMessage() : cause.getMessage(), cause);
        } catch (IllegalAccessException e) {
            throw JsErrorException.typeError("." + name + " is not accessible (on " + jsTypeName(object.getClass()) + ")");
        }
    }

    static Object getStatic(Class<?> clazz, String name) {
        try {
            Field field = clazz.getField(name);
            return field.get(null);
        } catch (Exception e) {
            if (clazz != null) {
                // Try getter method first (e.g., Base64.encoder -> Base64.getEncoder())
                Method getter = findStaticGetter(clazz, name);
                if (getter != null) {
                    try {
                        return getter.invoke(null, EMPTY);
                    } catch (Exception ex) {
                        throw JsErrorException.typeError("." + name + " is not a function (called on " + jsTypeName(clazz) + ")");
                    }
                }
                // Fall back to method reference
                for (Method m : clazz.getMethods()) {
                    if (m.getName().equals(name)) {
                        JavaType jc = new JavaType(clazz);
                        return jc.getMethod(name);
                    }
                }
                // Static nested class / enum: e.g. Foo.Bar where Bar is a member type
                for (Class<?> nested : clazz.getClasses()) {
                    if (nested.getSimpleName().equals(name)) {
                        return new JavaType(nested);
                    }
                }
            }
            throw JsErrorException.typeError("." + name + " is not a property (on " + jsTypeName(clazz) + ")");
        }
    }

    private static Method findStaticGetter(Class<?> clazz, String name) {
        String getterSuffix = name.substring(0, 1).toUpperCase() + name.substring(1);
        Method method = findStaticMethod(clazz, "get" + getterSuffix);
        if (method == null) {
            method = findStaticMethod(clazz, "is" + getterSuffix);
        }
        return method;
    }

    private static Method findStaticMethod(Class<?> clazz, String name) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name) && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }

    static void setStatic(Class<?> clazz, String name, Object value) {
        try {
            Field field = clazz.getField(name);
            field.set(null, value);
        } catch (Exception e) {
            throw JsErrorException.typeError("cannot set ." + name + " (on " + jsTypeName(clazz) + ")");
        }
    }

    static Object get(Object object, String name) {
        Method method = findGetter(object, name);
        if (method == null) {
            try {
                Field field = object.getClass().getField(name);
                return field.get(object);
            } catch (Exception e) {
                for (Method m : object.getClass().getMethods()) {
                    if (m.getName().equals(name)) {
                        JavaObject jo = new JavaObject(object);
                        return jo.getMethod(name);
                    }
                }
                throw JsErrorException.typeError("no instance property: " + name);
            }
        }
        try {
            return method.invoke(object, EMPTY);
        } catch (Exception e) {
            throw JsErrorException.typeError("cannot get ." + name + " (on " + jsTypeName(object.getClass()) + ")");
        }
    }

    static void set(Object object, String name, Object value) {
        String setterName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
        Object[] args = new Object[]{value};
        try {
            Method method = findMethod(object.getClass(), setterName, args);
            if (method == null) {
                throw new RuntimeException("no such method: " + setterName);
            }
            method.invoke(object, args);
        } catch (Exception e) {
            throw JsErrorException.typeError("cannot set ." + name + " (on " + jsTypeName(object.getClass()) + ")");
        }
    }

    //==================================================================================================================
    //
    static final Object[] EMPTY = new Object[0];

    private static Class<?>[] paramTypes(Object[] args) {
        Class<?>[] paramTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            paramTypes[i] = arg == null ? Object.class : arg.getClass();
        }
        return paramTypes;
    }

    private static Object invoke(Object object, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        if (method.isVarArgs()) {
            Class<?>[] paramTypes = method.getParameterTypes();
            int fixedCount = paramTypes.length - 1;
            Class<?> varArgArrayType = paramTypes[fixedCount];
            // if caller already passed the correct array type, use standard invocation
            if (args.length == paramTypes.length && args[fixedCount] != null
                    && varArgArrayType.isAssignableFrom(args[fixedCount].getClass())) {
                return method.invoke(object, args);
            }
            // pack individual args into varargs array
            Object[] invokeArgs = new Object[paramTypes.length];
            for (int i = 0; i < fixedCount; i++) {
                invokeArgs[i] = args[i];
            }
            Class<?> componentType = varArgArrayType.getComponentType();
            int varArgCount = Math.max(0, args.length - fixedCount);
            Object varArgArray = Array.newInstance(componentType, varArgCount);
            for (int i = 0; i < varArgCount; i++) {
                Array.set(varArgArray, i, args[fixedCount + i]);
            }
            invokeArgs[fixedCount] = varArgArray;
            return method.invoke(object, invokeArgs);
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 0 && paramTypes[paramTypes.length - 1].equals(Object[].class)) {
            List<Object> argsList = new ArrayList<>();
            for (int i = 0; i < (paramTypes.length - 1); i++) {
                argsList.add(args[i]);
            }
            List<Object> lastArg = new ArrayList<>();
            for (int i = paramTypes.length - 1; i < args.length; i++) {
                lastArg.add(args[i]);
            }
            argsList.add(lastArg.toArray());
            return method.invoke(object, argsList.toArray());
        } else {
            return method.invoke(object, args);
        }
    }

    private static Method findGetter(Object object, String name) {
        String getterSuffix = name.substring(0, 1).toUpperCase() + name.substring(1);
        Method method = findMethod(object.getClass(), "get" + getterSuffix, EMPTY);
        if (method == null) {
            method = findMethod(object.getClass(), "is" + getterSuffix, EMPTY);
        }
        return method;
    }

    private static Constructor<?> findConstructor(Class<?> clazz, Object[] args) {
        try {
            return clazz.getConstructor(paramTypes(args));
        } catch (Exception e) {
            for (Constructor<?> constructor : clazz.getConstructors()) {
                Class<?>[] argTypes = constructor.getParameterTypes();
                if (match(argTypes, args)) {
                    return constructor;
                }
            }
        }
        throw JsErrorException.typeError(jsTypeName(clazz) + " is not a constructor");
    }

    private static Method findMethod(Class<?> clazz, String name, Object[] args) {
        Method method = findMethodDirect(clazz, name, args);
        if (method == null) {
            return null;
        }
        // Check if the method's declaring class is accessible (public and exported)
        // If not, find the same method on an accessible interface or superclass
        // This handles internal JDK classes like ImmutableCollections$ListN
        Class<?> declaringClass = method.getDeclaringClass();
        if (!Modifier.isPublic(declaringClass.getModifiers()) || isModuleRestricted(declaringClass)) {
            Method accessibleMethod = findAccessibleMethod(clazz, name, method.getParameterTypes());
            if (accessibleMethod != null) {
                return accessibleMethod;
            }
        }
        return method;
    }

    private static Method findMethodDirect(Class<?> clazz, String name, Object[] args) {
        try {
            return clazz.getMethod(name, paramTypes(args));
        } catch (Exception e) {
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(name)) {
                    Class<?>[] argTypes = method.getParameterTypes();
                    if (match(argTypes, args)) {
                        return method;
                    }
                }
            }
            return null;
        }
    }

    private static boolean isModuleRestricted(Class<?> clazz) {
        // Check if the class is in a module that restricts reflective access
        // Internal JDK classes like ImmutableCollections$ListN are in java.base but not exported
        Module module = clazz.getModule();
        if (module == null || !module.isNamed()) {
            return false;
        }
        String packageName = clazz.getPackageName();
        // If the package is not exported to our module, it's restricted
        return !module.isExported(packageName, JavaUtils.class.getModule());
    }

    private static Method findAccessibleMethod(Class<?> clazz, String name, Class<?>[] paramTypes) {
        // First try interfaces (most common case: List, Map, Set, etc.)
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                Method method = iface.getMethod(name, paramTypes);
                if (Modifier.isPublic(iface.getModifiers())) {
                    return method;
                }
            } catch (NoSuchMethodException e) {
                // Continue to next interface
            }
        }
        // Then try superclass chain
        Class<?> superclass = clazz.getSuperclass();
        while (superclass != null && superclass != Object.class) {
            if (Modifier.isPublic(superclass.getModifiers()) && !isModuleRestricted(superclass)) {
                try {
                    return superclass.getMethod(name, paramTypes);
                } catch (NoSuchMethodException e) {
                    // Continue to parent
                }
            }
            // Also check interfaces of superclass
            for (Class<?> iface : superclass.getInterfaces()) {
                try {
                    Method method = iface.getMethod(name, paramTypes);
                    if (Modifier.isPublic(iface.getModifiers())) {
                        return method;
                    }
                } catch (NoSuchMethodException e) {
                    // Continue
                }
            }
            superclass = superclass.getSuperclass();
        }
        return null;
    }

    private static boolean matchSingle(Class<?> type, Object arg) {
        if (arg == null) {
            return true;
        }
        if (type.equals(int.class)) return arg instanceof Integer;
        if (type.equals(double.class)) return arg instanceof Number;
        if (type.equals(long.class)) return arg instanceof Integer || arg instanceof Long;
        if (type.equals(boolean.class)) return arg instanceof Boolean;
        if (type.equals(byte.class)) return arg instanceof Byte;
        if (type.equals(char.class)) return arg instanceof Character;
        if (type.equals(float.class)) return arg instanceof Number;
        if (type.equals(short.class)) return arg instanceof Short;
        return type.isAssignableFrom(arg.getClass());
    }

    private static boolean match(Class<?>[] types, Object[] args) {
        boolean lastIsArray = types.length > 0 && types[types.length - 1].isArray();
        for (int i = 0; i < types.length; i++) {
            if (i >= args.length) {
                // allow zero varargs elements
                return lastIsArray && i == types.length - 1;
            }
            Object arg = args[i];
            Class<?> argType = types[i];
            if (argType.isArray()) {
                if (arg instanceof List<?> list) {
                    // convert list to array of correct type
                    Class<?> arrayType = argType.getComponentType();
                    int count = list.size();
                    Object result = Array.newInstance(arrayType, count);
                    for (int j = 0; j < count; j++) {
                        Array.set(result, j, list.get(j));
                    }
                    args[i] = result;
                } else if (arg != null) {
                    if (argType.isAssignableFrom(arg.getClass())) {
                        // already the correct array type
                    } else if (i == types.length - 1 && matchSingle(argType.getComponentType(), arg)) {
                        // varargs: individual element matching component type
                    } else {
                        return false;
                    }
                }
                if (i == (types.length - 1)) { // var args
                    // validate any extra args match the component type
                    Class<?> componentType = argType.getComponentType();
                    for (int k = i + 1; k < args.length; k++) {
                        if (!matchSingle(componentType, args[k])) {
                            return false;
                        }
                    }
                    return true;
                }
                continue;
            }
            if (!matchSingle(argType, arg)) {
                return false;
            }
        }
        return types.length == args.length;
    }

    /**
     * Deliberately thin Java-bean → Map conversion. Used by
     * {@link JavaObject#toMap()} when JS code does {@code Object.keys(bean)}
     * or {@code JSON.stringify(bean)} against a host object.
     *
     * <p>Pass-through for the JSON-shape types (Map, List/Array, String,
     * Number, Boolean, null). For anything else, a shallow public-getter +
     * public-field walk produces a {@link LinkedHashMap}. This is enough for
     * debugging/inspection of plain POJOs; it does NOT replicate the json-smart
     * ASM bean unpacker's full surface (Lombok beans, JPA entities with
     * side-effecting getters, JDK temporals, Optional, Enum/BigDecimal coercion,
     * {@code byte[]} → base64, etc.).
     *
     * <p>Consumers needing the rich surface (e.g. karate-core, which still
     * depends on json-smart) should convert Java objects to Map/List at the
     * bind boundary before they reach karate-js, rather than expanding this
     * stub.
     */
    @SuppressWarnings("unchecked")
    static Object toMap(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) object;
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(k, toMap(v)));
            return result;
        }
        if (object instanceof List) {
            List<Object> list = (List<Object>) object;
            List<Object> result = new ArrayList<>(list.size());
            for (Object v : list) {
                result.add(toMap(v));
            }
            return result;
        }
        if (object.getClass().isArray()) {
            int n = Array.getLength(object);
            List<Object> result = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                result.add(toMap(Array.get(object, i)));
            }
            return result;
        }
        if (object instanceof String || object instanceof Number || object instanceof Boolean
                || object instanceof Character) {
            return object;
        }
        return beanToMap(object);
    }

    private static Map<String, Object> beanToMap(Object object) {
        Map<String, Object> result = new LinkedHashMap<>();
        Class<?> clazz = object.getClass();
        // public getters (getXxx / isXxx — exclude getClass)
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (Modifier.isStatic(m.getModifiers())) continue;
            if (m.getDeclaringClass() == Object.class) continue;
            String name = m.getName();
            String prop;
            if (name.startsWith("get") && name.length() > 3 && !"getClass".equals(name)) {
                prop = decapitalize(name.substring(3));
            } else if (name.startsWith("is") && name.length() > 2
                    && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                prop = decapitalize(name.substring(2));
            } else {
                continue;
            }
            try {
                Object value = m.invoke(object);
                result.put(prop, value == object ? null : toMap(value));
            } catch (Exception e) {
                // skip getters that fail — keep the walk best-effort
            }
        }
        // public fields (e.g. final public fields not covered by a getter)
        for (Field f : clazz.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            String name = f.getName();
            if (result.containsKey(name)) continue;
            try {
                Object value = f.get(object);
                result.put(name, value == object ? null : toMap(value));
            } catch (Exception e) {
                // skip
            }
        }
        return result;
    }

    private static String decapitalize(String s) {
        if (s.isEmpty()) return s;
        // matches java.beans.Introspector.decapitalize: leave "URL" / "FOO" alone
        if (s.length() > 1 && Character.isUpperCase(s.charAt(1)) && Character.isUpperCase(s.charAt(0))) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    static Object convertIfArray(Object o) {
        if (o != null && o.getClass().isArray()) {
            List<Object> list = new ArrayList<>();
            int count = Array.getLength(o);
            for (int i = 0; i < count; i++) {
                list.add(Array.get(o, i));
            }
            return list;
        } else {
            return o;
        }
    }

}
