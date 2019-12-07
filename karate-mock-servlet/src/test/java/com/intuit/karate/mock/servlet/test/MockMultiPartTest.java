package com.intuit.karate.mock.servlet.test;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;

import com.intuit.karate.ScriptValue;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.mock.servlet.MockMultiPart;

/**
 * @author nsehgal
 * 
 *         Test for different StandardMultipartHttpServletRequest implementation
 *         in spring. Below test checks both the implementation should return
 *         the CONTENT_DISPOSITION header details when asked via getHeader().
 *
 */
public class MockMultiPartTest {

	private MockMultiPart mockMultiPart = null;

	private static final String CONTENT_DISPOSITION = "content-disposition";

	@Before
	public void init() {
		ScriptValue NULL = new ScriptValue(null);
		MultiPartItem item = new MultiPartItem("file", NULL);
		item.setContentType("text/csv");
		item.setFilename("test.csv");
		mockMultiPart = new MockMultiPart(item);
	}

	@Test
	public void testSpring2MultipartHeader() {
		String headerValue = mockMultiPart.getHeader(HttpHeaders.CONTENT_DISPOSITION);
		Assert.assertNotNull(headerValue);
		Assert.assertEquals("form-data; filename=\"test.csv\"; name=\"file\"", headerValue);
	}

	@Test
	public void testSpring1MultipartHeader() {
		String headerValue = mockMultiPart.getHeader(CONTENT_DISPOSITION);
		Assert.assertNotNull(headerValue);
		Assert.assertEquals("form-data; filename=\"test.csv\"; name=\"file\"", headerValue);
	}
}
