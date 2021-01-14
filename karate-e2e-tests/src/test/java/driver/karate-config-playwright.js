function fn() {
  var driverType = karate.properties['driver.type'] || 'playwright';
  karate.log('using driver:', driverType);
  // the executable defaults to 'playwright' so a shell script with that name in PATH will do
  karate.configure('driver', {type: driverType, showDriverLog: true});
  return { driverType: driverType };
}
