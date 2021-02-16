package com.intuit.karate.resource;

import com.intuit.karate.FileUtils;
import com.intuit.karate.core.MockServer;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlResourceLoaderTest {

    @Test
    public void suffixParsing() throws Exception {

        final UrlResourceLoader loader = new UrlResourceLoader();

        assertEquals(".feature", loader.parseSuffix("no suffix at all"));
        assertEquals(".feature", loader.parseSuffix("suffix .but-not-a-word"));
        assertEquals(".js", loader.parseSuffix("this ./correct_suffix.js"));
    }

    @Test
    public void emptyOptional() throws Exception {

        final Optional<Resource> resource = new UrlResourceLoader().getResource(null, "http://does-not-exists.foo/test");
        assertFalse(resource.isPresent());
    }

    @Test
    public void loadHttpResource() {
        MockServer server = MockServer
                .feature("classpath:com/intuit/karate/resource/remote-resource.feature")
                .http(0).build();

        final Optional<Resource> resource = new UrlResourceLoader()
            .getResource(null, String.format("http://localhost:%s/resource-url.suffix", server.getPort()));

        assertTrue(resource.isPresent());
        assertEquals(FileUtils.toString(resource.get().getFile()), "remote fetched resource");
    }
}