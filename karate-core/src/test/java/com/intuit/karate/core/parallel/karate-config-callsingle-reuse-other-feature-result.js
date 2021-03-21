function fn() {
  var configuration = karate.callSingle('callonce-config-api-url.feature')
  var config = karate.callSingle('callonce-config-call-api-param-url.feature', configuration);
  return config;
}
