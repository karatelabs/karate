function fn() {
  var webSocketUrl = karate.properties['karate.driver.webSocketUrl'];
  var serverUrl = karate.properties['karate.driver.serverUrl'];

  karate.log('karate-config: serverUrl =', serverUrl);

  var driverConfig = { timeout: 30000 };
  // Only set webSocketUrl if provided (ContainerDriverProvider handles it otherwise)
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
