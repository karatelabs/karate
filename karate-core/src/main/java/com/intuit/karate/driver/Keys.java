/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.driver;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Keys {

    private final Driver driver;

    public Keys(Driver driver) {
        this.driver = driver;
    }

    public static Integer code(char c) {
        return CODES.get(c);
    }
    
    public static String keyValue(char c) {
        return VALUES.get(c);
    }
 
    public static boolean isNormal(char c) {
        return c < NULL;
    }
    
    public static boolean isModifier(char c) {
        switch(c) {
            case CONTROL:
            case ALT:
            case SHIFT:
            case META:
                return true;
            default:
                return false;
        }
    }

    public static String keyIdentifier(char c) {
        return "\\u" + Integer.toHexString(c | 0x10000).substring(1);
    }

    public static final char NULL = '\uE000';
    public static final char CANCEL = '\uE001';
    public static final char HELP = '\uE002';
    public static final char BACK_SPACE = '\uE003';
    public static final char TAB = '\uE004';
    public static final char CLEAR = '\uE005';
    public static final char RETURN = '\uE006';
    public static final char ENTER = '\uE007';
    public static final char SHIFT = '\uE008';
    public static final char CONTROL = '\uE009';
    public static final char ALT = '\uE00A';
    public static final char PAUSE = '\uE00B';
    public static final char ESCAPE = '\uE00C';
    public static final char SPACE = '\uE00D';
    public static final char PAGE_UP = '\uE00E';
    public static final char PAGE_DOWN = '\uE00F';
    public static final char END = '\uE010';
    public static final char HOME = '\uE011';
    public static final char LEFT = '\uE012';
    public static final char UP = '\uE013';
    public static final char RIGHT = '\uE014';
    public static final char DOWN = '\uE015';
    public static final char INSERT = '\uE016';
    public static final char DELETE = '\uE017';
    public static final char SEMICOLON = '\uE018';
    public static final char EQUALS = '\uE019';

    // numpad keys
    public static final char NUMPAD0 = '\uE01A';
    public static final char NUMPAD1 = '\uE01B';
    public static final char NUMPAD2 = '\uE01C';
    public static final char NUMPAD3 = '\uE01D';
    public static final char NUMPAD4 = '\uE01E';
    public static final char NUMPAD5 = '\uE01F';
    public static final char NUMPAD6 = '\uE020';
    public static final char NUMPAD7 = '\uE021';
    public static final char NUMPAD8 = '\uE022';
    public static final char NUMPAD9 = '\uE023';
    public static final char MULTIPLY = '\uE024';
    public static final char ADD = '\uE025';
    public static final char SEPARATOR = '\uE026';
    public static final char SUBTRACT = '\uE027';
    public static final char DECIMAL = '\uE028';
    public static final char DIVIDE = '\uE029';

    // function keys
    public static final char F1 = '\uE031';
    public static final char F2 = '\uE032';
    public static final char F3 = '\uE033';
    public static final char F4 = '\uE034';
    public static final char F5 = '\uE035';
    public static final char F6 = '\uE036';
    public static final char F7 = '\uE037';
    public static final char F8 = '\uE038';
    public static final char F9 = '\uE039';
    public static final char F10 = '\uE03A';
    public static final char F11 = '\uE03B';
    public static final char F12 = '\uE03C';
    public static final char META = '\uE03D';

    private static final Map<Character, Integer> CODES = new HashMap();
    private static final Map<Character, String> VALUES = new HashMap();
    
    private static void put(char c, int code, String value) {
        CODES.put(c, code);
        VALUES.put(c, value);
    }
    
    public static final int CODE_SHIFT = 16;
    public static final int CODE_CONTROL = 17;
    public static final int CODE_ALT = 18;
    public static final int CODE_META = 91; // left command key on mac, right is 93

    static {
        put(CANCEL, 3, "Cancel");
        put(BACK_SPACE, 8, "Backspace");
        put(TAB, 9, "Tab");
        put(CLEAR, 12, "Clear");
        put(NULL, 12, "Clear"); // same as clear
        put(RETURN, 13, "Enter"); // same as enter
        put(ENTER, 13, "Enter");
        put(SHIFT, CODE_SHIFT, "Shift");
        put(CONTROL, CODE_CONTROL, "Control");
        put(ALT, CODE_ALT, "Alt");
        put(PAUSE, 19, "Pause");
        put(ESCAPE, 27, "Escape");
        put(SPACE, 32, " ");
        put(PAGE_UP, 33, "PageUp");
        put(PAGE_DOWN, 34, "PageDown");
        put(END, 35, "End");
        put(HOME, 36, "Home");
        put(LEFT, 37, "ArrowLeft");
        put(UP, 38, "ArrowUp");
        put(RIGHT, 39, "ArrowRight");
        put(DOWN, 40, "ArrowDown");
        put(NUMPAD0, 96, "0");
        put(NUMPAD1, 97, "1");
        put(NUMPAD2, 98, "2");
        put(NUMPAD3, 99, "3");
        put(NUMPAD4, 100, "4");
        put(NUMPAD5, 101, "5");
        put(NUMPAD6, 102, "6");
        put(NUMPAD7, 103, "7");
        put(NUMPAD8, 104, "8");
        put(NUMPAD9, 105, "9");
        put(MULTIPLY, 106, "Multiply");
        put(ADD, 107, "Add");
        put(SEPARATOR, 108, "Separator");
        put(SUBTRACT, 109, "Subtract");
        put(DECIMAL, 110, "Decimal");
        put(DIVIDE, 111, "Divide");
        put(F1, 112, "F1");
        put(F2, 113, "F2");
        put(F3, 114, "F3");
        put(F4, 115, "F4");
        put(F5, 116, "F5");
        put(F6, 117, "F6");
        put(F7, 118, "F7");
        put(F8, 119, "F8");
        put(F9, 120, "F9");
        put(F10, 121, "F10");
        put(F11, 122, "F11");
        put(F12, 123, "F12");
        put(DELETE, 127, "Delete");
        put(INSERT, 155, "Insert");
        put(HELP, 156, "Help");
        put(META, CODE_META, "Meta");
        //======================================================================
        CODES.put(' ', 32);
        CODES.put(',', 44);
        CODES.put('-', 45);
        CODES.put('.', 46);
        CODES.put('/', 47);
        CODES.put('0', 48);
        CODES.put('1', 49);
        CODES.put('2', 50);
        CODES.put('3', 51);
        CODES.put('4', 52);
        CODES.put('5', 53);
        CODES.put('6', 54);
        CODES.put('7', 55);
        CODES.put('8', 56);
        CODES.put('9', 57);
        CODES.put(';', 59);
        CODES.put('=', 61);
        CODES.put('A', 65);
        CODES.put('B', 66);
        CODES.put('C', 67);
        CODES.put('D', 68);
        CODES.put('E', 69);
        CODES.put('F', 70);
        CODES.put('G', 71);
        CODES.put('H', 72);
        CODES.put('I', 73);
        CODES.put('J', 74);
        CODES.put('K', 75);
        CODES.put('L', 76);
        CODES.put('M', 77);
        CODES.put('N', 78);
        CODES.put('O', 79);
        CODES.put('P', 80);
        CODES.put('Q', 81);
        CODES.put('R', 82);
        CODES.put('S', 83);
        CODES.put('T', 84);
        CODES.put('U', 85);
        CODES.put('V', 86);
        CODES.put('W', 87);
        CODES.put('X', 88);
        CODES.put('Y', 89);
        CODES.put('Z', 90);
        CODES.put('[', 91);
        CODES.put('\\', 92);
        CODES.put(']', 93);
        CODES.put('a', 97);
        CODES.put('b', 98);
        CODES.put('c', 99);
        CODES.put('d', 100);
        CODES.put('e', 101);
        CODES.put('f', 102);
        CODES.put('g', 103);
        CODES.put('h', 104);
        CODES.put('i', 105);
        CODES.put('j', 106);
        CODES.put('k', 107);
        CODES.put('l', 108);
        CODES.put('m', 109);
        CODES.put('n', 110);
        CODES.put('o', 111);
        CODES.put('p', 112);
        CODES.put('q', 113);
        CODES.put('r', 114);
        CODES.put('s', 115);
        CODES.put('t', 116);
        CODES.put('u', 117);
        CODES.put('v', 118);
        CODES.put('w', 119);
        CODES.put('x', 120);
        CODES.put('y', 121);
        CODES.put('z', 122);
        CODES.put('&', 150);
        CODES.put('*', 151);
        CODES.put('"', 152);
        CODES.put('<', 153);
        CODES.put('>', 160);
        CODES.put('{', 161);
        CODES.put('}', 162);
        CODES.put('`', 192);
        CODES.put('\'', 222);
        CODES.put('@', 512);
        CODES.put(':', 513);
        CODES.put('$', 515);
        CODES.put('!', 517);
        CODES.put('(', 519);
        CODES.put('#', 520);
        CODES.put('+', 521);
        CODES.put(')', 522);
        CODES.put('_', 523);
        
    }

}
