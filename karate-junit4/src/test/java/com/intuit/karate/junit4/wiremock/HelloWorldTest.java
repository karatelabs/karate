package com.intuit.karate.junit4.wiremock;

import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
public class HelloWorldTest {

    @ClassRule
    public static WireMockClassRule WIREMOCK_RULE = new WireMockClassRule(wireMockConfig().dynamicPort());

    @Rule
    public WireMockClassRule instanceRule = WIREMOCK_RULE;

    public static final byte[] testBytes = new byte[]{15, 98, -45, 0, 0, 7, -124, 75, 12, 26, 0, 9};

    @BeforeClass
    public static void before() {
        System.setProperty("wiremock.port", WIREMOCK_RULE.port() + "");
        String uuid = UUID.randomUUID().toString();
        stubFor(post(urlEqualTo("/v1/cats"))
                .withHeader("Content-Type", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"id\": \"" + uuid + "\", name: \"Billie\" }")));
        stubFor(get(urlEqualTo("/v1/cats/" + uuid))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"id\": \"" + uuid + "\", name: \"Billie\" }")));
        stubFor(post(urlEqualTo("/v1/dogs"))
                .withHeader("Content-Type", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"id\": \"" + uuid + "\", name: \"Dummy\" }")));
        stubFor(get(urlEqualTo("/v1/dogs/" + uuid))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"id\": \"" + uuid + "\", name: \"Dummy\" }")));
        stubFor(get(urlEqualTo("/v1/binary/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBody(testBytes)));
        stubFor(patch(urlEqualTo("/v1/patch"))
                .willReturn((aResponse().withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"success\": true }"))));
        stubFor(delete(urlEqualTo("/v1/delete"))
                .willReturn((aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"success\": true }"))));
        stubFor(delete(urlEqualTo("/v1/deleteEmptyResponse"))
                .willReturn(null));
        stubFor(get(urlEqualTo("/v1/commas?foo=bar%2Cbaz"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"success\": true }")));
        stubFor(get(urlEqualTo("/v1/german"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody("<name>Müller</name>")));
        stubFor(get(urlMatching("/v1/encoding/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"success\": true }")));
    }

}
