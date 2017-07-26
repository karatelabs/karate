package com.intuit.karate.restdocs;

import org.springframework.restdocs.config.SnippetConfigurer;

/**
 * Created by rkumar32 on 7/21/17.
 */
public class KarateSnippetConfigurer extends
        SnippetConfigurer<KarateRestDocumentationConfigurer, KarateSnippetConfigurer> {

    KarateSnippetConfigurer(KarateRestDocumentationConfigurer parent) {
        super(parent);
    }
}