package com.intuit.karate.resource;

import com.intuit.karate.FileUtils;
import io.micrometer.core.instrument.util.IOUtils;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlResourceLoader implements ResourceLoader {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(UrlResourceLoader.class);

    private final Pattern suffixPattern = Pattern.compile("(?<suffix>\\.\\w+)$");

    @Override public Optional<Resource> getResource(File workingDir, String path) {

        try {
            final String content = IOUtils.toString(new URL(path).openStream());
            final File file = File.createTempFile("karate_", parseSuffix(path));
            file.deleteOnExit();

            FileUtils.writeToFile(file, content);
            return Optional.of(new FileResource(file));
        } catch (Exception e) {
            LOGGER.debug("Cannot load feature from path: " + path);
            return Optional.empty();
        }
    }

    String parseSuffix(String path) {

        final Matcher matcher = suffixPattern.matcher(path);
        if (matcher.find()) {
            return matcher.group("suffix");
        }
        return ".feature";
    }
}
