package com.intuit.karate.junit4.large;

import com.intuit.karate.FileUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import java.io.File;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author pthomas3
 */
public class LargePayloadRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(LargePayloadRunner.class);
    
    @Test
    public void testLargeXml() {
        Document doc = XmlUtils.toXmlDoc("<ProcessRequest xmlns=\"http://someservice.com/someProcess\"/>");
        Element root = doc.getDocumentElement();        
        Element test = doc.createElement("statusCode");
        test.setTextContent("changeme");
        root.appendChild(test);
        Element foo = doc.createElement("foo");
        root.appendChild(foo);
        for (int i = 0; i < 1000000; i++) {
            Element bar = doc.createElement("bar");
            bar.setTextContent("baz" + i);
            foo.appendChild(bar);
        }
        String xml = XmlUtils.toString(doc);
        byte[] bytes = FileUtils.toBytes(xml);
        int size = bytes.length;
        logger.debug("xml byte count: " + size);
        FileUtils.writeToFile(new File("target/large.xml"), xml);
        Results results = Runner
                .path("classpath:com/intuit/karate/junit4/large/large.feature")
                .parallel(1);
        assertEquals(results.getErrorMessages(), 0, results.getFailCount());        
    }    
    
}
