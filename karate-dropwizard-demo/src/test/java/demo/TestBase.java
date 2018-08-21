package demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.intuit.karate.junit4.Karate;
import io.dropwizard.Configuration;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.BaseConstructor;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.util.Map;

@RunWith(Karate.class)
public abstract class TestBase {

    @BeforeClass
    public static void setUp() throws Exception {
        String serviceConfigPath = "src/main/dev/service.yml";
        String port = getPort(serviceConfigPath);
        String args[] = new String[]{"server", serviceConfigPath};
        System.setProperty("demo.server.port", port);
        new DropwizardKarateApplication().run(args);
    }

    private static String getPort(String serviceConfigPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode json = mapper.readTree(new File(serviceConfigPath));
        return json.get("server").get("applicationConnectors").get(0).get("port").asText();
    }
}
