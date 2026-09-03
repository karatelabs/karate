function fn() {
  // `console` only — nothing set for `report`, so the report buffer keeps its default threshold
  karate.configure('logging', { console: 'info' });
  return {};
}
