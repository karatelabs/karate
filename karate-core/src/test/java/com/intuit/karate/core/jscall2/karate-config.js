function fn() {
    let config = {
      serverUrl: 'http://localhost:' + karate.properties['server.port']
    };
    config = karate.callSingle('classpath:com/intuit/karate/core/jscall2/call-single.feature', config);
    config = karate.call('classpath:com/intuit/karate/core/jscall2/call.feature', config);
    config.isResponseStatus200_config = function () {
        if (responseStatus != 200) {
            karate.log("retry since expectedStatus 200 != actual responseStatus: " + responseStatus);
            return false;
        }
        return true;
    }
    return config;
}