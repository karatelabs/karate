// Issue #2802: parameter name `config` shadows the factory's captured `config`
// when this function is invoked from gherkin context. Lexical scoping must win.
function(config) {
  return config.db.read();
}
