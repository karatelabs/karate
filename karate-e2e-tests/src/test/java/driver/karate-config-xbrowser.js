function fn() {
  karate.log('using driver:', driver);
  karate.configure('driver', {type: driver, showDriverLog: true});
}
