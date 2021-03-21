function fn() {
  var callApi = karate.callSingle('callonce-config-call-api.feature');
  var config = {}
  config.products = callApi.response;
  return config;
}
