package com.intuit.karate.formats.postman;

import com.intuit.karate.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

public class PostmanConverterTest {

    @Test
    public void testSuccess() throws IOException {
        if (FileUtils.isOsWindows()) { // TODO
            return;
        }
        // create the temp file and dirctory
        File tempSource = File.createTempFile("karate-postman-input", ".postman_collection.json");
        tempSource.deleteOnExit();
        Path tempOutput = Files.createTempDirectory("karate-postman-output");
        tempOutput.toFile().deleteOnExit();

        // populate the temp source file with the Postman export data
        InputStream is = getClass().getResourceAsStream("postman-echo-single.postman_collection");
        String postman = FileUtils.toString(is);
        Files.write(Paths.get(tempSource.toURI()), postman.getBytes());

        // perform the conversion
        boolean successful = new PostmanConverter().convert(tempSource.toString(), tempOutput.toString());
        Assert.assertTrue(successful);

        // load the expected output from the resources
        is = getClass().getResourceAsStream("expected-converted.txt");
        String expectedConverted = FileUtils.toString(is);

        // load the actual output form the disk
        Path actualOutputPath = Paths.get(tempOutput.toString(),
            tempSource.getName().replace(".postman_collection.json", "") + ".feature");
        String converted = new String(Files.readAllBytes(actualOutputPath), StandardCharsets.UTF_8);

        // the first line is dynamic, as it contains the temp dir characters
        Assert.assertTrue(converted.startsWith("Feature: karate-postman-input"));

        // trim the file so it doesn't contain the line starting with 'Feature':
        String convertedTrimmed = Arrays.stream(converted.split(System.lineSeparator()))
            .filter(line -> !line.startsWith("Feature:"))
            .collect(Collectors.joining(System.lineSeparator()));

        // assert that the trimmed actual output equals the trimmed expected output
        Assert.assertEquals(convertedTrimmed.trim(), expectedConverted.trim());
    }

    @Test
    public void testInvalidSourcePath() {
        boolean successful = new PostmanConverter().convert("does-not-exist_1234567890", "./");
        Assert.assertFalse(successful);
    }

}
