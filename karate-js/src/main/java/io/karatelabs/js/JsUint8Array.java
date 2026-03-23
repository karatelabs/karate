/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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

import java.util.*;

/**
 * JavaScript Uint8Array implementation backed by a byte[].
 * Implements List<Object> for Java interop with auto-unwrapping.
 */
non-sealed class JsUint8Array extends JsArray implements JsBinaryValue {

    private final byte[] buffer;

    JsUint8Array(int length) {
        this.buffer = new byte[length];
    }

    JsUint8Array(byte[] bytes) {
        this.buffer = bytes.clone();
    }

    // =================================================================================================
    // JS internal access - overrides JsArray to work with byte[] buffer
    // =================================================================================================

    @Override
    public Object getElement(int index) {
        if (index >= 0 && index < buffer.length) {
            return buffer[index] & 0xFF; // return as unsigned
        }
        return Terms.UNDEFINED;
    }

    @Override
    public void setElement(int index, Object value) {
        if (index >= 0 && index < buffer.length && value instanceof Number v) {
            buffer[index] = (byte) (v.intValue() & 0xFF);
        }
    }

    @Override
    public Iterable<KeyValue> jsEntries() {
        return () -> new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < buffer.length;
            }

            @Override
            public KeyValue next() {
                int i = index++;
                return new KeyValue(JsUint8Array.this, i, i + "", buffer[i] & 0xFF);
            }
        };
    }

    @Override
    public List<Object> toList() {
        ArrayList<Object> list = new ArrayList<>(buffer.length);
        for (KeyValue kv : jsEntries()) {
            list.add(kv.value());
        }
        return list;
    }

    // =================================================================================================
    // List<Object> implementation - overrides to work with byte[] buffer
    // =================================================================================================

    @Override
    public int size() {
        return buffer.length;
    }

    @Override
    public boolean isEmpty() {
        return buffer.length == 0;
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof Number n) {
            int val = n.intValue() & 0xFF;
            for (byte b : buffer) {
                if ((b & 0xFF) == val) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Iterator<Object> iterator() {
        return new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < buffer.length;
            }

            @Override
            public Object next() {
                return buffer[index++] & 0xFF;
            }
        };
    }

    @Override
    public Object[] toArray() {
        Object[] result = new Object[buffer.length];
        for (int i = 0; i < buffer.length; i++) {
            result[i] = buffer[i] & 0xFF;
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        int size = buffer.length;
        T[] result = a.length >= size ? a : (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
        for (int i = 0; i < size; i++) {
            result[i] = (T) (Integer) (buffer[i] & 0xFF);
        }
        if (a.length > size) {
            result[size] = null;
        }
        return result;
    }

    @Override
    public Object get(int index) {
        if (index < 0 || index >= buffer.length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + buffer.length);
        }
        return buffer[index] & 0xFF;
    }

    @Override
    public Object set(int index, Object element) {
        if (index < 0 || index >= buffer.length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + buffer.length);
        }
        int previous = buffer[index] & 0xFF;
        if (element instanceof Number n) {
            buffer[index] = (byte) (n.intValue() & 0xFF);
        }
        return previous;
    }

    @Override
    public int indexOf(Object o) {
        if (o instanceof Number n) {
            int val = n.intValue() & 0xFF;
            for (int i = 0; i < buffer.length; i++) {
                if ((buffer[i] & 0xFF) == val) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        if (o instanceof Number n) {
            int val = n.intValue() & 0xFF;
            for (int i = buffer.length - 1; i >= 0; i--) {
                if ((buffer[i] & 0xFF) == val) {
                    return i;
                }
            }
        }
        return -1;
    }

    // Fixed-size array - these operations throw UnsupportedOperationException
    @Override
    public boolean add(Object o) {
        throw new UnsupportedOperationException("Uint8Array is fixed-size");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("Uint8Array is fixed-size");
    }

    @Override
    public void add(int index, Object element) {
        throw new UnsupportedOperationException("Uint8Array is fixed-size");
    }

    @Override
    public Object remove(int index) {
        throw new UnsupportedOperationException("Uint8Array is fixed-size");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Uint8Array is fixed-size");
    }

    @Override
    public boolean addAll(Collection<? extends Object> c) {
        throw new UnsupportedOperationException("Uint8Array is fixed-size");
    }

    @Override
    public boolean addAll(int index, Collection<? extends Object> c) {
        throw new UnsupportedOperationException("Uint8Array is fixed-size");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("Uint8Array is fixed-size");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("Uint8Array is fixed-size");
    }

    @Override
    public ListIterator<Object> listIterator() {
        return new Uint8ListIterator(0);
    }

    @Override
    public ListIterator<Object> listIterator(int index) {
        return new Uint8ListIterator(index);
    }

    @Override
    public List<Object> subList(int fromIndex, int toIndex) {
        // Return a copy as a new list since we can't create a view of fixed-size array
        List<Object> result = new ArrayList<>(toIndex - fromIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            result.add(buffer[i] & 0xFF);
        }
        return result;
    }

    private class Uint8ListIterator implements ListIterator<Object> {
        private int index;

        Uint8ListIterator(int index) {
            this.index = index;
        }

        @Override public boolean hasNext() { return index < buffer.length; }
        @Override public Object next() { return buffer[index++] & 0xFF; }
        @Override public boolean hasPrevious() { return index > 0; }
        @Override public Object previous() { return buffer[--index] & 0xFF; }
        @Override public int nextIndex() { return index; }
        @Override public int previousIndex() { return index - 1; }
        @Override public void remove() { throw new UnsupportedOperationException("Uint8Array is fixed-size"); }
        @Override public void set(Object o) {
            if (o instanceof Number n) {
                buffer[index - 1] = (byte) (n.intValue() & 0xFF);
            }
        }
        @Override public void add(Object o) { throw new UnsupportedOperationException("Uint8Array is fixed-size"); }
    }

    // =================================================================================================
    // ObjectLike overrides
    // =================================================================================================

    @Override
    public Object getMember(String name) {
        // Uint8Array specific: length from buffer (must check BEFORE super to avoid returning 0 from empty list)
        if ("length".equals(name)) {
            return buffer.length;
        }
        // Delegate to parent for prototype chain (array methods)
        return super.getMember(name);
    }

    @Override
    public Object getJavaValue() {
        return buffer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object call(Context context, Object[] args) {
        if (args.length == 0) {
            return new byte[0];
        }
        Object arg = args[0];
        if (arg instanceof Number n) {
            return new byte[n.intValue()];
        }
        if (arg instanceof List) {
            List<Object> items = (List<Object>) arg;
            byte[] bytes = new byte[items.size()];
            for (int i = 0; i < items.size(); i++) {
                Object val = items.get(i);
                if (val instanceof Number n) {
                    bytes[i] = (byte) (n.intValue() & 0xFF);
                } else {
                    bytes[i] = 0;
                }
            }
            return bytes;
        }
        return new byte[0];
    }

}
