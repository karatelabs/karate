package com.intuit.karate.restdocs;

import org.springframework.restdocs.RestDocumentationContext;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.config.RestDocumentationConfigurer;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by rkumar32 on 7/21/17.
 */
public class KarateRestDocumentationConfigurer extends
        RestDocumentationConfigurer<KarateSnippetConfigurer, KarateRestDocumentationConfigurer> {

    private final KarateSnippetConfigurer snippetConfigurer = new KarateSnippetConfigurer(
            this);
    private HashMap<String, Object> configuration;
    private RestDocumentationContext context;
    private final RestDocumentationContextProvider contextProvider;

    public RestDocumentationContext getContext() {
        return context;
    }

    public KarateRestDocumentationConfigurer(
            RestDocumentationContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    public KarateSnippetConfigurer snippets() {
        return this.snippetConfigurer;
    }

    public HashMap<String, Object> getConfiguration() {
        return configuration;
    }

    public void apply() {
        this.context = this.contextProvider.beforeOperation();
        this.configuration = new HashMap<>();
        apply(configuration, context);
    }
}
