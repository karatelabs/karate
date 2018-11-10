function fn() {
  var config = {};
  config.paymentServiceUrl = karate.properties['payment.service.url'];    
  config.queueName = karate.properties['shipping.queue.name'];
    if (config.paymentServiceUrl.startsWith('https')) {
      if (config.queueName == 'DEMO.CONTRACT.SSL') {
        karate.configure('ssl', { trustStore: 'classpath:server-keystore.p12', password: 'karate-mock', type: 'pkcs12' });
      } else {
        karate.configure('ssl', true);
      }
    }
  return config;
}
