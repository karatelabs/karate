function fn() {
  var webSocketUrl = karate.properties['karate.driver.webSocketUrl'];
  var serverUrl = karate.properties['karate.driver.serverUrl'];
  var driverType = karate.properties['karate.driver.type'];
  var webDriverUrl = karate.properties['karate.driver.webDriverUrl'];

  karate.log('karate-config: serverUrl =', serverUrl);

  var driverConfig = { timeout: 30000 };

  // W3C WebDriver configuration
  if (driverType) {
    driverConfig.type = driverType;
    karate.log('karate-config: driverType =', driverType);
  }
  if (webDriverUrl) {
    driverConfig.webDriverUrl = webDriverUrl;
    karate.log('karate-config: webDriverUrl =', webDriverUrl);
  }

  // CDP configuration
  if (webSocketUrl) {
    driverConfig.webSocketUrl = webSocketUrl;
    karate.log('karate-config: webSocketUrl =', webSocketUrl);
  }

  var config = {
    serverUrl: serverUrl,
    driverConfig: driverConfig
  };

  karate.configure('driver', config.driverConfig);

  return config;
}
