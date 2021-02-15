function fn() {
  if (karate.env === 'mock') {
    return { paymentServiceUrl: 'http://localhost:8080' };
  }
  return { paymentServiceUrl: karate.properties['payment.service.url'] };
}
