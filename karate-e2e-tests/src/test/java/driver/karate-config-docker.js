function fn() {
  var driverType = 'chrome';
  karate.configure('driver', {type: driverType, showDriverLog: true, start: false});
  return { driverType: driverType };
}
