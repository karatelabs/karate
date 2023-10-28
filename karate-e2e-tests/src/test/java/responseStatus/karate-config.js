function fn() {
    var env = karate.env; // get system property 'karate.env'
    karate.log('karate.env system property was:', env);
    if (!env) {
        env = 'dev';
    }
    var config = {
        env: env,
        myVarName: 'someValue'
    }
    if (env == 'dev') {
        // customize
        // e.g. config.foo = 'bar';
    } else if (env == 'e2e') {
        // customize
    }

    console.log("HELLO WORLD");

    config = karate.callSingle('classpath:responseStatus/call-single.feature', config);
    config = karate.call('classpath:responseStatus/call.feature', config);

    config.isResponseStatus200_config = function () {
        console.log(Object.keys(this));
        if (responseStatus != 200) {
            karate.log("Retry since expectedStatus 200 != actual responseStatus: " + responseStatus);
            return false;
        }
        return true;
    }

    return config;
}