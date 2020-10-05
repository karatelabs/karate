package com.intuit.karate.http.apache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.nio.charset.StandardCharsets;

import org.apache.http.HttpEntity;
import org.junit.Test;

/** @author nsehgal */
public class ApacheHttpUtilsTest {

  @Test
  public void testContentTypeWithQuotes() {
    final String originalContentType =
        "multipart/related; charset=UTF-8; boundary=\"----=_Part_19_1913847857.1592612068756\"; type=\"application/xop+xml\"; start-info=\"text/xml\"";

    HttpEntity httpEntity =
        ApacheHttpUtils.getEntity(
            "content is not important", originalContentType, StandardCharsets.UTF_8);

    assertEquals(originalContentType, httpEntity.getContentType().getValue());
  }

  @Test
  public void testContentTypeWithoutQuotes() {
    final String originalContentType =
        "multipart/related; charset=UTF-8; boundary=----=_Part_19_1913847857.1592612068756; type=application/xop+xml; start-info=text/xml";

    final String expectedContentType =
        "multipart/related; charset=UTF-8; boundary=\"----=_Part_19_1913847857.1592612068756\"; type=\"application/xop+xml\"; start-info=\"text/xml\"";

    HttpEntity httpEntity =
        ApacheHttpUtils.getEntity(
            "content is not important", originalContentType, StandardCharsets.UTF_8);
 
    assertNotEquals(originalContentType, httpEntity.getContentType().getValue());
    assertEquals(expectedContentType, httpEntity.getContentType().getValue());
  }

  @Test
  public void testContentTypeWithoutQuotesCharsetInLast() {
    final String originalContentType =
        "multipart/related; boundary=----=_Part_19_1913847857.1592612068756; type=application/xop+xml; start-info=text/xml; charset=UTF-8";

    final String expectedContentType =
        "multipart/related; boundary=\"----=_Part_19_1913847857.1592612068756\"; type=\"application/xop+xml\"; start-info=\"text/xml\"; charset=UTF-8";

    HttpEntity httpEntity =
        ApacheHttpUtils.getEntity(
            "content is not important", originalContentType, StandardCharsets.UTF_8);

    assertNotEquals(originalContentType, httpEntity.getContentType().getValue());
    assertEquals(expectedContentType, httpEntity.getContentType().getValue());
  }

  @Test
  public void testContentTypeWithCustomCharset() {
    final String originalContentType =
        "multipart/related; boundary=----=_Part_19_1913847857.1592612068756; type=application/xop+xml; start-info=text/xml";

    final String expectedContentType =
        "multipart/related; boundary=\"----=_Part_19_1913847857.1592612068756\"; type=\"application/xop+xml\"; start-info=\"text/xml\"; charset=UTF-8";

    HttpEntity httpEntity =
        ApacheHttpUtils.getEntity(
            "content is not important", originalContentType, StandardCharsets.UTF_8);

    assertNotEquals(originalContentType, httpEntity.getContentType().getValue());
    assertEquals(expectedContentType, httpEntity.getContentType().getValue());
  }
}
