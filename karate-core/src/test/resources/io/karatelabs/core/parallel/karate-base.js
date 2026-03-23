function fn() {
  return {
    // Shared utility function from karate-base.js
    baseFunction: function(x) { return 'base:' + x; },
    // Another shared function
    formatName: function(first, last) { return first + ' ' + last; }
  };
}
