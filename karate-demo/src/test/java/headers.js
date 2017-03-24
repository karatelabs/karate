function() {
  var token = karate.get('token');
  var time = karate.get('time');
  if (token && time) {
    // demoBaseUrl was available at the time this function was declared
	// and so behaves like a constant, use 'karate.get' for dynamic values
    return { Authorization: token + time + demoBaseUrl };
  } else {
    return {};
  }
}
