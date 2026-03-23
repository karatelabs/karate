function fn() {
  // Enable disk caching for callSingle results
  // Cache for 5 minutes in target/callsingle-cache directory
  karate.configure('callSingleCache', { minutes: 5, dir: 'target/callsingle-cache' });
  return {};
}
