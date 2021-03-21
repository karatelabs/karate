function fn() {
  var configuration = karate.callSingle('callonce-config-api-url.feature')

  // this called feature will try to reuse a variable defined inside the 'callonce-config-api-url.feature'
  // the callSingle() result scope is contained and scope was not shared so it won't have access
  var config = karate.callSingle('callonce-config-call-api-reuse-variable.feature')
  return config;
}
