/*
 * karate-image report ext — client-side renderer for the `image-comparison` embed.
 *
 * Registers a renderer with core's report JS (KarateReport.registerEmbed, see
 * IMAGE_SPIKE.md §3.8a). Core emits the multi-part embed
 *   { name:"image-comparison", parts:[{role:"baseline"|"latest"|"diff", ...}], meta }
 * and delegates its rendering here. We draw a compact thumbnail + status badge that
 * opens a <dialog> lightbox with side-by-side / slider / blink / onion-skin tools.
 *
 * Wire-shape source of truth is ImageApi.java emit(...)/meta(...): roles are
 * baseline/latest/diff (NOT current); meta carries name, mismatchPercentage,
 * threshold, engine, passed, and (when a baseline was just created) baselineEstablished.
 *
 * Styling is hand-authored + scoped under .k-image-ext (IMAGE_SPIKE.md O6, channel b);
 * core runs no Tailwind build over this file. Resemble.js is lazy-loaded from CDN on
 * first lightbox open (D11) to power an optional live-diff view; the server already
 * wrote the precomputed diff PNG, so the core tools work with no network.
 */
(function () {
    'use strict';

    var RESEMBLE_CDN = 'https://unpkg.com/resemblejs@5.0.0/resemble.js';

    var KarateImage = {
        _seq: 0,
        _data: {},          // dialogId -> { baseline, latest, diff, name, established, passed, pct }
        _resemble: null,    // 'loading' | 'ready' | 'failed' | null

        // ---- rendering (called by core via registerEmbed) ----
        render: function (embed, api) {
            var parts = embed.parts || [];
            function src(role) {
                var p = parts.find(function (x) { return x.role === role; });
                return p ? api._embedPartSrc(p) : null;
            }
            var baseline = src('baseline'), latest = src('latest'), diff = src('diff');
            var meta = embed.meta || {};
            var established = meta.baselineEstablished === true;
            var passed = meta.passed !== false;
            var pct = (typeof meta.mismatchPercentage === 'number') ? meta.mismatchPercentage : 0;
            var name = meta.name || embed.name || 'image';

            var status = established ? 'established' : (passed ? 'pass' : 'fail');
            var badge = established ? 'baseline established'
                : (passed ? 'match' : 'mismatch ' + pct.toFixed(2) + '%');
            var thumb = diff || latest || baseline;

            var id = 'kimg-' + (this._seq++);
            this._data[id] = {
                baseline: baseline, latest: latest, diff: diff,
                name: name, established: established, passed: passed, pct: pct
            };
            var esc = api._esc.bind(api);

            var h = '<div class="k-image-ext ki-card ki-' + status + '">';
            h += '<button type="button" class="ki-thumb" onclick="KarateImage.open(\'' + id + '\')" '
                + 'title="Open image comparison">';
            if (thumb) {
                h += '<img src="' + thumb + '" alt="' + esc(name) + '">';
            } else {
                h += '<span class="ki-noimg">no image</span>';
            }
            h += '</button>';
            h += '<div class="ki-meta">';
            h += '<span class="ki-badge ki-badge-' + status + '">' + badge + '</span>';
            h += '<span class="ki-name">' + esc(name) + '</span>';
            if (!established) {
                h += '<span class="ki-sub">' + pct.toFixed(2) + '% diff'
                    + (typeof meta.threshold === 'number' ? ' · threshold ' + meta.threshold : '')
                    + (meta.engine ? ' · ' + esc(String(meta.engine)) : '') + '</span>';
            }
            h += '</div>';
            h += this._dialogHtml(id, esc);
            h += '</div>';
            return h;
        },

        _dialogHtml: function (id, esc) {
            var d = this._data[id];
            var img = function (role, label) {
                var s = d[role];
                return '<figure class="ki-fig">'
                    + (s ? '<img class="ki-view ki-view-' + role + '" src="' + s + '" alt="' + esc(role) + '">'
                        : '<div class="ki-missing">no ' + role + '</div>')
                    + '<figcaption>' + label + '</figcaption></figure>';
            };
            var h = '<dialog id="' + id + '" class="k-image-ext ki-dialog">';
            h += '<header class="ki-head">';
            h += '<strong>' + esc(d.name) + '</strong>';
            h += '<nav class="ki-tabs">';
            h += '<button type="button" data-mode="side" class="ki-tab ki-tab-on" onclick="KarateImage.mode(\'' + id + '\',\'side\')">Side by side</button>';
            h += '<button type="button" data-mode="slider" class="ki-tab" onclick="KarateImage.mode(\'' + id + '\',\'slider\')">Slider</button>';
            h += '<button type="button" data-mode="blink" class="ki-tab" onclick="KarateImage.mode(\'' + id + '\',\'blink\')">Blink</button>';
            h += '<button type="button" data-mode="onion" class="ki-tab" onclick="KarateImage.mode(\'' + id + '\',\'onion\')">Onion skin</button>';
            h += '</nav>';
            h += '<button type="button" class="ki-close" onclick="KarateImage.close(\'' + id + '\')" aria-label="Close">&times;</button>';
            h += '</header>';

            h += '<div class="ki-body">';
            // side-by-side
            h += '<div class="ki-pane ki-pane-side" data-pane="side">'
                + img('baseline', 'Baseline') + img('latest', 'Latest') + img('diff', 'Diff') + '</div>';
            // overlay pane reused by slider / blink / onion
            h += '<div class="ki-pane ki-pane-overlay" data-pane="overlay" hidden>';
            h += '<div class="ki-stage" onpointerdown="KarateImage.dragStart(\'' + id + '\', event)">';
            h += d.baseline ? '<img class="ki-layer ki-layer-base" src="' + d.baseline + '" alt="baseline" draggable="false">' : '';
            h += d.latest ? '<img class="ki-layer ki-layer-top" src="' + d.latest + '" alt="latest" draggable="false">' : '';
            h += '<div class="ki-divider" hidden><span class="ki-grip"></span></div>';
            h += '</div>';
            h += '<div class="ki-controls">';
            h += '<label class="ki-range" data-for="slider" hidden>Wipe <input type="range" min="0" max="100" value="50" oninput="KarateImage.slide(\'' + id + '\', this.value)"></label>';
            h += '<label class="ki-range" data-for="onion" hidden>Opacity <input type="range" min="0" max="100" value="50" oninput="KarateImage.onion(\'' + id + '\', this.value)"></label>';
            h += '<span class="ki-hint" data-for="blink" hidden>Blinking baseline ↔ latest…</span>';
            h += '</div>';
            h += '</div>';
            h += '</div>';
            h += '</dialog>';
            return h;
        },

        // ---- interaction ----
        open: function (id) {
            var dlg = document.getElementById(id);
            if (dlg && typeof dlg.showModal === 'function') {
                dlg.showModal();
            } else if (dlg) {
                dlg.setAttribute('open', '');
            }
            this._loadResemble();
        },
        close: function (id) {
            this._stopBlink(id);
            var dlg = document.getElementById(id);
            if (dlg) { dlg.close ? dlg.close() : dlg.removeAttribute('open'); }
        },

        mode: function (id, m) {
            var dlg = document.getElementById(id);
            if (!dlg) return;
            this._stopBlink(id);
            dlg.querySelectorAll('.ki-tab').forEach(function (t) {
                t.classList.toggle('ki-tab-on', t.getAttribute('data-mode') === m);
            });
            var side = dlg.querySelector('[data-pane="side"]');
            var overlay = dlg.querySelector('[data-pane="overlay"]');
            var top = dlg.querySelector('.ki-layer-top');
            var divider = dlg.querySelector('.ki-divider');
            side.hidden = (m !== 'side');
            overlay.hidden = (m === 'side');
            dlg.querySelectorAll('.ki-controls [data-for]').forEach(function (c) {
                c.hidden = (c.getAttribute('data-for') !== m);
            });
            var stage = dlg.querySelector('.ki-stage');
            if (top) { top.style.opacity = ''; top.style.clipPath = ''; }
            if (divider) divider.hidden = true;
            if (stage) stage.classList.toggle('ki-stage-wipe', m === 'slider');
            if (m === 'slider') { divider.hidden = false; this.slide(id, 50); }
            else if (m === 'onion') { this.onion(id, 50); }
            else if (m === 'blink') { this._startBlink(id); }
        },

        slide: function (id, v) {
            v = Math.max(0, Math.min(100, Number(v)));
            var dlg = document.getElementById(id);
            if (!dlg) return;
            var top = dlg.querySelector('.ki-layer-top');
            var divider = dlg.querySelector('.ki-divider');
            var range = dlg.querySelector('.ki-range[data-for="slider"] input');
            if (top) top.style.clipPath = 'inset(0 ' + (100 - v) + '% 0 0)';
            if (divider) divider.style.left = v + '%';
            if (range && Number(range.value) !== v) range.value = v;
        },

        // Drag the wipe directly on the image (the divider bar / anywhere on the stage),
        // in addition to the range input. Only active in slider mode.
        dragStart: function (id, ev) {
            var dlg = document.getElementById(id);
            var stage = dlg && dlg.querySelector('.ki-stage');
            if (!stage || !stage.classList.contains('ki-stage-wipe')) return;
            ev.preventDefault();
            var self = this;
            var pct = function (e) {
                var r = stage.getBoundingClientRect();
                if (!r.width) return 50;
                return (e.clientX - r.left) / r.width * 100;
            };
            self.slide(id, pct(ev));
            if (stage.setPointerCapture && ev.pointerId != null) {
                try { stage.setPointerCapture(ev.pointerId); } catch (e) { /* ignore */ }
            }
            var move = function (e) { self.slide(id, pct(e)); };
            var up = function () {
                stage.removeEventListener('pointermove', move);
                stage.removeEventListener('pointerup', up);
                stage.removeEventListener('pointercancel', up);
            };
            stage.addEventListener('pointermove', move);
            stage.addEventListener('pointerup', up);
            stage.addEventListener('pointercancel', up);
        },
        onion: function (id, v) {
            var dlg = document.getElementById(id);
            var top = dlg && dlg.querySelector('.ki-layer-top');
            if (top) top.style.opacity = (v / 100);
        },

        _blinkTimers: {},
        _startBlink: function (id) {
            var dlg = document.getElementById(id);
            var top = dlg && dlg.querySelector('.ki-layer-top');
            if (!top) return;
            var on = true;
            top.style.opacity = '1';
            this._blinkTimers[id] = setInterval(function () {
                on = !on; top.style.opacity = on ? '1' : '0';
            }, 600);
        },
        _stopBlink: function (id) {
            if (this._blinkTimers[id]) { clearInterval(this._blinkTimers[id]); delete this._blinkTimers[id]; }
            var dlg = document.getElementById(id);
            var top = dlg && dlg.querySelector('.ki-layer-top');
            if (top) top.style.opacity = '';
        },

        // Lazy-load Resemble.js from CDN on first lightbox open (D11). The core
        // slider/blink/onion tools work without it; this just makes the library
        // available for future live-diff use and fulfils the lazy-load contract.
        _loadResemble: function () {
            if (this._resemble) return;
            this._resemble = 'loading';
            var self = this;
            var s = document.createElement('script');
            s.src = RESEMBLE_CDN;
            s.async = true;
            s.onload = function () { self._resemble = 'ready'; };
            s.onerror = function () { self._resemble = 'failed'; };
            document.head.appendChild(s);
        }
    };

    window.KarateImage = KarateImage;

    // Register with core. Runs when this <script defer> executes — typically after
    // Alpine has already rendered, so registerEmbed upgrades the embeds in place.
    if (window.KarateReport && typeof window.KarateReport.registerEmbed === 'function') {
        window.KarateReport.registerEmbed('image-comparison', function (embed, api) {
            return KarateImage.render(embed, api);
        });
    }
})();
