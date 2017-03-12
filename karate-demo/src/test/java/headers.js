function() {
  var token = karate.get('token');
  var time = karate.get('time');
  if (token && time) {
    // demoBaseUrl is like a constant, since it was injected at the time of configuration / init
    return { Authorization: token + time + demoBaseUrl };
  } else {
    return {};
  }
}
