function fn() {
  // above INFO, which is where print and the HTTP blocks enter the report buffer
  karate.configure('logging', { report: 'warn' });
  return {};
}
