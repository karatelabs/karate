function fn() {
  var webSocketUrl = karate.properties['karate.driver.webSocketUrl'];
  var serverUrl = karate.properties['karate.driver.serverUrl'];
  var httpsServerUrl = karate.properties['karate.driver.httpsServerUrl'];

  karate.log('karate-config (oopif): serverUrl =', serverUrl);
  karate.log('karate-config (oopif): httpsServerUrl =', httpsServerUrl);

  var driverConfig = { timeout: 30000 };

  if (webSocketUrl) {
    driverConfig.webSocketUrl = webSocketUrl;
    karate.log('karate-config (oopif): webSocketUrl =', webSocketUrl);
  }

  var config = {
    serverUrl: serverUrl,
    httpsServerUrl: httpsServerUrl,
    driverConfig: driverConfig
  };

  karate.configure('driver', config.driverConfig);

  return config;
}
