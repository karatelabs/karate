package com.intuit.karate.resource;

import io.github.classgraph.ResourceList;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DefaultResourceLoader implements ResourceLoader {

    @Override public Optional<Resource> getResource(File workingDir, String path) {
        if (path.startsWith("classpath:")) {
            path = ResourceUtils.removePrefix(path);
            File file = ResourceUtils.classPathToFile(path);
            if (file != null) {
                return Optional.of(new FileResource(file, true, path));
            }
            List<Resource> resources = new ArrayList<>();
            ResourceList rl = ResourceUtils.SCAN_RESULT.getResourcesWithPath(path);
            if (rl == null) {
                rl = ResourceList.emptyList();
            }
            rl.forEachByteArrayIgnoringIOException((res, bytes) -> {
                URI uri = res.getURI();
                if ("file".equals(uri.getScheme())) {
                    File found = Paths.get(uri).toFile();
                    resources.add(new FileResource(found, true, res.getPath()));
                } else {
                    resources.add(new JarResource(bytes, res.getPath(), uri));
                }
            });
            if (resources.isEmpty()) {
                throw new RuntimeException("not found: " + path);
            }
            return Optional.ofNullable(resources.get(0));
        } else {
            File file = new File(ResourceUtils.removePrefix(path));
            if (!file.exists()) {
                throw new RuntimeException("not found: " + path);
            }
            Path relativePath = workingDir.toPath().relativize(file.getAbsoluteFile().toPath());
            return Optional.of(new FileResource(file, false, relativePath.toString()));
        }
    }

}
