package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import java.util.Arrays;
import java.util.Iterator;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class MultiPartBuilderTest {

    static final Logger logger = LoggerFactory.getLogger(MultiPartBuilderTest.class);

    String join(String... lines) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> iterator = Arrays.asList(lines).iterator();
        while (iterator.hasNext()) {
            sb.append(iterator.next()).append('\r').append('\n');
        }
        return sb.toString();
    }

    @Test
    void testMultiPart() {
        MultiPartBuilder builder = new MultiPartBuilder(true, null);
        builder.part("bar", "hello world");
        byte[] bytes = builder.build();
        String boundary = builder.getBoundary();
        String actual = FileUtils.toString(bytes);
        String expected = join(
                "--" + boundary,
                "content-disposition: form-data; name=\"bar\"",
                "content-length: 11",
                "content-type: text/plain; charset=UTF-8",
                "",
                "hello world",
                "--" + boundary + "--"
        );
        assertEquals(expected, actual);
    }

    @Test
    void testUrlEncoded() {
        MultiPartBuilder builder = new MultiPartBuilder(false, null);
        builder.part("bar", "hello world");
        byte[] bytes = builder.build();
        assertEquals("application/x-www-form-urlencoded", builder.getContentTypeHeader());
        String actual = FileUtils.toString(bytes);
        assertEquals("bar=hello+world", actual);
    }

}
