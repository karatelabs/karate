// Test-only ext asset for DummyExt. In a real ext this registers an Alpine
// component in alpine:init (D5) to render into the slot DOM containers.
document.addEventListener('alpine:init', () => {
  Alpine.data('dummyPanel', () => ({
    init() { /* no-op: presence of this file + the <script> tag is what the E2E test asserts */ }
  }));
});
