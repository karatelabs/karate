package com.intuit.karate.resource;

import com.intuit.karate.FileUtils;
import com.intuit.karate.core.MockServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResourceLoaderRegistryTest {


    @Test
    public void registerRemoteResourceLoader() {
        final ResourceLoaderRegistration register = ResourceLoaderRegistry.register(new UrlResourceLoader());
        try {
            MockServer server = MockServer
                .feature("classpath:com/intuit/karate/resource/remote-resource.feature")
                .http(0).build();

            final Resource resource = ResourceLoaderRegistry.getResource(null, String.format("http://localhost:%s/resource-url.suffix", server.getPort()));
            assertNotNull(resource);
            assertEquals(FileUtils.toString(resource.getFile()), "remote fetched resource");
        } finally {
            register.unregister();
        }
    }
}