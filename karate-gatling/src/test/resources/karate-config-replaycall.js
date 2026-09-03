function fn() {
  // the customer's shape: a helper defined here, handed a lambda by the scenario, and the
  // karate.call() that reaches the sub-feature happens inside that lambda
  return {
    withCall: function(lambda) {
      return lambda();
    }
  };
}
