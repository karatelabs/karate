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

import com.intuit.karate.Script;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Table {

    private static final Logger logger = LoggerFactory.getLogger(Table.class);

    enum ColumnType {
        STRING,
        EVALUATED
    }

    class Column {

        final String key;
        final int index;
        final ColumnType type;

        Column(String key, int index, ColumnType type) {
            this.key = key;
            this.index = index;
            this.type = type;
        }

    }

    private final List<List<String>> rows;
    private final Map<String, Column> colMap;
    private final List<Column> cols;
    private final List<Integer> lineNumbers;
    private final String dynamicExpression;

    public String getDynamicExpression() {
        return dynamicExpression;
    }

    public boolean isDynamic() {
        return dynamicExpression != null;
    }

    public Collection<String> getKeys() {
        return colMap.keySet();
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

    public String getValueAsString(String key, int row) {
        Column col = colMap.get(key);
        if (col == null) {
            return null;
        }
        return rows.get(row).get(col.index);
    }

    public Table(List<List<String>> rows, List<Integer> lineNumbers) {
        this.rows = rows;
        this.lineNumbers = lineNumbers;
        List<String> keys = rows.get(0);
        int colCount = keys.size();
        colMap = new LinkedHashMap(colCount);
        for (int i = 0; i < colCount; i++) {
            String key = keys.get(i);
            ColumnType type;
            if (key.endsWith("!")) {
                key = key.substring(0, key.length() - 1);
                type = ColumnType.EVALUATED;
            } else {
                type = ColumnType.STRING;
            }
            colMap.put(key, new Column(key, i, type));
        }
        if (colCount == 1 && rows.size() == 1) {
            dynamicExpression = keys.get(0);
        } else {
            dynamicExpression = null;
        }
        cols = new ArrayList(colMap.values()); // just to be able to call cols.get(index)
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public List<Map<String, String>> getRowsAsMaps() {
        int rowCount = rows.size();
        List<Map<String, String>> list = new ArrayList(rowCount - 1);
        for (int i = 1; i < rowCount; i++) { // don't include header row    
            Map<String, String> map = new LinkedHashMap(cols.size());
            list.add(map);
            List<String> row = rows.get(i);
            for (Column col : cols) {
                map.put(col.key, row.get(col.index));
            }
        }
        return list;
    }

    private static Object convert(String raw, Column col) {
        try {
            switch (col.type) {
                case EVALUATED:
                    if (Script.isJson(raw)) {
                        raw = '(' + raw + ')';
                    }
                    return ScriptBindings.eval(raw, null).getValue();
                default:
                    if (StringUtils.isBlank(raw)) {
                        return null;
                    } else {
                        return raw;
                    }
            }
        } catch (Exception e) {
            if (logger.isTraceEnabled()) {
                logger.trace("type conversion failed for column: {}, type: {} - {}", col.key, col.type, e.getMessage());
            }
            return raw;
        }
    }

    public Map<String, Object> getExampleData(int exampleIndex) {
        List<String> row = rows.get(exampleIndex + 1);
        Map<String, Object> map = new LinkedHashMap(cols.size());
        for (Column col : cols) {
            String raw = row.get(col.index);
            map.put(col.key, convert(raw, col));
        }
        return map;
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
