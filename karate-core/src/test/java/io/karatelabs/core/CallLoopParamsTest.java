package io.karatelabs.core;

import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallLoopParamsTest {

    @TempDir
    Path tempDir;

    @Test
    void testCallArgReassignedInLoop() throws Exception {
        List<String> requests = Collections.synchronizedList(new ArrayList<>());
        HttpServer server = HttpServer.start(0, request -> {
            String start = request.getParam("start");
            requests.add(request.getPath() + (start == null ? "" : "?start=" + start));
            String body;
            if (start == null) {
                body = "{ \"page\": \"first\", \"_links\": { \"next\": { \"href\": \"/records?start=1\" } } }";
            } else if ("1".equals(start)) {
                body = "{ \"page\": \"second\", \"_links\": { \"next\": { \"href\": \"/records?start=2\" } } }";
            } else {
                body = "{ \"page\": \"third\", \"_links\": {} }";
            }
            return HttpResponse.json(body);
        });
        Files.writeString(tempDir.resolve("callee.feature"), """
                Feature:
                Scenario:
                Given url 'http://localhost:%d'
                And path path
                And params params
                When method get
                Then status 200
                """.formatted(server.getPort()));
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
                Feature:
                Background:
                * def paginate =
                \"""
                function(arg, maxIterations) {
                  let counter = 1;
                  let seen = [];
                  while (counter <= maxIterations) {
                    let result = karate.call('callee.feature', arg).response;
                    seen.push(result.page);
                    if (!result._links.next) { return seen; }
                    let params = {};
                    let paramStr = result._links.next.href;
                    let qIdx = paramStr.indexOf('?');
                    if (qIdx !== -1) {
                      paramStr = paramStr.substring(qIdx + 1);
                      let pairs = paramStr.split('&');
                      for (let i = 0; i < pairs.length; i++) {
                        let kv = pairs[i].split('=');
                        params[kv[0]] = kv[1];
                      }
                    }
                    arg = Object.assign({}, arg, { params: params });
                    counter = counter + 1;
                  }
                  return seen;
                }
                \"""
                Scenario:
                * def seen = paginate({ path: '/records', params: {} }, 3)
                * match seen == ['first', 'second', 'third']
                """);
        SuiteResult result;
        try {
            result = Runner.path(caller.toString())
                    .workingDir(tempDir)
                    .outputDir(tempDir.resolve("reports"))
                    .outputHtmlReport(false)
                    .parallel(1);
        } finally {
            server.stop();
        }
        assertEquals(List.of("/records", "/records?start=1", "/records?start=2"), requests);
        assertEquals(0, result.getScenarioFailedCount());
    }

}
