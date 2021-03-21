function fn() {
  var configuration = karate.callSingle('callonce-config-api-url-2.feature')
  var config = karate.callSingle('callonce-config-reuse-other-feature.feature', configuration);
  return config;
}
