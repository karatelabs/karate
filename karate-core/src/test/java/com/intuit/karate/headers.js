function fn() {
  var out = {
    foo: 'bar_' + someConfig // someConfig is 'start-up' config, will never change
  };
  var ticket = karate.get('ticket'); // get can get the 'latest' values of variables
  if (ticket) {
    out.baz = 'ban'
  }
  return out;
}
