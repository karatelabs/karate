// Shared client-side logic for index.html and details.html.
// Both pages embed run-meta as a JSON island; only details.html has the
// search/filter UI.

(function () {
  // -------- run-meta block (both pages) --------
  try {
    const raw = document.getElementById('run-meta')?.textContent;
    if (raw) {
      const meta = JSON.parse(raw);
      const el = document.getElementById('meta');
      if (el && meta) {
        const lines = [];
        if (meta.test262_sha)        lines.push('test262 SHA     ' + meta.test262_sha);
        if (meta.karate_js_version)  lines.push('karate-js ver   ' + meta.karate_js_version);
        if (meta.karate_js_git_sha)  lines.push('karate-js SHA   ' + meta.karate_js_git_sha);
        if (meta.jdk)                lines.push('JDK             ' + meta.jdk);
        if (meta.os)                 lines.push('OS              ' + meta.os);
        if (meta.started_at)         lines.push('started         ' + meta.started_at);
        if (meta.elapsed_ms != null) lines.push('elapsed         ' + (meta.elapsed_ms / 1000).toFixed(1) + 's');
        el.textContent = lines.join('\n');
      }
    }
  } catch (e) { /* no meta or parse failure — fine, skip */ }

  // -------- details.html: search + status filter --------
  // The details page tags every <li.row> with data-path and data-s; sections
  // hide themselves when none of their rows are visible.
  const detailsRoot = document.getElementById('details-root');
  if (!detailsRoot) return;

  const input = document.getElementById('q');
  const statusFilters = document.querySelectorAll('.status-filter');
  const rows = detailsRoot.querySelectorAll('li.row');
  const sections = detailsRoot.querySelectorAll('section.slice');

  function refresh() {
    const q = (input?.value || '').toLowerCase().trim();
    const allowed = new Set();
    statusFilters.forEach(cb => { if (cb.checked) allowed.add(cb.dataset.s); });

    const visiblePerSection = new WeakMap();
    rows.forEach(li => {
      const path = (li.dataset.path || '').toLowerCase();
      const s = li.dataset.s;
      const match = (!q || path.includes(q)) && allowed.has(s);
      li.classList.toggle('hidden', !match);
      if (match) {
        const sec = li.closest('section.slice');
        if (sec) visiblePerSection.set(sec, (visiblePerSection.get(sec) || 0) + 1);
      }
    });
    sections.forEach(sec => {
      const visible = (visiblePerSection.get(sec) || 0) > 0;
      sec.classList.toggle('hidden', !visible);
    });
  }

  input?.addEventListener('input', refresh);
  statusFilters.forEach(cb => cb.addEventListener('change', refresh));
  // Run once on load to apply default filter (FAIL only).
  refresh();
})();
