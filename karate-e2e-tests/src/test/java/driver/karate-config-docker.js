function fn() {
  karate.configure('driver', {
    type: 'chrome',
    showDriverLog: true,
    start: false,
    timeout: 60000,
    beforeStart: 'supervisorctl start ffmpeg',
    afterStop: 'supervisorctl stop ffmpeg',
    videoFile: '/tmp/karate.mp4'
  });
  var hostname = com.intuit.karate.FileUtils.isOsWindows() ? 'host.docker.internal' : 'localhost'
  var serverPort = karate.properties['server.port'] || 8080;
  var config = {
        driverType: 'chrome',
        serverUrl: 'http://' + hostname + ':' + serverPort
    };
  return config;
}
