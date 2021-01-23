package ui;

import com.intuit.karate.http.HttpServer;
import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.BeforeAll;

class UiRunner {
    
    @BeforeAll
    public static void beforeAll() {
        HttpServer server = MockRunner.start(0);
        System.setProperty("web.url.base", "http://localhost:" + server.getPort());        
    }
    
    @Karate.Test
    Karate testUi() {
        return Karate.run("classpath:ui/test.feature");
    }
    
}
