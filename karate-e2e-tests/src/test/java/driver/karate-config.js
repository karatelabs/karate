function fn() {
  var serverPort = karate.properties['server.port'] || 8080;
  var hostname = com.intuit.karate.FileUtils.isOsWindows() ? 'host.docker.internal' : 'localhost'
  var config = {
    serverUrl: 'http://' + hostname + ':' + serverPort
  };
  return config;
}