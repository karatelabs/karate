package com.intuit.karate.driver.appium;

import com.intuit.karate.Json;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class MobileDriverOptionsTest {

    @Test
    public void testGetBrowserName() {
        String driverSession = "{\n" +
                "          desiredCapabilities: { \n" +
                "            browserName: 'Safari',\n" +
                "          },\n" +
                "          capabilities: {\n" +
                "            firstMatch: [\n" +
                "                {\n" +
                "                  browserName: \"Safari\",\n" +
                "                  platformName: \"iOS\",\n" +
                "                  'appium:platformVersion' : \"15.0\",\n" +
                "                  'appium:deviceName' : \"iPhone 8 Simulator\",\n" +
                "                  'appium:screenResolution' : \"1024x768\",\n" +
                "                  idleTimeout : 12000,\n" +
                "                  avoidProxy : false,\n" +
                "                  'sauce:options': {\n" +
                "                      username: sauceUsername,\n" +
                "                      accessKey: sauceKey,\n" +
                "                      \n" +
                "                  }\n" +
                "                }\n" +
                "            ]\n" +
                "          }\n" +
                "        }";

        Map<String, Object> options = Json.of(driverSession).get("$");
        String browserName = MobileDriverOptions.getBrowserName(options);
        Assertions.assertEquals("Safari", browserName);


        String driverSession2 = "{\n" +
                "          capabilities: {\n" +
                "            firstMatch: [\n" +
                "                {\n" +
                "                  browserName: \"Safari\",\n" +
                "                  platformName: \"iOS\",\n" +
                "                  'appium:platformVersion' : \"15.0\",\n" +
                "                  'appium:deviceName' : \"iPhone 8 Simulator\",\n" +
                "                  'appium:screenResolution' : \"1024x768\",\n" +
                "                  idleTimeout : 12000,\n" +
                "                  avoidProxy : false,\n" +
                "                  'sauce:options': {\n" +
                "                      username: sauceUsername,\n" +
                "                      accessKey: sauceKey,\n" +
                "                      \n" +
                "                  }\n" +
                "                }\n" +
                "            ]\n" +
                "          }\n" +
                "        }";

        Map<String, Object> options2 = Json.of(driverSession2).get("$");
        String browserName2 = MobileDriverOptions.getBrowserName(options2);
        Assertions.assertEquals("Safari", browserName2);


        String driverSession3 = "{\n" +
                "          desiredCapabilities: {\n" +
                "                  browserName: \"Safari\",\n" +
                "                  platformName: \"iOS\",\n" +
                "                }\n" +
                "        }";

        Map<String, Object> options3 = Json.of(driverSession3).get("$");
        String browserName3 = MobileDriverOptions.getBrowserName(options3);
        Assertions.assertEquals("Safari", browserName3);


        String driverSession4 = "{\n" +
                "          capabilities: {\n" +
                "                  browserName: \"Safari\",\n" +
                "                  platformName: \"iOS\",\n" +
                "                  'appium:platformVersion' : \"15.0\",\n" +
                "          }\n" +
                "        }";

        Map<String, Object> options4 = Json.of(driverSession4).get("$");
        String browserName4 = MobileDriverOptions.getBrowserName(options4);
        Assertions.assertEquals("Safari", browserName4);


        String driverSession5 = "{\n" +
                "        }";

        Map<String, Object> options5 = Json.of(driverSession5).get("$");
        String browserName5 = MobileDriverOptions.getBrowserName(options5);
        Assertions.assertEquals(null, browserName5);
    }
}
