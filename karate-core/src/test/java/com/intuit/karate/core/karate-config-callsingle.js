function fn() {
  var config = karate.callSingle('callonce-config-called.feature');
  return config;
}
