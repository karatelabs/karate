/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Table {

    private final List<List<String>> rows;
    private final Map<String, Integer> keyColumns;
    private final List<Integer> lineNumbers;
    private final String dynamicExpression;

    public String getDynamicExpression() {
        return dynamicExpression;
    }            

    public boolean isDynamic() {
        return dynamicExpression != null;
    }        

    public List<String> getKeys() {
        return rows.get(0);
    }

    public int getLineNumberForRow(int i) {
        return lineNumbers.get(i);
    }

    public Table replace(String token, String value) {
        int rowCount = rows.size();
        List<String> keys = rows.get(0);
        int colCount = keys.size();
        List<List<String>> list = new ArrayList(rowCount);
        list.add(keys); // header row
        for (int i = 1; i < rowCount; i++) { // don't include header row    
            List<String> row = rows.get(i);
            List<String> replaced = new ArrayList(colCount);
            list.add(replaced);
            for (int j = 0; j < colCount; j++) {
                String text = row.get(j).replace(token, value);
                replaced.add(text);
            }
        }
        return new Table(list, lineNumbers);
    }

    public String getValue(String key, int row) {
        Integer col = keyColumns.get(key);
        if (col == null) {
            return null;
        }
        return rows.get(row).get(col);
    }

    public Table(List<List<String>> rows, List<Integer> lineNumbers) {
        this.rows = rows;
        this.lineNumbers = lineNumbers;
        List<String> keys = rows.get(0);
        int colCount = keys.size();
        keyColumns = new LinkedHashMap(colCount);
        for (int i = 0; i < colCount; i++) {
            keyColumns.put(keys.get(i), i);
        }
        if (colCount == 1 && rows.size() == 1) {
            dynamicExpression = keys.get(0);
        } else {
            dynamicExpression = null;
        }
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public List<Map<String, String>> getRowsAsMaps() {
        List<String> keys = rows.get(0);
        int colCount = keys.size();
        int rowCount = rows.size();
        List<Map<String, String>> list = new ArrayList(rowCount - 1);
        for (int i = 1; i < rowCount; i++) { // don't include header row    
            Map<String, String> map = new LinkedHashMap(colCount);
            list.add(map);
            for (int j = 0; j < colCount; j++) {
                map.put(keys.get(j), rows.get(i).get(j));
            }
        }
        return list;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        for (List<String> row : rows) {
        	sb.append('|').append('\t');
            for (String s : row) {
                sb.append(s).append('\t').append('|');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

}
