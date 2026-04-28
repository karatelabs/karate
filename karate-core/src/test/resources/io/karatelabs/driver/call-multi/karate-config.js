function fn() {
  var webSocketUrl = karate.properties['karate.driver.webSocketUrl'];
  var serverUrl = karate.properties['karate.driver.serverUrl'];

  var driverConfig = { timeout: 30000 };
  if (webSocketUrl) {
    driverConfig.webSocketUrl = webSocketUrl;
  }

  var config = {
    serverUrl: serverUrl,
    driverConfig: driverConfig
  };

  karate.configure('driver', config.driverConfig);

  return config;
}
