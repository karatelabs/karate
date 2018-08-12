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
import java.util.Set;

/**
 *
 * @author pthomas3
 */
public class Table {
    
    private final List<List<String>> rows;
    private final Map<String,Integer> keyColumns;  
    private final List<Integer> lineNumbers;
    
    public Set<String> getKeys() {
        return keyColumns.keySet();
    }
    
    public int getLineNumberForRow(int i) {
        return lineNumbers.get(i);
    }
    
    public Table replace(String token, String value) {        
        int rowCount = rows.size();
        int colCount = getKeys().size();
        List<List<String>> list = new ArrayList(rowCount);
        list.add(new ArrayList(getKeys())); // header row
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
        List<String> heads = rows.get(0);
        int columnCount = heads.size();
        keyColumns = new LinkedHashMap(columnCount);
        for (int i = 0; i < columnCount; i++) {
            String text = heads.get(i);
            keyColumns.put(text, i);
        }
    }

    public List<List<String>> getRows() {
        return rows;
    }
    
    @Override
    public String toString() {
        return rows.toString();
    }        
    
}
