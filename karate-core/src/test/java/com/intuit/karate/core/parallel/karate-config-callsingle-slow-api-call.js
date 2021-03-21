function fn() {
  var callApi = karate.callSingle('callonce-config-call-slow-api.feature');
  var config = {}
  config.products = callApi.response;
  return config;
}
