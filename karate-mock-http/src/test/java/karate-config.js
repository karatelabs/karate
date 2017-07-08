function() {
  var config = {
    demoUrlBase: 'http://localhost:8080'
  };
  if (karate.env == 'dev-mock') {
    karate.configure('httpClientClass', 'mockhttp.jersey.MockJerseyServlet');
  } else if (karate.env == 'dev-mock-factory') {
    var Factory = Java.type('mockhttp.jersey.MockJerseyServletFactory');
    karate.configure('httpClientInstance', Factory.getMock());
  }
  return config;
}
