function fn() {
  karate.configure('driver', {
    type: 'chrome',
    showDriverLog: true,
    start: false,
    beforeStart: 'supervisorctl start ffmpeg',
    afterStop: 'supervisorctl stop ffmpeg',
    videoFile: '/tmp/karate.mp4'
  });
  return {driverType: 'chrome'};
}
