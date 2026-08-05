/*
 * Present mostly so Karate does not warn once per scenario that it is missing - that
 * warning allocates, on every iteration, and lands in the same allocation panel the
 * workload is trying to be read from.
 *
 * mockUrl is set by the harness when a workload declares needsMock().
 *
 * Deliberately karate.properties[...] and not the karate.sysprop() we recommend to users: a
 * workload with no mock reads a key that is ABSENT, which is the interesting shape — a missed
 * property read is the expensive one, and keeping it here means a regression in that path shows
 * up in the null pair rather than going unmeasured.
 */
function fn() {
  return {
    mockUrl: karate.properties['mock.url']
  };
}
