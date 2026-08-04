/*
 * Present mostly so Karate does not warn once per scenario that it is missing - that
 * warning allocates, on every iteration, and lands in the same allocation panel the
 * workload is trying to be read from.
 *
 * mockUrl is set by the harness when a workload declares needsMock().
 */
function fn() {
  return {
    mockUrl: karate.properties['mock.url']
  };
}
