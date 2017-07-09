function() {
  var config = {
    demoBaseUrl: 'http://localhost:8080'
  };
  if (karate.env == 'dev-mock') {
    karate.configure('httpClientClass', 'mockhttp.jersey.MockJerseyServlet');
  } else if (karate.env == 'dev-mock-factory') {
    var Factory = Java.type('mockhttp.jersey.MockJerseyServletFactory');
    karate.configure('httpClientInstance', Factory.getMock());
  } else if (karate.env == 'dev-mock-springmvc') {
    var Factory = Java.type('demo.MockSpringMvcServlet');
    karate.configure('httpClientInstance', Factory.getMock());
  }
  return config;
}
