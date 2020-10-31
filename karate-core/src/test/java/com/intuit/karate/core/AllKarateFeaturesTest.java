package com.intuit.karate.core;

import com.intuit.karate.Resource;
import com.intuit.karate.FileUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class AllKarateFeaturesTest {

    static final Logger logger = LoggerFactory.getLogger(AllKarateFeaturesTest.class);

    @Test
    void testParsingAllFeaturesInKarate() {
        List<Resource> files = FileUtils.scanForFeatureFiles(false, "..", null);
        logger.debug("found files count: {}", files.size());
        assertTrue(files.size() > 200);
        for (Resource file : files) {
            logger.trace("parsing: {}", file.getRelativePath());
            FeatureParser.parse(file);
        }
    }

}
