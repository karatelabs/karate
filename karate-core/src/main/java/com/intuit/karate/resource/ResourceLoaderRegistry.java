package com.intuit.karate.resource;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Provides access to all registered ResourcesLoaders.
 * ResourcesLoaders are used by Karate to load feature and java script files.
 *
 * This concept enables you to load these resources from any location.
 */
public class ResourceLoaderRegistry {

    private static final List<ResourceLoader> registeredResourceLoaderList = Collections.synchronizedList(new ArrayList<>());

    static {
        restoreDefault();
    }

    public static void restoreDefault() {
        clear();
        register(new DefaultResourceLoader());
    }

    public static void clear() {
        registeredResourceLoaderList.clear();
    }

    public static ResourceLoaderRegistration register(ResourceLoader resourceLoader) {
        registeredResourceLoaderList.add(0, resourceLoader);
        return () -> registeredResourceLoaderList.remove(resourceLoader);
    }

    public static Resource getResource(File workingDir, String path) {

        return registeredResourceLoaderList.stream()
            .map(rl -> rl.getResource(workingDir, path))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Could not load feature from path " + path));
    }


}
