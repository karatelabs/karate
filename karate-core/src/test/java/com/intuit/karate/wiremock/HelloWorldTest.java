package com.intuit.karate.wiremock;

import com.intuit.karate.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import cucumber.api.CucumberOptions;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@CucumberOptions(plugin = {"html:target/cucumber-html"})
public class HelloWorldTest {

    @ClassRule
    public static WireMockClassRule WIREMOCK_RULE = new WireMockClassRule(wireMockConfig().dynamicPort());

    @Rule
    public WireMockClassRule instanceRule = WIREMOCK_RULE;

    @BeforeClass
    public static void before() {
        System.setProperty("wiremock.port", WIREMOCK_RULE.port() + "");
        String uuid = UUID.randomUUID().toString();
        stubFor(post(urlEqualTo("/v1/cats"))
                .withRequestBody(matching(".*Billie.*"))
                .withHeader("Content-Type", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ id: \"" + uuid + "\", name: \"Billie\" }")));
        stubFor(get(urlEqualTo("/v1/cats/" + uuid))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ id: \"" + uuid + "\", name: \"Billie\" }"))); 
        stubFor(post(urlEqualTo("/v1/dogs"))
                .withRequestBody(matching(".*"))
                .withHeader("Content-Type", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ id: \"" + uuid + "\", name: \"Dummy\" }")));
        stubFor(get(urlEqualTo("/v1/dogs/" + uuid))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ id: \"" + uuid + "\", name: \"Dummy\" }")));         
    }

}
