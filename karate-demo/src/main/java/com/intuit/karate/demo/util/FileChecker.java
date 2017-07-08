package com.intuit.karate.demo.util;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 *
 * @author pthomas3
 */
public class FileChecker {

    private static final Logger logger = LoggerFactory.getLogger(FileChecker.class);

    public static String getMetadata(String id) throws Exception {
        logger.info("java was called from karate ! id: {}", id);
        // behind the scenes the server is storing data in a file, we read it directly here
        File file = new File("target/demofiles/" + id + "_meta.txt");
        return FileUtils.readFileToString(file, "utf-8");
    }

}
