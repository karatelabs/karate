function fn() {
  var driverType = karate.properties['driver.type'] || 'chrome';
  karate.log('using driver:', driverType);
  karate.configure('driver', {type: driverType, showDriverLog: true});
  return {driverType: driverType};
}
