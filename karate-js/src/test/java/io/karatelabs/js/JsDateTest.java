package io.karatelabs.js;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class JsDateTest extends EvalBase {

    @BeforeAll
    static void forceUTC() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Test
    void testDev() {

    }

    @Test
    void testDateCopyConstructor() {
        // Test that new Date(dateObject) creates a copy with the same timestamp
        eval("var original = new Date('2024-01-01');"
                + "var copy = new Date(original);"
                + "var origTime = original.getTime();"
                + "var copyTime = copy.getTime();");

        // Copy should have the exact same timestamp as original
        assertEquals(get("origTime"), get("copyTime"),
            "new Date(dateObject) should create a copy with the same timestamp");
    }

    @Test
    void testDateCopyImmutability() {
        // Test that copying a Date object creates an independent copy
        // Bug report: modifying a copy should not affect the original
        eval("var original = new Date('2024-01-01');"
                + "var copy = new Date(original);"
                + "copy.setMonth(copy.getMonth() + 10);"
                + "var originalMonth = original.getMonth();"
                + "var originalYear = original.getFullYear();"
                + "var originalIso = original.toISOString();"
                + "var copyMonth = copy.getMonth();"
                + "var copyYear = copy.getFullYear();");

        // Original should still be January 2024 (unchanged)
        assertEquals(0, get("originalMonth"), "Original month should still be 0 (January)");
        assertEquals(2024, get("originalYear"), "Original year should still be 2024");
        assertTrue(get("originalIso").toString().startsWith("2024-01-01"),
            "Original date should still be 2024-01-01");

        // Copy should be November 2024 (original month 0 + 10 = month 10)
        assertEquals(10, get("copyMonth"), "Copy month should be 10 (November)");
        assertEquals(2024, get("copyYear"), "Copy year should be 2024");
    }

    @Test
    void testDateConstructor() {
        // Compare timestamps instead of objects (JsDate vs Date)
        assertEquals(JsDate.parse("2025-03-15").getTime(), eval("new Date(2025, 2, 15).getTime()"));
    }

    @Test
    void testDateCreation() {
        // Test date creation with year, month, day parameters
        eval("var date = new Date(2025, 2, 15); var month = date.getMonth(); var day = date.getDate(); var year = date.getFullYear();");
        assertInstanceOf(Object.class, get("date"));
        assertEquals(2, get("month")); // March is month 2 in JavaScript (0-indexed)
        assertEquals(15, get("day"));
        assertEquals(2025, get("year"));
        // Test date creation with full parameters
        eval("var dateWithTime = new Date(2025, 2, 15, 13, 45, 30, 500);"
                + " var hours = dateWithTime.getHours(); var minutes = dateWithTime.getMinutes();"
                + " var seconds = dateWithTime.getSeconds(); var ms = dateWithTime.getMilliseconds()");
        assertEquals(13, get("hours"));
        assertEquals(45, get("minutes"));
        assertEquals(30, get("seconds"));
        assertEquals(500, get("ms"));
    }

    @Test
    void testDateManipulation() {
        assertEquals(1741996800000L, eval("new Date(2025, 2, 15).getTime()"));
        // dates are mutable in js - verify setDate returns timestamp and modifies the date
        eval("var date = new Date(2025, 2, 15); var setResult = date.setDate(date.getDate() + 10); var finalTime = date.getTime();");
        assertEquals(1742860800000L, get("setResult"));
        assertEquals(JsDate.parse("2025-03-25").getTime(), get("finalTime"));
        assertEquals(25, eval("var date = new Date(2025, 2, 15); date.setDate(date.getDate() + 10); date.getDate()"));
    }

    @Test
    void testDateSetters() {
        eval("var date = new Date(2025, 2, 15);"
                + "date.setDate(date.getDate() + 10);"
                + "var newDay = date.getDate();"
                + "date.setMonth(date.getMonth() + 1);"
                + "var newMonth = date.getMonth();"
                + "date.setHours(23, 59, 59, 999);"
                + "var endDayHours = date.getHours();"
                + "var endDayMinutes = date.getMinutes();"
                + "var endDaySeconds = date.getSeconds();"
                + "var endDayMs = date.getMilliseconds();");
        assertEquals(25, get("newDay"));
        assertEquals(3, get("newMonth"));
        assertEquals(23, get("endDayHours"));
        assertEquals(59, get("endDayMinutes"));
        assertEquals(59, get("endDaySeconds"));
        assertEquals(999, get("endDayMs"));
    }

    @Test
    void testDateComparison() {
        eval("var date1 = new Date(2025, 0, 1); var date2 = new Date(2025, 0, 2); var isDate2Greater = date2 > date1");
        assertTrue((Boolean) get("isDate2Greater"));
        // Test same day, different time
        eval("var dateEarly = new Date(2025, 0, 1, 9, 0, 0); var dateLate = new Date(2025, 0, 1, 17, 0, 0); var isDateLateGreater = dateLate > dateEarly;");
        assertTrue((Boolean) get("isDateLateGreater"));
    }

    @Test
    void testDateOverflowHandling() {
        // Test that setDate handles overflow correctly (should roll to next month)
        eval("var date = new Date(2025, 0, 31);"
                + "date.setDate(32);"
                + "var month = date.getMonth();"
                + "var day = date.getDate();");
        // In standard JavaScript, setting date to 32 in January should roll to February 1st
        assertEquals(1, get("month")); // February (0-indexed)
        assertEquals(1, get("day"));

        // Test underflow
        eval("var date2 = new Date(2025, 2, 1);"
                + "date2.setDate(0);"
                + "var month2 = date2.getMonth();"
                + "var day2 = date2.getDate();");
        // Setting date to 0 should go to last day of previous month
        assertEquals(1, get("month2")); // February
        assertEquals(28, get("day2")); // 2025 is not a leap year

        // Test setMonth overflow
        eval("var date3 = new Date(2025, 11, 15);"
                + "date3.setMonth(12);"
                + "var year3 = date3.getFullYear();"
                + "var month3 = date3.getMonth();");
        assertEquals(2026, get("year3"));
        assertEquals(0, get("month3")); // January

        // Test setHours overflow
        eval("var date4 = new Date(2025, 0, 31, 23, 0, 0);"
                + "date4.setHours(25);"
                + "var day4 = date4.getDate();"
                + "var hour4 = date4.getHours();");
        assertEquals(1, get("day4")); // Next day
        assertEquals(1, get("hour4")); // 25 - 24 = 1
    }

    @Test
    void testDateArithmeticUsingTimestamp() {
        // Test the recommended workaround for date arithmetic using timestamps
        eval("var startDate = new Date(2025, 0, 31);"
                + "var msPerDay = 24 * 60 * 60 * 1000;"
                + "var startTime = startDate.getTime();"
                + "var nextDayTime = startTime + msPerDay;"
                + "var nextDay = new Date(nextDayTime);"
                + "var month = nextDay.getMonth();"
                + "var day = nextDay.getDate();");
        // This should correctly give us February 1st
        assertEquals(1, get("month")); // February (0-indexed)
        assertEquals(1, get("day"));

        // Test adding multiple days across month boundary
        eval("var date = new Date(2025, 1, 28);"
                + "var msPerDay = 24 * 60 * 60 * 1000;"
                + "var newTime = date.getTime() + (3 * msPerDay);"
                + "var newDate = new Date(newTime);"
                + "var newMonth = newDate.getMonth();"
                + "var newDay = newDate.getDate();");
        // February 28 + 3 days = March 3 (2025 is not a leap year)
        assertEquals(2, get("newMonth")); // March (0-indexed)
        assertEquals(3, get("newDay"));
    }

    @Test
    void testDateLoopIteration() {
        // Test iterating through dates (common pattern in business logic)
        eval("var startDate = new Date(2025, 1, 26);"
                + "var dates = [];"
                + "var msPerDay = 24 * 60 * 60 * 1000;"
                + "for (var i = 0; i < 5; i++) {"
                + "    var currentTime = startDate.getTime() + (i * msPerDay);"
                + "    var currentDate = new Date(currentTime);"
                + "    dates.push(currentDate.getDate());"
                + "}"
                + "var dateArray = dates.join(',');");
        // Should iterate from Feb 26 through March 2
        assertEquals("26,27,28,1,2", get("dateArray"));
    }

    @Test
    void testDateObject() {
        // Test date construction
        eval("var date = new Date()");
        assertInstanceOf(Object.class, get("date"));
        eval("var dateA = new Date(1609459200000); var timeA = dateA.getTime();");
        assertEquals(1609459200000L, get("timeA"));

        // Test static methods
        assertInstanceOf(Number.class, eval("Date.now()"));
        assertTrue((Long) eval("Date.now()") > 0);

        // Test parsing (exact value may vary by timezone)
        eval("var time = Date.parse('2021-01-01T00:00:00Z')");
        assertInstanceOf(Number.class, get("time"));

        eval("var fixedDate = new Date(1609459200000);" // 2021-01-01
                + "var time = fixedDate.getTime();"
                + "var year = fixedDate.getFullYear();"
                + "var month = fixedDate.getMonth();"
                + "var date = fixedDate.getDate();"
                + "var day = fixedDate.getDay();"
                + "var strDate = fixedDate.toString();"
                + "var isoDate = fixedDate.toISOString();");
        assertEquals(1609459200000L, get("time"));
        assertEquals(2021, get("year"));
        assertEquals(0, get("month"));
        assertEquals(1, get("date"));
        assertEquals(5, get("day"));
        assertNotNull(get("strDate"));
        assertNotNull(get("isoDate"));
        assertTrue(get("isoDate").toString().contains("2021-01-01"));
        eval("var originalDate = new Date(2020, 0, 1);"
                + "var newTimestamp = new Date(2022, 0, 1).getTime();"
                + "originalDate.setTime(newTimestamp);"
                + "var afterSetYear = originalDate.getFullYear();");
        assertEquals(2022, get("afterSetYear"));

        // ES6: Date() without 'new' returns a string representation of current time
        // Since it's always current time, we can only verify it's a string with expected format
        Object dateStr = eval("Date()");
        assertInstanceOf(String.class, dateStr);
        assertTrue(((String) dateStr).contains("202"), "Date string should contain year");

        // Test toString() format with a fixed timestamp (2021-01-01T00:00:00Z in UTC)
        // Browser-style format: "Fri Jan 01 2021 00:00:00 GMT+0000"
        assertEquals("Fri Jan 01 2021 00:00:00 GMT+0000", eval("new Date(1609459200000).toString()"));

        // Test ability to pass date object to a function that expects a timestamp
        String js = "function getTimestamp(time) {"
                + "    if (time && time.getTime) {"
                + "      return time.getTime();"
                + "    }"
                + "    return time;"
                + "};"
                + "var fixedDate = new Date(1609459200000);"
                + "var timestamp = getTimestamp(fixedDate)";
        eval(js);
        assertEquals(1609459200000L, get("timestamp"));

        // Test ability to re-use date objects
        js = "function getDate(time) {"
                + "    return new Date(time);"
                + "};"
                + "var fixedDate = getDate(1609459200000);"
                + "var fixedTime = fixedDate.getTime();";
        eval(js);
        assertEquals(1609459200000L, get("fixedTime"));
    }

    @Test
    void testToLocaleDateString() {
        // Basic call without arguments should not throw
        Object result = eval("new Date(2025, 0, 15).toLocaleDateString()");
        assertInstanceOf(String.class, result);
        assertFalse(((String) result).isEmpty());
    }

    @Test
    void testToLocaleDateStringWithLocale() {
        // With locale argument - should produce a locale-formatted date
        Object result = eval("new Date(2025, 0, 15).toLocaleDateString('en-US')");
        assertInstanceOf(String.class, result);
        assertTrue(((String) result).contains("1") && ((String) result).contains("15"));
    }

    @Test
    void testToLocaleDateStringSplitPattern() {
        // This is the exact pattern from issue #2780 that was failing
        eval("var t = new Date(2025, 0, 15);"
                + "var tx = t.toLocaleDateString('en-NZ');"
                + "var parts = tx.split('/');");
        Object parts = get("parts");
        assertInstanceOf(java.util.List.class, parts);
    }

    @Test
    void testToLocaleTimeString() {
        Object result = eval("new Date(2025, 0, 15, 13, 45, 30).toLocaleTimeString()");
        assertInstanceOf(String.class, result);
        assertTrue(((String) result).contains("13") || ((String) result).contains("1:45"));
    }

    @Test
    void testToLocaleString() {
        Object result = eval("new Date(2025, 0, 15, 13, 45, 30).toLocaleString()");
        assertInstanceOf(String.class, result);
        assertFalse(((String) result).isEmpty());
    }

    @Test
    void testToDateString() {
        Object result = eval("new Date(2025, 0, 15).toDateString()");
        assertInstanceOf(String.class, result);
        assertTrue(((String) result).contains("2025"));
        assertTrue(((String) result).contains("Jan"));
        assertTrue(((String) result).contains("Wed"));
    }

    @Test
    void testToTimeString() {
        // @BeforeAll sets UTC, so we expect 13:45:30 GMT+Z
        Object result = eval("new Date(2025, 0, 15, 13, 45, 30).toTimeString()");
        assertInstanceOf(String.class, result);
        String s = (String) result;
        assertTrue(s.contains("GMT"), "toTimeString should contain GMT, got: " + s);
    }

    @Test
    void testToJSON() {
        // toJSON() should return same as toISOString()
        eval("var jsonDate = new Date(Date.UTC(2025, 0, 15));"
                + "var jsonResult = jsonDate.toJSON();");
        assertEquals("2025-01-15T00:00:00.000Z", get("jsonResult"));
        // toJSON and toISOString should be identical
        eval("var d3 = new Date(1609459200000);"
                + "var iso = d3.toISOString();"
                + "var json = d3.toJSON();");
        assertEquals(get("iso"), get("json"));
    }

    @Test
    void testGetTimezoneOffset() {
        // With UTC timezone set in @BeforeAll, offset should be 0
        assertEquals(0, eval("new Date().getTimezoneOffset()"));
    }

    @Test
    void testGetUTCMethods() {
        // UTC on Jan 15, 2025 at 13:45:30.123
        eval("var utcDate = new Date(Date.UTC(2025, 0, 15, 13, 45, 30, 123));"
                + "var uf = utcDate.getUTCFullYear();"
                + "var um = utcDate.getUTCMonth();"
                + "var ud = utcDate.getUTCDate();"
                + "var uday = utcDate.getUTCDay();"
                + "var uh = utcDate.getUTCHours();"
                + "var umin = utcDate.getUTCMinutes();"
                + "var us = utcDate.getUTCSeconds();"
                + "var ums = utcDate.getUTCMilliseconds();");
        assertEquals(2025, get("uf"));
        assertEquals(0, get("um"));       // January = 0
        assertEquals(15, get("ud"));
        assertEquals(3, get("uday"));     // Wednesday = 3
        assertEquals(13, get("uh"));
        assertEquals(45, get("umin"));
        assertEquals(30, get("us"));
        assertEquals(123, get("ums"));
    }

    @Test
    void testGetTimezoneOffsetUsedInDateMath() {
        // Common pattern: adjusting dates by timezone offset
        eval("var utcMillis = Date.UTC(2025, 0, 15);"
                + "var d2 = new Date(utcMillis);"
                + "var offset = d2.getTimezoneOffset();"
                + "var local = new Date(d2.getTime() - offset * 60000);"
                + "var localIso = local.toISOString();");
        // In UTC (@BeforeAll), offset is 0, so local should equal UTC
        assertEquals("2025-01-15T00:00:00.000Z", get("localIso"));
    }

    @Test
    void testInvalidDateNaNPropagation() {
        // new Date(NaN) → Invalid Date; getters return NaN; toString returns "Invalid Date"
        eval("var d = new Date(NaN);"
                + "var t = d.getTime();"
                + "var y = d.getFullYear();"
                + "var s = d.toString();");
        assertEquals(Double.NaN, get("t"));
        assertEquals(Double.NaN, get("y"));
        assertEquals("Invalid Date", get("s"));
    }

    @Test
    void testInvalidDateToISOStringThrowsRangeError() {
        // toISOString on Invalid Date is a RangeError per spec
        try {
            eval("new Date(NaN).toISOString()");
            fail("Expected RangeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("RangeError"), "Expected RangeError, got: " + e.getMessage());
        }
    }

    @Test
    void testTwoArgConstructor() {
        // new Date(year, month) with 2 args was previously dropping to current time
        assertEquals(1467331200000L, eval("new Date(2016, 6).getTime()"));
        assertEquals(6, eval("new Date(2016, 6).getMonth()"));
    }

    @Test
    void testYearTwoDigitLegacyMapping() {
        // Spec: years 0..99 → +1900 in the Date constructor
        assertEquals(1999, eval("new Date(99, 0).getFullYear()"));
        assertEquals(1900, eval("new Date(0, 0).getFullYear()"));
        // outside 0..99: pass through
        assertEquals(2099, eval("new Date(2099, 0).getFullYear()"));
    }

    @Test
    void testSetterValueOfDispatch() {
        // Spec: setHours coerces ToNumber on its arg, calling user-defined valueOf
        eval("var d = new Date(2016, 6);"
                + "var calls = 0;"
                + "var arg = { valueOf: function() { calls++; return 2; } };"
                + "d.setHours(arg);"
                + "var hour = d.getHours();");
        assertEquals(1, get("calls"));
        assertEquals(2, get("hour"));
    }

    @Test
    void testSetterReadsTimeValueBeforeArgCoercion() {
        // Spec: thisTimeValue is read BEFORE ToNumber. If t is NaN, return NaN
        // without overwriting [[DateValue]] — so a valueOf that mutated the date
        // back to a valid value is preserved.
        eval("var d = new Date(NaN);"
                + "var arg = { valueOf: function() { d.setTime(0); return 1; } };"
                + "var r = d.setHours(arg);"
                + "var t = d.getTime();");
        assertEquals(Double.NaN, get("r"), "setHours returns NaN because original t was NaN");
        assertEquals(0L, get("t"), "[[DateValue]] preserves the value the valueOf wrote");
    }

    @Test
    void testHasOwnPropertyOnDatePrototype() {
        // Built-in prototype methods are own properties of Date.prototype
        assertEquals(true, eval("Date.prototype.hasOwnProperty('toString')"));
        assertEquals(true, eval("Date.prototype.hasOwnProperty('getTime')"));
        assertEquals(true, eval("Date.prototype.hasOwnProperty('constructor')"));
    }

    @Test
    void testHasOwnPropertyOnConstructor() {
        // Date constructor's intrinsic statics are own properties
        assertEquals(true, eval("Date.hasOwnProperty('now')"));
        assertEquals(true, eval("Date.hasOwnProperty('parse')"));
        assertEquals(true, eval("Date.hasOwnProperty('UTC')"));
        assertEquals(true, eval("Date.hasOwnProperty('prototype')"));
    }

    @Test
    void testObjectPrototypeToStringTag() {
        // Object.prototype.toString.call(date) === "[object Date]" via type-tag dispatch
        assertEquals("[object Date]", eval("Object.prototype.toString.call(new Date())"));
        assertEquals("[object Array]", eval("Object.prototype.toString.call([])"));
        assertEquals("[object Object]", eval("Object.prototype.toString.call({})"));
    }

    @Test
    void testNonDateThisThrowsTypeError() {
        // Date.prototype.getTime.call({}) → TypeError
        try {
            eval("Date.prototype.getTime.call({})");
            fail("Expected TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("TypeError"), "Expected TypeError, got: " + e.getMessage());
        }
    }

    @Test
    void testParseRoundTripToString() {
        // Date.parse(new Date(0).toString()) === 0 — round-trip through our toString format.
        // Engine may return Long or Double for numeric values; compare via longValue().
        assertEquals(0L, ((Number) eval("Date.parse(new Date(0).toString())")).longValue());
        assertEquals(0L, ((Number) eval("Date.parse(new Date(0).toUTCString())")).longValue());
        assertEquals(0L, ((Number) eval("Date.parse(new Date(0).toISOString())")).longValue());
    }

    @Test
    void testIsoExtendedYear() {
        // Year > 9999 / < 0 → ±YYYYYY ISO extended-year format
        assertEquals("+010000-01-01T00:00:00.000Z", eval("new Date(Date.UTC(10000, 0, 1)).toISOString()"));
        assertEquals("-000001-07-01T00:00:00.000Z", eval("new Date('-000001-07-01T00:00Z').toISOString()"));
    }

    @Test
    void testNegativeYearToString() {
        // Year -1 → "-0001" in toString output (4-digit padded with explicit sign)
        Object s = eval("new Date('-000001-07-01T00:00Z').toString()");
        assertTrue(s.toString().contains("-0001"), "expected '-0001' in: " + s);
    }
}
