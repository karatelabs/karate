function fn() {
  var token = karate.get('token');
  var time = karate.get('time');
  if (token && time) {
    var uuid = java.util.UUID.randomUUID(); // create a unique id for each request
    // demoBaseUrl was available at the time this function was declared
    // and so behaves like a constant, use 'karate.get' for dynamic values
    return { 
        Authorization: token + time + demoBaseUrl,
        request_id: uuid + '' // convert the java uuid into a string
    };
  } else {
    return {};
  }
}
