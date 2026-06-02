/*
 * karate-image report ext — client-side renderer for the `image-comparison` embed.
 *
 * Core emits the multi-part embed { name:"image-comparison",
 *   parts:[{role:"baseline"|"latest"|"diff", ...}], meta } and delegates rendering here via
 * KarateReport.registerEmbed (see EXT.md § Embeds). Core already defers the WHOLE renderer
 * until the embed scrolls into view, so this draws a compact thumbnail + a <dialog> lightbox.
 *
 * Lightbox = hybrid tabs. The Diff tab is editable (a "Tune" toggle reveals live re-diff
 * controls + ignore-box authoring); Side / Slider / Blink / Onion are read-only views. Heavy
 * work is deferred to lightbox-open: full-res images load and the vendored (patched)
 * Resemble.js is fetched, then the live re-diff repaints as you change options/boxes. "Show
 * options" emits the minimal tuning JSON; "Rebase" emits a copy-paste command — both via the
 * absolute paths the recipe put in meta.
 *
 * Wire-shape source of truth is ImageApi (embed/meta): roles baseline/latest/diff; meta carries
 * name, pass, mismatch, mismatchPercentage, threshold/defaultThreshold, engine/defaultEngine,
 * baselineEstablished, ignoredBoxes, and baselinePath/latestPath/optionsPath. Styling is
 * hand-authored + scoped under .k-image-ext (image.css) — not Tailwind (see EXT.md).
 */
(function () {
    'use strict';

    // Vendored next to this script (ReportAssets.asset). image.js lives at ext/image/image.js
    // and runs from feature-html/*.html, so the report-relative path is ../ext/image/.
    var RESEMBLE_URL = '../ext/image/resemble.js';

    var KarateImage = {
        _seq: 0,
        _data: {},            // dialogId -> view model
        _resemble: null,      // null | 'loading' | 'ready' | 'failed'
        _resembleCbs: [],     // callbacks waiting for resemble to load

        // ---- rendering (called by core via registerEmbed, only when visible) ----
        render: function (embed, api) {
            var parts = embed.parts || [];
            var srcOf = function (role) {
                var p = parts.find(function (x) { return x.role === role; });
                return p ? api._embedPartSrc(p) : null;
            };
            var meta = embed.meta || {};
            var id = 'kimg-' + (this._seq++);
            var pct = (typeof meta.mismatchPercentage === 'number') ? meta.mismatchPercentage : 0;
            this._data[id] = {
                baseline: srcOf('baseline'), latest: srcOf('latest'), diff: srcOf('diff'),
                name: meta.name || embed.name || 'image',
                established: meta.baselineEstablished === true,
                pass: meta.pass !== false,
                pct: pct,
                threshold: typeof meta.threshold === 'number' ? meta.threshold : 0,
                defaultThreshold: typeof meta.defaultThreshold === 'number' ? meta.defaultThreshold : 0,
                engine: meta.engine || 'resemble',
                defaultEngine: meta.defaultEngine || 'resemble',
                ignore: meta.ignore || 'less',
                boxes: (meta.ignoredBoxes || []).map(function (b, i) {
                    return { id: i, top: b.top, left: b.left, bottom: b.bottom, right: b.right };
                }),
                paths: { baseline: meta.baselinePath, latest: meta.latestPath, options: meta.optionsPath },
                rebaseCommand: meta.rebaseCommand || null,
                optionsCommand: meta.optionsCommand || null,
                resembleControl: null, tune: false, zoom: 'fit', errorType: null, errorColor: null,
                transparency: null, boxSeq: 0, activeBox: null
            };
            return this._cardHtml(id, api);
        },

        _cardHtml: function (id, api) {
            var d = this._data[id];
            var esc = api._esc.bind(api);
            var status = d.established ? 'established' : (d.pass ? 'pass' : 'fail');
            var badge = d.established ? 'baseline established'
                : (d.pass ? 'match' : 'mismatch ' + d.pct.toFixed(2) + '%');
            var thumb = d.diff || d.latest || d.baseline;

            var h = '<div class="k-image-ext ki-card ki-' + status + '">';
            h += '<button type="button" class="ki-thumb" onclick="KarateImage.open(\'' + id + '\')" title="Open image comparison">';
            h += thumb ? '<img src="' + thumb + '" alt="' + esc(d.name) + '">' : '<span class="ki-noimg">no image</span>';
            h += '</button>';
            h += '<div class="ki-meta">';
            h += '<span class="ki-badge ki-badge-' + status + '">' + badge + '</span>';
            h += '<span class="ki-name">' + esc(d.name) + '</span>';
            if (!d.established) {
                h += '<span class="ki-sub">' + d.pct.toFixed(2) + '% diff · threshold ' + d.threshold
                    + ' · ' + esc(String(d.engine)) + '</span>';
            }
            h += '</div>';
            h += this._dialogHtml(id, esc);
            h += '</div>';
            return h;
        },

        _dialogHtml: function (id, esc) {
            var d = this._data[id];
            var TABS = [['diff', 'Diff'], ['side', 'Side by side'], ['slider', 'Slider'],
                ['blink', 'Blink'], ['onion', 'Onion skin']];
            var h = '<dialog id="' + id + '" class="k-image-ext ki-dialog">';
            // header
            h += '<header class="ki-head"><strong>' + esc(d.name) + '</strong>';
            h += '<nav class="ki-tabs">';
            TABS.forEach(function (t, i) {
                h += '<button type="button" data-mode="' + t[0] + '" class="ki-tab' + (i === 0 ? ' ki-tab-on' : '')
                    + '" onclick="KarateImage.mode(\'' + id + '\',\'' + t[0] + '\')">' + t[1] + '</button>';
            });
            h += '</nav>';
            h += '<button type="button" class="ki-close" onclick="KarateImage.close(\'' + id + '\')" aria-label="Close">&times;</button>';
            h += '</header>';

            h += '<div class="ki-body">';

            // --- Diff tab (editable) ---
            h += '<div class="ki-pane ki-pane-diff" data-pane="diff">';
            h += '<div class="ki-diffwrap">';
            h += '<div class="ki-toolbar">';
            h += '<button type="button" class="ki-toggle" onclick="KarateImage.toggleTune(\'' + id + '\')">Tune</button>';
            h += '<button type="button" class="ki-zoom" onclick="KarateImage.toggleZoom(\'' + id + '\')">100%</button>';
            h += '<span class="ki-spacer"></span>';
            h += '<button type="button" class="ki-act" onclick="KarateImage.showOptions(\'' + id + '\')">Show options</button>';
            h += '<button type="button" class="ki-act" onclick="KarateImage.rebase(\'' + id + '\')">Rebase</button>';
            h += '</div>';
            // stage: the (precomputed, then live) diff image + an editable ignore-box layer
            h += '<div class="ki-stage ki-stage-fit"><div class="ki-canvas">';
            h += d.diff ? '<img class="ki-diffimg" data-src="' + d.diff + '" alt="diff">'
                : '<div class="ki-missing">no diff (pass / established)</div>';
            h += '<div class="ki-boxlayer"></div>';
            h += '</div></div>';
            h += '</div>';
            // controls panel (hidden until Tune)
            h += '<aside class="ki-controls" hidden>' + this._controlsHtml(id) + '</aside>';
            h += '</div>';

            // --- read-only views share the full-res images, loaded on open ---
            var img = function (role, label) {
                var s = d[role];
                return '<figure class="ki-fig">'
                    + (s ? '<img class="ki-view ki-view-' + role + '" data-src="' + s + '" alt="' + esc(role) + '">'
                        : '<div class="ki-missing">no ' + role + '</div>')
                    + '<figcaption>' + label + '</figcaption></figure>';
            };
            h += '<div class="ki-pane ki-pane-side" data-pane="side" hidden>'
                + img('baseline', 'Baseline') + img('latest', 'Latest') + img('diff', 'Diff') + '</div>';

            h += '<div class="ki-pane ki-pane-overlay" data-pane="overlay" hidden>';
            h += '<div class="ki-ostage" onpointerdown="KarateImage.dragStart(\'' + id + '\', event)">';
            h += d.baseline ? '<img class="ki-layer ki-layer-base" data-src="' + d.baseline + '" alt="baseline" draggable="false">' : '';
            h += d.latest ? '<img class="ki-layer ki-layer-top" data-src="' + d.latest + '" alt="latest" draggable="false">' : '';
            h += '<div class="ki-divider" hidden><span class="ki-grip"></span></div>';
            h += '</div>';
            h += '<div class="ki-octrls">';
            h += '<label class="ki-range" data-for="slider" hidden>Wipe <input type="range" min="0" max="100" value="50" oninput="KarateImage.slide(\'' + id + '\', this.value)"></label>';
            h += '<label class="ki-range" data-for="onion" hidden>Opacity <input type="range" min="0" max="100" value="50" oninput="KarateImage.onion(\'' + id + '\', this.value)"></label>';
            h += '<span class="ki-hint" data-for="blink" hidden>Blinking baseline ↔ latest…</span>';
            h += '</div>';
            h += '</div>';

            h += '</div>'; // ki-body

            // copy panel (Show options / Rebase output)
            h += '<div class="ki-copy" hidden><div class="ki-copy-head"><span class="ki-copy-title"></span>'
                + '<button type="button" class="ki-copy-btn" onclick="KarateImage.copy(\'' + id + '\')">Copy</button>'
                + '<button type="button" class="ki-copy-x" onclick="KarateImage.hideCopy(\'' + id + '\')">&times;</button></div>'
                + '<pre class="ki-copy-pre"></pre></div>';

            h += '</dialog>';
            return h;
        },

        _controlsHtml: function (id) {
            var sel = function (name, label, opts, cur) {
                var s = '<label class="ki-ctl">' + label + ' <select onchange="KarateImage.setOpt(\'' + id + '\',\'' + name + '\',this.value)">';
                opts.forEach(function (o) {
                    s += '<option value="' + o + '"' + (o === cur ? ' selected' : '') + '>' + o + '</option>';
                });
                return s + '</select></label>';
            };
            var h = '<div class="ki-ctl-title">Live re-diff</div>';
            h += sel('ignore', 'Ignore', ['nothing', 'less', 'colors', 'antialiasing', 'alpha'], 'less');
            h += sel('errorType', 'Error', ['movement', 'flat', 'diffOnly', 'flatDifferenceIntensity', 'movementDifferenceIntensity'], 'movement');
            h += '<label class="ki-ctl">Color '
                + '<button type="button" class="ki-swatch ki-pink" onclick="KarateImage.setColor(\'' + id + '\',\'pink\')"></button>'
                + '<button type="button" class="ki-swatch ki-yellow" onclick="KarateImage.setColor(\'' + id + '\',\'yellow\')"></button></label>';
            h += '<label class="ki-ctl">Transparency <input type="range" min="0" max="100" value="100" oninput="KarateImage.setTransparency(\'' + id + '\',this.value)"></label>';
            h += '<div class="ki-ctl-title">Ignore boxes <button type="button" class="ki-mini" onclick="KarateImage.addBox(\'' + id + '\')">+ add</button></div>';
            h += '<ul class="ki-boxlist"></ul>';
            return h;
        },

        // ---- open / close (defer full-res + resemble to here) ----
        open: function (id) {
            var dlg = document.getElementById(id);
            if (!dlg) return;
            // swap data-src -> src now (full-res images load on open, not at render)
            dlg.querySelectorAll('img[data-src]').forEach(function (im) {
                im.src = im.getAttribute('data-src'); im.removeAttribute('data-src');
            });
            if (typeof dlg.showModal === 'function') dlg.showModal(); else dlg.setAttribute('open', '');
            this.mode(id, 'diff');
        },
        close: function (id) {
            this._stopBlink(id);
            var dlg = document.getElementById(id);
            if (dlg) { dlg.close ? dlg.close() : dlg.removeAttribute('open'); }
        },

        // ---- tab switching ----
        mode: function (id, m) {
            var dlg = document.getElementById(id);
            if (!dlg) return;
            this._stopBlink(id);
            dlg.querySelectorAll('.ki-tab').forEach(function (t) {
                t.classList.toggle('ki-tab-on', t.getAttribute('data-mode') === m);
            });
            var show = function (sel, on) { var el = dlg.querySelector(sel); if (el) el.hidden = !on; };
            show('[data-pane="diff"]', m === 'diff');
            show('[data-pane="side"]', m === 'side');
            var overlay = (m === 'slider' || m === 'blink' || m === 'onion');
            show('[data-pane="overlay"]', overlay);
            if (!overlay) return;
            var top = dlg.querySelector('.ki-layer-top');
            var divider = dlg.querySelector('.ki-divider');
            var stage = dlg.querySelector('.ki-ostage');
            if (top) { top.style.opacity = ''; top.style.clipPath = ''; }
            if (divider) divider.hidden = true;
            if (stage) stage.classList.toggle('ki-stage-wipe', m === 'slider');
            dlg.querySelectorAll('.ki-octrls [data-for]').forEach(function (c) {
                c.hidden = (c.getAttribute('data-for') !== m);
            });
            if (m === 'slider') { divider.hidden = false; this.slide(id, 50); }
            else if (m === 'onion') { this.onion(id, 50); }
            else if (m === 'blink') { this._startBlink(id); }
        },

        // ---- Diff tab: tune + zoom ----
        toggleTune: function (id) {
            var d = this._data[id], dlg = document.getElementById(id);
            d.tune = !d.tune;
            dlg.querySelector('.ki-controls').hidden = !d.tune;
            dlg.querySelector('.ki-pane-diff').classList.toggle('ki-tuning', d.tune);
            dlg.querySelector('.ki-toggle').classList.toggle('ki-on', d.tune);
            if (d.tune) { this._loadResemble(id, function () { KarateImage._liveDiff(id); }); this._renderBoxes(id); }
        },
        toggleZoom: function (id) {
            var d = this._data[id], dlg = document.getElementById(id);
            d.zoom = d.zoom === 'fit' ? 'full' : 'fit';
            var stage = dlg.querySelector('.ki-stage');
            stage.classList.toggle('ki-stage-fit', d.zoom === 'fit');
            stage.classList.toggle('ki-stage-full', d.zoom === 'full');
            dlg.querySelector('.ki-zoom').textContent = d.zoom === 'fit' ? '100%' : 'Fit';
            this._renderBoxes(id);
        },

        // ---- live re-diff (resemble) ----
        setOpt: function (id, k, v) { this._data[id][k] = v; this._liveDiff(id); },
        setColor: function (id, c) {
            this._data[id].errorColor = c === 'pink' ? { red: 255, green: 0, blue: 255 } : { red: 255, green: 255, blue: 0 };
            this._liveDiff(id);
        },
        setTransparency: function (id, v) { this._data[id].transparency = Number(v) / 100; this._liveDiff(id); },

        _liveDiff: function (id) {
            var d = this._data[id], dlg = document.getElementById(id);
            if (!d.tune || this._resemble !== 'ready' || !window.resemble || !d.baseline || !d.latest) return;
            var diffImg = dlg.querySelector('.ki-diffimg');
            if (!diffImg) return;
            var ctl = window.resemble(d.latest).compareTo(d.baseline);
            switch (d.ignore) {
                case 'nothing': ctl.ignoreNothing(); break;
                case 'colors': ctl.ignoreColors(); break;
                case 'antialiasing': ctl.ignoreAntialiasing(); break;
                case 'alpha': ctl.ignoreAlpha(); break;
                default: ctl.ignoreLess();
            }
            var out = { ignoredBoxes: d.boxes.map(function (b) { return { top: b.top, left: b.left, bottom: b.bottom, right: b.right }; }) };
            if (d.errorType) out.errorType = d.errorType;
            if (d.errorColor) out.errorColor = d.errorColor;
            if (d.transparency != null) out.transparency = d.transparency;
            ctl.outputSettings(out).onComplete(function (data) {
                if (data && data.getImageDataUrl) diffImg.src = data.getImageDataUrl();
            });
            d.resembleControl = ctl;
        },

        _loadResemble: function (id, cb) {
            if (this._resemble === 'ready') return cb && cb();
            if (cb) this._resembleCbs.push(cb);
            if (this._resemble) return;          // already loading/failed
            this._resemble = 'loading';
            var self = this;
            var s = document.createElement('script');
            s.src = RESEMBLE_URL; s.async = true;
            s.onload = function () { self._resemble = 'ready'; self._resembleCbs.splice(0).forEach(function (f) { f(); }); };
            s.onerror = function () { self._resemble = 'failed'; };
            document.head.appendChild(s);
        },

        // ---- ignore-box authoring (compact pointer-events drag/resize) ----
        addBox: function (id) {
            var d = this._data[id];
            d.boxes.push({ id: d.boxSeq++, left: 10, top: 10, right: 110, bottom: 90 });
            this._renderBoxes(id); this._liveDiff(id);
        },
        removeBox: function (id, boxId) {
            var d = this._data[id];
            d.boxes = d.boxes.filter(function (b) { return b.id !== boxId; });
            this._renderBoxes(id); this._liveDiff(id);
        },
        _scale: function (id) {
            var dlg = document.getElementById(id), img = dlg.querySelector('.ki-diffimg');
            if (!img || !img.naturalWidth) return 1;
            return img.clientWidth / img.naturalWidth;
        },
        _renderBoxes: function (id) {
            var d = this._data[id], dlg = document.getElementById(id);
            var layer = dlg.querySelector('.ki-boxlayer'), list = dlg.querySelector('.ki-boxlist');
            if (!layer) return;
            var sc = this._scale(id);
            layer.innerHTML = '';
            d.boxes.forEach(function (b) {
                var el = document.createElement('div');
                el.className = 'ki-box'; el.setAttribute('data-box', b.id);
                el.style.left = (b.left * sc) + 'px'; el.style.top = (b.top * sc) + 'px';
                el.style.width = ((b.right - b.left) * sc) + 'px'; el.style.height = ((b.bottom - b.top) * sc) + 'px';
                ['nw', 'ne', 'sw', 'se'].forEach(function (h) {
                    var hd = document.createElement('span'); hd.className = 'ki-h ki-h-' + h; hd.setAttribute('data-h', h);
                    el.appendChild(hd);
                });
                el.addEventListener('pointerdown', function (ev) { KarateImage._boxDown(id, b.id, ev); });
                layer.appendChild(el);
            });
            if (list) {
                list.innerHTML = '';
                d.boxes.forEach(function (b) {
                    var li = document.createElement('li'); li.className = 'ki-boxitem';
                    li.innerHTML = '<span>' + [b.left, b.top, b.right, b.bottom].map(Math.round).join(', ') + '</span>'
                        + '<button type="button" onclick="KarateImage.removeBox(\'' + id + '\',' + b.id + ')">delete</button>';
                    list.appendChild(li);
                });
            }
        },
        _boxDown: function (id, boxId, ev) {
            ev.preventDefault(); ev.stopPropagation();
            var d = this._data[id], b = d.boxes.find(function (x) { return x.id === boxId; });
            if (!b) return;
            var handle = ev.target.getAttribute('data-h');   // null = move
            var sc = this._scale(id);
            var sx = ev.clientX, sy = ev.clientY, o = { left: b.left, top: b.top, right: b.right, bottom: b.bottom };
            var self = this;
            var move = function (e) {
                var dx = (e.clientX - sx) / sc, dy = (e.clientY - sy) / sc;
                if (!handle) { b.left = o.left + dx; b.right = o.right + dx; b.top = o.top + dy; b.bottom = o.bottom + dy; }
                else {
                    if (handle.indexOf('w') >= 0) b.left = Math.min(o.left + dx, b.right - 5);
                    if (handle.indexOf('e') >= 0) b.right = Math.max(o.right + dx, b.left + 5);
                    if (handle.indexOf('n') >= 0) b.top = Math.min(o.top + dy, b.bottom - 5);
                    if (handle.indexOf('s') >= 0) b.bottom = Math.max(o.bottom + dy, b.top + 5);
                }
                self._renderBoxes(id);
            };
            var up = function () {
                window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up);
                self._liveDiff(id);
            };
            window.addEventListener('pointermove', move); window.addEventListener('pointerup', up);
        },

        // ---- actions: show options / rebase (command templates) ----
        showOptions: function (id) {
            var d = this._data[id], o = {};
            if (d.engine !== d.defaultEngine) o.engine = d.engine;
            if (d.threshold !== d.defaultThreshold) o.threshold = d.threshold;
            if (d.ignore && d.ignore !== 'less') o.ignore = d.ignore;
            if (d.errorType) o.errorType = d.errorType;
            if (d.boxes.length) o.ignoredBoxes = d.boxes.map(function (b) {
                return { top: Math.round(b.top), left: Math.round(b.left), bottom: Math.round(b.bottom), right: Math.round(b.right) };
            });
            var json = JSON.stringify(o, null, 2);
            var tmpl = d.optionsCommand || ('cat <<EOF > ${optionsPath}\n${json}\nEOF');
            this._showCopy(id, 'Write options', this._fill(tmpl, d.paths, json));
        },
        rebase: function (id) {
            var d = this._data[id];
            var tmpl = d.rebaseCommand || 'cp ${latestPath} ${baselinePath}';
            this._showCopy(id, 'Rebase command', this._fill(tmpl, d.paths, null));
        },
        _fill: function (tmpl, paths, json) {
            return String(tmpl)
                .replace(/\$\{baselinePath\}/g, paths.baseline || '<baselinePath>')
                .replace(/\$\{latestPath\}/g, paths.latest || '<latestPath>')
                .replace(/\$\{optionsPath\}/g, paths.options || '<optionsPath>')
                .replace(/\$\{json\}/g, json == null ? '' : json);
        },
        _showCopy: function (id, title, text) {
            var dlg = document.getElementById(id), box = dlg.querySelector('.ki-copy');
            box.querySelector('.ki-copy-title').textContent = title;
            box.querySelector('.ki-copy-pre').textContent = text;
            box.hidden = false;
        },
        hideCopy: function (id) { document.getElementById(id).querySelector('.ki-copy').hidden = true; },
        copy: function (id) {
            var pre = document.getElementById(id).querySelector('.ki-copy-pre');
            var txt = pre.textContent;
            if (navigator.clipboard) { navigator.clipboard.writeText(txt).catch(function () { }); }
            else {
                var ta = document.createElement('textarea'); ta.value = txt; document.body.appendChild(ta);
                ta.select(); try { document.execCommand('copy'); } catch (e) { } document.body.removeChild(ta);
            }
        },

        // ---- read-only overlay views (slider / onion / blink) ----
        slide: function (id, v) {
            v = Math.max(0, Math.min(100, Number(v)));
            var dlg = document.getElementById(id); if (!dlg) return;
            var top = dlg.querySelector('.ki-layer-top'), divider = dlg.querySelector('.ki-divider');
            var range = dlg.querySelector('.ki-range[data-for="slider"] input');
            if (top) top.style.clipPath = 'inset(0 ' + (100 - v) + '% 0 0)';
            if (divider) divider.style.left = v + '%';
            if (range && Number(range.value) !== v) range.value = v;
        },
        dragStart: function (id, ev) {
            var dlg = document.getElementById(id), stage = dlg && dlg.querySelector('.ki-ostage');
            if (!stage || !stage.classList.contains('ki-stage-wipe')) return;
            ev.preventDefault();
            var self = this;
            var pct = function (e) {
                var r = stage.getBoundingClientRect();
                return r.width ? (e.clientX - r.left) / r.width * 100 : 50;
            };
            self.slide(id, pct(ev));
            var move = function (e) { self.slide(id, pct(e)); };
            var up = function () { stage.removeEventListener('pointermove', move); stage.removeEventListener('pointerup', up); };
            stage.addEventListener('pointermove', move); stage.addEventListener('pointerup', up);
        },
        onion: function (id, v) {
            var top = document.getElementById(id).querySelector('.ki-layer-top');
            if (top) top.style.opacity = (Number(v) / 100);
        },
        _blinkTimers: {},
        _startBlink: function (id) {
            var top = document.getElementById(id).querySelector('.ki-layer-top');
            if (!top) return;
            var on = true; top.style.opacity = '1';
            this._blinkTimers[id] = setInterval(function () { on = !on; top.style.opacity = on ? '1' : '0'; }, 600);
        },
        _stopBlink: function (id) {
            if (this._blinkTimers[id]) { clearInterval(this._blinkTimers[id]); delete this._blinkTimers[id]; }
            var top = document.getElementById(id) && document.getElementById(id).querySelector('.ki-layer-top');
            if (top) top.style.opacity = '';
        }
    };

    window.KarateImage = KarateImage;

    if (window.KarateReport && typeof window.KarateReport.registerEmbed === 'function') {
        window.KarateReport.registerEmbed('image-comparison', function (embed, api) {
            return KarateImage.render(embed, api);
        });
    }
})();
