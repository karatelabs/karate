Feature:

Scenario:
* eval
"""
var temp = {
  proxy: { uri: 'http://my-proxy.com:3128', nonProxyHosts: [ 'my-api2.com' ]}
};
karate.configure('proxy', temp.proxy);
"""
