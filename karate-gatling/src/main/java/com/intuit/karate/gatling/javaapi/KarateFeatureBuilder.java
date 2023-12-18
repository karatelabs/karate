package com.intuit.karate.gatling.javaapi;

import com.intuit.karate.gatling.KarateFeatureActionBuilder;
import com.intuit.karate.gatling.PreDef;
import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.internal.Converters;

public class KarateFeatureBuilder implements ActionBuilder {

    
    public KarateFeatureActionBuilder builder;

    public KarateFeatureBuilder(String name, String... tags) {
        builder = PreDef.karateFeature(name, Converters.toScalaSeq(tags));
    }

    public KarateFeatureBuilder silent() {
        builder = builder.silent();
        return this;
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        return builder;
    }
}
