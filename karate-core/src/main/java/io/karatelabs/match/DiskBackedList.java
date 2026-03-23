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
package io.karatelabs.match;

import net.minidev.json.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

/**
 * A disk-backed list implementation for handling large collections without
 * exhausting memory. Items are serialized as JSON lines to a temporary file.
 */
public class DiskBackedList implements LargeValueStore {

    private static final Logger logger = LoggerFactory.getLogger(DiskBackedList.class);

    private final File tempFile;
    private final int size;
    private final List<Long> lineOffsets;
    private RandomAccessFile raf;
    private boolean closed = false;

    private DiskBackedList(File tempFile, int size, List<Long> lineOffsets) {
        this.tempFile = tempFile;
        this.size = size;
        this.lineOffsets = lineOffsets;
    }

    /**
     * Creates a DiskBackedList from an iterable source.
     *
     * @param source the source iterable to store
     * @return a new DiskBackedList containing all items from the source
     * @throws IOException if an I/O error occurs
     */
    public static DiskBackedList create(Iterable<?> source) throws IOException {
        File tempFile = File.createTempFile("karate-match-", ".jsonl");
        tempFile.deleteOnExit();
        List<Long> offsets = new ArrayList<>();
        int count = 0;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            long offset = 0;
            for (Object item : source) {
                offsets.add(offset);
                String json = serializeItem(item);
                byte[] bytes = (json + "\n").getBytes(StandardCharsets.UTF_8);
                bos.write(bytes);
                offset += bytes.length;
                count++;
            }
        }
        logger.debug("created disk-backed list with {} items in {}", count, tempFile);
        return new DiskBackedList(tempFile, count, offsets);
    }

    /**
     * Estimates the memory size of an object in bytes.
     * This is a rough estimate for determining whether to use disk storage.
     *
     * @param obj the object to estimate
     * @return estimated size in bytes
     */
    public static long estimateSize(Object obj) {
        if (obj == null) {
            return 8;
        }
        if (obj instanceof String s) {
            return 40 + s.length() * 2L;
        }
        if (obj instanceof Number) {
            return 24;
        }
        if (obj instanceof Boolean) {
            return 16;
        }
        if (obj instanceof byte[] bytes) {
            return 16 + bytes.length;
        }
        if (obj instanceof List<?> list) {
            long size = 40;
            for (Object item : list) {
                size += 8 + estimateSize(item);
            }
            return size;
        }
        if (obj instanceof java.util.Map<?, ?> map) {
            long size = 48;
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                size += 32 + estimateSize(entry.getKey()) + estimateSize(entry.getValue());
            }
            return size;
        }
        return 100;
    }

    /**
     * Estimates the total memory size of a list using random sampling.
     * For lists with more than SAMPLE_SIZE items, samples 5 random items and extrapolates.
     *
     * @param list the list to estimate
     * @return estimated size in bytes
     */
    public static long estimateCollectionSize(List<?> list) {
        final int SAMPLE_SIZE = 5;
        int size = list.size();
        if (size == 0) {
            return 40;
        }
        if (size <= SAMPLE_SIZE) {
            // Small list - check all items
            long total = 40;
            for (Object item : list) {
                total += 8 + estimateSize(item);
            }
            return total;
        }
        // Large list - random sample of 5 items
        long sampleTotal = 0;
        Random random = new Random();
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            int idx = random.nextInt(size);
            sampleTotal += 8 + estimateSize(list.get(idx));
        }
        long avgItemSize = sampleTotal / SAMPLE_SIZE;
        return 40 + (avgItemSize * size);
    }

    private static String serializeItem(Object item) {
        if (item == null) {
            return "null";
        }
        return JSONValue.toJSONString(item);
    }

    private static Object deserializeItem(String json) {
        if (json == null || json.isEmpty() || "null".equals(json)) {
            return null;
        }
        return JSONValue.parse(json);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Object get(int index) {
        if (closed) {
            throw new IllegalStateException("store has been closed");
        }
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
        }
        try {
            if (raf == null) {
                raf = new RandomAccessFile(tempFile, "r");
            }
            long offset = lineOffsets.get(index);
            raf.seek(offset);
            String line = raf.readLine();
            return deserializeItem(line);
        } catch (IOException e) {
            throw new RuntimeException("failed to read item at index " + index, e);
        }
    }

    @Override
    public Iterator<Object> iterator() {
        if (closed) {
            throw new IllegalStateException("store has been closed");
        }
        return new DiskBackedIterator();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException e) {
                logger.warn("failed to close RandomAccessFile: {}", e.getMessage());
            }
            raf = null;
        }
        if (tempFile.exists()) {
            if (!tempFile.delete()) {
                logger.warn("failed to delete temp file: {}", tempFile);
            }
        }
    }

    private class DiskBackedIterator implements Iterator<Object> {
        private final BufferedReader reader;
        private int currentIndex = 0;
        private String nextLine = null;
        private boolean hasNextCalled = false;

        DiskBackedIterator() {
            try {
                reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(tempFile), StandardCharsets.UTF_8));
            } catch (FileNotFoundException e) {
                throw new RuntimeException("temp file not found", e);
            }
        }

        @Override
        public boolean hasNext() {
            if (hasNextCalled) {
                return nextLine != null;
            }
            hasNextCalled = true;
            if (currentIndex >= size) {
                closeReader();
                return false;
            }
            try {
                nextLine = reader.readLine();
                if (nextLine == null) {
                    closeReader();
                    return false;
                }
                return true;
            } catch (IOException e) {
                closeReader();
                throw new RuntimeException("failed to read next line", e);
            }
        }

        @Override
        public Object next() {
            if (!hasNextCalled) {
                hasNext();
            }
            if (nextLine == null) {
                throw new NoSuchElementException();
            }
            hasNextCalled = false;
            currentIndex++;
            return deserializeItem(nextLine);
        }

        private void closeReader() {
            try {
                reader.close();
            } catch (IOException e) {
                logger.warn("failed to close reader: {}", e.getMessage());
            }
        }
    }

}
