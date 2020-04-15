function() {
  if (karate.env === 'mock') {
    var Factory = Java.type('payment.mock.servlet.MockSpringMvcServlet');
    karate.configure('httpClientInstance', Factory.getMock());
    return { paymentServiceUrl: 'http://localhost:8080' }
  }
  return { paymentServiceUrl: karate.properties['payment.service.url'] }
}
