// Client-side filter/search for index.html. All data is already in the DOM.

(function () {
  // Render run-meta block.
  try {
    const raw = document.getElementById('run-meta').textContent;
    const meta = JSON.parse(raw);
    const el = document.getElementById('meta');
    if (el && meta) {
      const lines = [];
      if (meta.test262_sha)      lines.push('test262 SHA     ' + meta.test262_sha);
      if (meta.karate_js_git_sha)lines.push('karate-js SHA   ' + meta.karate_js_git_sha);
      if (meta.jdk)              lines.push('JDK             ' + meta.jdk);
      if (meta.os)               lines.push('OS              ' + meta.os);
      if (meta.started_at)       lines.push('started         ' + meta.started_at);
      if (meta.elapsed_ms != null) lines.push('elapsed         ' + (meta.elapsed_ms / 1000).toFixed(1) + 's');
      el.textContent = lines.join('\n');
    }
  } catch (e) { /* no meta, fine */ }

  const input = document.getElementById('q');
  const statusFilters = document.querySelectorAll('.status-filter');
  const items = document.querySelectorAll('#tree li.t');

  function refresh() {
    const q = (input?.value || '').toLowerCase().trim();
    const allowed = new Set();
    statusFilters.forEach(cb => { if (cb.checked) allowed.add(cb.dataset.s); });

    // Track which <details> actually contain a visible test; collapse empties.
    const visibleCountByDetails = new WeakMap();

    items.forEach(li => {
      const path = (li.dataset.path || '').toLowerCase();
      const status = li.querySelector('.status')?.textContent?.trim();
      const match = (!q || path.includes(q)) && allowed.has(status);
      li.classList.toggle('hidden', !match);
      if (match) {
        // Bubble up: mark every ancestor <details> as containing a visible item.
        let d = li.closest('details');
        while (d) {
          visibleCountByDetails.set(d, (visibleCountByDetails.get(d) || 0) + 1);
          d = d.parentElement?.closest('details');
        }
      }
    });

    document.querySelectorAll('#tree details').forEach(d => {
      const hasVisible = (visibleCountByDetails.get(d) || 0) > 0;
      d.classList.toggle('hidden', !hasVisible);
    });
  }

  input?.addEventListener('input', refresh);
  statusFilters.forEach(cb => cb.addEventListener('change', refresh));
})();
