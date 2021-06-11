function fn() {
  var config = karate.callonce('callonce-config-called.feature');
  return config;
}
