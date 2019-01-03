function fn() {
  var uuid = java.util.UUID.randomUUID() + '';
  karate.set('prevUuid', uuid);
  return { token: uuid };
}
