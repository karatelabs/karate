package com.intuit.karate.mock.core;

import com.intuit.karate.core.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class KarateMockServer {

    private static final Logger logger = LoggerFactory.getLogger(KarateMockServer.class);

    List<Feature> features;
    boolean watch;
    Map<String, Object> args;


    private KarateMockServer(){
    }

    public static class Builder {

        final List<Feature> features;
        boolean watch;
        Map<String, Object> args;

        Builder(Feature feature) {
            this.features = Arrays.asList(feature);
        }

        Builder(List<Feature> features) {
            this.features = features;
        }

        public Builder watch(boolean value) {
            watch = value;
            return this;
        }

        public Builder args(Map<String, Object> value) {
            args = value;
            return this;
        }

        public Builder arg(String name, Object value) {
            if (args == null) {
                args = new HashMap();
            }
            args.put(name, value);
            return this;
        }

        public KarateMockCallback build() {
            KarateMockCallback callback = watch ? new ReloadingMockHandler(features, args) : new KarateMockHandler(features, args);
            return callback;
        }
    }

    private static class ReloadingMockHandler implements KarateMockCallback {

        private final Map<String, Object> args;
        private KarateMockCallback callback;
        private final LinkedHashMap<File, Long> files = new LinkedHashMap<>();

        public ReloadingMockHandler(List<Feature> features, Map<String, Object> args) {
            this.args = args;
            for (Feature f : features) {
                this.files.put(f.getResource().getFile(), f.getResource().getFile().lastModified());
            }
            logger.debug("watch mode init - {}", files);
            callback = new KarateMockHandler(features, args);
        }

        @Override
        public KarateMessage receive(KarateMessage req) {
            boolean reload = files.entrySet().stream().reduce(false, (modified, entry) -> entry.getKey().lastModified() > entry.getValue(), (a, b) -> a || b);
            if (reload) {
                List<Feature> features = files.keySet().stream().map(f -> Feature.read(f)).collect(Collectors.toList());
                callback = new KarateMockHandler(features, args);
            }
            return callback.receive(req);
        }
    }

    public static Builder feature(String path) {
        return new Builder(Feature.read(path));
    }

    public static Builder feature(File file) {
        return new Builder(Feature.read(file));
    }

    public static Builder feature(Feature feature) {
        return new Builder(feature);
    }

    public static Builder featurePaths(List<String> paths) {
        return new Builder(paths.stream().map(p -> Feature.read(p)).collect(Collectors.toList()));
    }

    public static Builder featurePaths(String... paths) {
        return featurePaths(Arrays.asList(paths));
    }

    public static Builder featureFiles(List<File> features) {
        return new Builder(features.stream().map(file -> Feature.read(file)).collect(Collectors.toList()));
    }

    public static Builder features(List<Feature> features) {
        return new Builder(features);
    }

}
