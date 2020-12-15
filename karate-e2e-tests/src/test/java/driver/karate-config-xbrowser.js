function fn() {
  karate.log('using driver:', driverType);
  karate.configure('driver', {type: driverType, showDriverLog: true});
}
