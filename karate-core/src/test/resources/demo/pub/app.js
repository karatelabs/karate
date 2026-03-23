// Static JavaScript file for demo app
// This file should be served directly without processing

console.log('Demo app loaded');

function sayHello(name) {
    return 'Hello, ' + name + '!';
}

// Export for testing
if (typeof module !== 'undefined') {
    module.exports = { sayHello: sayHello };
}
