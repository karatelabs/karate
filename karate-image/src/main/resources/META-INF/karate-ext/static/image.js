/*
 * karate-image report ext — client-side renderer for the `image-comparison` embed.
 *
 * Core emits the multi-part embed { name:"image-comparison",
 *   parts:[{role:"baseline"|"latest"|"diff", ...}], meta } and delegates rendering here via
 * KarateReport.registerEmbed (see EXT.md § Embeds). Core defers the WHOLE renderer until the
 * embed scrolls into view, so this draws a compact thumbnail + a <dialog> lightbox.
 *
 * Lightbox = ONE image stage. "Look" is always available: view-toggle buttons (Baseline /
 * Latest / Side by side / Slider / Blink / Onion) act on the same stage; the default (no
 * toggle) is the diff. "Advanced" gates everything else — ignore-box authoring, the live
 * re-diff options (ignore / error type / error color), and the Show options / Rebase write
 * actions — and forces the Diff view since those operate on the diff.
 *
 * Live re-diff re-runs the vendored Resemble.js as you change options/boxes. Resemble reads
 * baseline/latest as base64 data URLs from meta (meta.baselineData / meta.latestData) —
 * canvas-readable, so tuning works even from file:// (file-based <img> would taint the
 * canvas). That base64 lives ONLY on this embed's meta; normal screenshots stay file-based.
 * Styling is hand-authored + scoped under .k-image-ext (image.css), not Tailwind.
 */
(function () {
    'use strict';

    var RESEMBLE_URL = '../ext/image/resemble.js';   // vendored next to this script
    // the primary slot's mode; "Side by side" is a separate toggle that pins baseline+latest beside it
    var VIEWS = [['diff', 'Diff'], ['slider', 'Slider'], ['blink', 'Blink'], ['onion', 'Onion']];

    var KarateImage = {
        _seq: 0,
        _data: {},
        _resemble: null,          // null | 'loading' | 'ready' | 'failed'
        _resembleCbs: [],
        _blinkTimers: {},

        // ---- render (called by core via registerEmbed, only when visible) ----
        render: function (embed, api) {
            var parts = embed.parts || [];
            var fileSrc = function (role) {
                var p = parts.find(function (x) { return x.role === role; });
                return p ? api._embedPartSrc(p) : null;
            };
            var meta = embed.meta || {};
            var id = 'kimg-' + (this._seq++);
            var pct = (typeof meta.mismatchPercentage === 'number') ? meta.mismatchPercentage : 0;
            var boxes = (meta.ignoredBoxes || []).map(function (b, i) {
                return { id: i, top: b.top, left: b.left, bottom: b.bottom, right: b.right };
            });
            this._data[id] = {
                name: meta.name || embed.name || 'image',
                established: meta.baselineEstablished === true,
                pass: meta.pass !== false,
                pct: pct,
                threshold: typeof meta.threshold === 'number' ? meta.threshold : 0,
                defaultThreshold: typeof meta.defaultThreshold === 'number' ? meta.defaultThreshold : 0,
                engine: meta.engine || 'resemble',
                defaultEngine: meta.defaultEngine || 'resemble',
                ignore: meta.ignore || 'less',
                diffFile: fileSrc('diff'),
                baseData: meta.baselineData || fileSrc('baseline'),
                lateData: meta.latestData || fileSrc('latest'),
                boxes: boxes,
                paths: { baseline: meta.baselinePath, latest: meta.latestPath, options: meta.optionsPath },
                rebaseCommand: meta.rebaseCommand || null,
                optionsCommand: meta.optionsCommand || null,
                view: 'diff', side: false, advanced: false, zoom: 'fit',
                errorType: null, errorColor: null, liveDiff: null, boxSeq: boxes.length
            };
            return this._cardHtml(id, api);
        },

        _cardHtml: function (id, api) {
            var d = this._data[id], esc = api._esc.bind(api);
            var status = d.established ? 'established' : (d.pass ? 'pass' : 'fail');
            var badge = d.established ? 'baseline established' : (d.pass ? 'match' : 'mismatch ' + d.pct.toFixed(2) + '%');
            var thumb = d.diffFile || d.lateData || d.baseData;
            var h = '<div class="k-image-ext ki-card ki-' + status + '">';
            h += '<button type="button" class="ki-thumb" onclick="KarateImage.open(\'' + id + '\')" title="Open image comparison">';
            h += thumb ? '<img src="' + thumb + '" alt="' + esc(d.name) + '">' : '<span class="ki-noimg">no image</span>';
            h += '</button>';
            h += '<div class="ki-meta"><span class="ki-badge ki-badge-' + status + '">' + badge + '</span>';
            h += '<span class="ki-name">' + esc(d.name) + '</span>';
            if (!d.established) {
                h += '<span class="ki-sub">' + d.pct.toFixed(2) + '% diff · threshold ' + d.threshold + ' · ' + esc(String(d.engine)) + '</span>';
            }
            h += '</div>' + this._dialogHtml(id, esc) + '</div>';
            return h;
        },

        _dialogHtml: function (id, esc) {
            var d = this._data[id];
            var h = '<dialog id="' + id + '" class="k-image-ext ki-dialog">';
            // header: title | views | spacer | Advanced | zoom | (adv) Show options / Rebase | close
            h += '<header class="ki-head"><strong>' + esc(d.name) + '</strong>';
            h += '<span class="ki-views">';
            VIEWS.forEach(function (v) {
                h += '<button type="button" class="ki-vbtn" data-view="' + v[0] + '" onclick="KarateImage.setView(\'' + id + '\',\'' + v[0] + '\')">' + v[1] + '</button>';
            });
            // opacity slider sits next to the view buttons, shown only for Onion (Slider is
            // draggable on the image, so it needs no control)
            h += '<label class="ki-range" data-for="blend" hidden>Opacity <input type="range" min="0" max="100" value="50" oninput="KarateImage.blend(\'' + id + '\', this.value)"></label>';
            // right-aligned control group (one container with margin-left:auto). Advanced is a
            // single CSS class on the dialog (.ki-advanced) that reveals every .ki-adv element.
            h += '<span class="ki-right">';
            h += '<button type="button" class="ki-sidebtn" onclick="KarateImage.toggleSide(\'' + id + '\')">Side by side</button>';
            h += '<button type="button" class="ki-toggle" onclick="KarateImage.toggleAdvanced(\'' + id + '\')">Advanced</button>';
            h += '<button type="button" class="ki-zoom" onclick="KarateImage.toggleZoom(\'' + id + '\')">100%</button>';
            h += '<button type="button" class="ki-close" onclick="KarateImage.close(\'' + id + '\')" aria-label="Close">&times;</button>';
            h += '</span>';
            h += '</header>';

            // edit bar — entirely advanced (.ki-adv): row 1 = re-diff options, row 2 = boxes + write actions
            h += '<div class="ki-bar ki-adv">';
            h += '<div class="ki-bar-row">';
            h += this._sel(id, 'ignore', 'Ignore', ['nothing', 'less', 'colors', 'antialiasing', 'alpha'], 'less');
            h += this._sel(id, 'errorType', 'Error', ['movement', 'flat', 'diffOnly', 'flatDifferenceIntensity', 'movementDifferenceIntensity'], 'movement');
            h += 'Color <button type="button" class="ki-swatch ki-pink" title="pink" onclick="KarateImage.setColor(\'' + id + '\',\'pink\')"></button>';
            h += '<button type="button" class="ki-swatch ki-yellow" title="yellow" onclick="KarateImage.setColor(\'' + id + '\',\'yellow\')"></button>';
            h += '</div>';
            h += '<div class="ki-bar-row">';
            h += '<button type="button" class="ki-act" onclick="KarateImage.showOptions(\'' + id + '\')">Show options</button>';
            h += '<button type="button" class="ki-act" onclick="KarateImage.rebase(\'' + id + '\')">Rebase</button>';
            h += ' · Ignore boxes <button type="button" class="ki-mini" onclick="KarateImage.addBox(\'' + id + '\')">+ add</button>';
            h += '<ul class="ki-boxlist"></ul>';
            h += '</div>';
            h += '<span class="ki-notice"></span>';
            h += '</div>';

            h += '<div class="ki-body"><div class="ki-stage ki-stage-fit">'
                + '<div class="ki-primary"><div class="ki-canvas"></div></div>'
                + '<div class="ki-aside" hidden></div></div></div>';

            h += '<div class="ki-copy" hidden><div class="ki-copy-head"><span class="ki-copy-title"></span>'
                + '<button type="button" class="ki-copy-btn" onclick="KarateImage.copy(\'' + id + '\')">Copy</button>'
                + '<button type="button" class="ki-copy-x" onclick="KarateImage.hideCopy(\'' + id + '\')">&times;</button></div>'
                + '<pre class="ki-copy-pre"></pre></div>';
            h += '</dialog>';
            return h;
        },

        _sel: function (id, name, label, opts, cur) {
            var s = label + ' <select onchange="KarateImage.setOpt(\'' + id + '\',\'' + name + '\',this.value)">';
            opts.forEach(function (o) { s += '<option value="' + o + '"' + (o === cur ? ' selected' : '') + '>' + o + '</option>'; });
            return s + '</select> ';
        },

        // ---- open / close ----
        open: function (id) {
            var dlg = document.getElementById(id);
            if (!dlg) return;
            if (typeof dlg.showModal === 'function') dlg.showModal(); else dlg.setAttribute('open', '');
            this.setView(id, 'diff');
            this.renderAside(id);
        },
        close: function (id) {
            this._stopBlink(id);
            var dlg = document.getElementById(id);
            if (dlg) { dlg.close ? dlg.close() : dlg.removeAttribute('open'); }
        },

        // ---- Advanced (editing) toggle ----
        // Advanced is just a CSS class on the dialog (.ki-advanced) — CSS reveals every
        // .ki-adv element. No per-element JS toggling, no forcing the view, no recursion:
        // view / side / advanced are fully orthogonal. Editing still happens on the Diff view
        // (the box layer + live re-diff only render there).
        toggleAdvanced: function (id) {
            var dlg = document.getElementById(id), d = this._data[id];
            d.advanced = dlg.classList.toggle('ki-advanced');
            dlg.querySelector('.ki-toggle').classList.toggle('ki-on', d.advanced);
            if (d.advanced && this._resemble !== 'ready') this._loadResemble(id, function () { KarateImage._liveDiff(id); });
            this.setView(id, d.view);   // re-render: the Diff view gains/loses its box layer
        },
        // ---- Side-by-side toggle: pin baseline+latest to the right of the primary ----
        toggleSide: function (id) {
            var d = this._data[id];
            d.side = !d.side;
            document.getElementById(id).querySelector('.ki-sidebtn').classList.toggle('ki-on', d.side);
            this.renderAside(id);
        },
        renderAside: function (id) {
            var d = this._data[id], aside = document.getElementById(id).querySelector('.ki-aside');
            if (!aside) return;
            if (d.side) {
                aside.innerHTML = this._fig('Baseline', d.baseData) + this._fig('Latest', d.lateData);
                aside.hidden = false;
            } else {
                aside.innerHTML = ''; aside.hidden = true;
            }
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

        // ---- view switching on the single stage ----
        setView: function (id, mode) {
            var d = this._data[id], dlg = document.getElementById(id);
            if (!dlg) return;
            this._stopBlink(id);
            d.view = mode;
            dlg.querySelectorAll('.ki-vbtn').forEach(function (b) {
                b.classList.toggle('ki-on', b.getAttribute('data-view') === mode);
            });
            var blend = dlg.querySelector('.ki-range[data-for="blend"]');
            if (blend) blend.hidden = (mode !== 'onion');   // slider drags on the image

            var canvas = dlg.querySelector('.ki-canvas');
            canvas.onpointerdown = null;
            canvas.classList.remove('ki-canvas-wipe');
            var two = (mode === 'slider' || mode === 'blink' || mode === 'onion');
            if (two) {
                canvas.innerHTML =
                    (d.baseData ? '<img class="ki-img ki-layer ki-layer-base" src="' + d.baseData + '" draggable="false" alt="baseline">' : '')
                    + (d.lateData ? '<img class="ki-img ki-layer ki-layer-top" src="' + d.lateData + '" draggable="false" alt="latest">' : '')
                    + '<div class="ki-divider" hidden><span class="ki-grip"></span></div>';
                var top = canvas.querySelector('.ki-layer-top');
                if (top) { top.style.opacity = ''; top.style.clipPath = ''; }
                if (mode === 'slider') { canvas.classList.add('ki-canvas-wipe'); canvas.querySelector('.ki-divider').hidden = false; this.blend(id, 50); canvas.onpointerdown = function (ev) { KarateImage._wipeDrag(id, ev); }; }
                else if (mode === 'onion') { this.blend(id, 50); }
                else { this._startBlink(id); }
            } else {
                var src = mode === 'baseline' ? d.baseData : mode === 'latest' ? d.lateData
                    : (d.liveDiff || d.diffFile || d.lateData);
                canvas.innerHTML = src ? '<img class="ki-img" src="' + src + '" draggable="false" alt="' + mode + '">'
                    : '<div class="ki-missing">no ' + mode + '</div>';
                if (mode === 'diff' && d.advanced) {
                    canvas.insertAdjacentHTML('beforeend', '<div class="ki-boxlayer"></div>');
                    this._renderBoxes(id);
                    this._liveDiff(id);
                }
            }
        },
        _fig: function (label, src) {
            return '<figure class="ki-fig">'
                + (src ? '<img class="ki-img" src="' + src + '" draggable="false" alt="' + label + '">' : '<div class="ki-missing">no ' + label.toLowerCase() + '</div>')
                + '<figcaption>' + label + '</figcaption></figure>';
        },

        // ---- live re-diff (Resemble over base64 — works from file://) ----
        setOpt: function (id, k, v) { this._data[id][k] = v; this._liveDiff(id); },
        setColor: function (id, c) {
            this._data[id].errorColor = c === 'pink' ? { red: 255, green: 0, blue: 255 } : { red: 255, green: 255, blue: 0 };
            this._liveDiff(id);
        },
        _liveDiff: function (id) {
            var d = this._data[id], dlg = document.getElementById(id);
            if (!d.advanced || d.view !== 'diff' || this._resemble !== 'ready' || !window.resemble || !d.baseData || !d.lateData) return;
            var ctl = window.resemble(d.lateData).compareTo(d.baseData);
            switch (d.ignore) {
                case 'nothing': ctl.ignoreNothing(); break;
                case 'colors': ctl.ignoreColors(); break;
                case 'antialiasing': ctl.ignoreAntialiasing(); break;
                case 'alpha': ctl.ignoreAlpha(); break;
                default: ctl.ignoreLess();
            }
            var out = { ignoredBoxes: d.boxes.map(function (b) { return { top: Math.round(b.top), left: Math.round(b.left), bottom: Math.round(b.bottom), right: Math.round(b.right) }; }) };
            if (d.errorType) out.errorType = d.errorType;
            if (d.errorColor) out.errorColor = d.errorColor;
            ctl.outputSettings(out).onComplete(function (data) {
                if (!data || !data.getImageDataUrl) return;
                d.liveDiff = data.getImageDataUrl();
                if (d.view === 'diff') {
                    var img = dlg.querySelector('.ki-canvas > .ki-img');
                    if (img) img.src = d.liveDiff;
                }
            });
        },
        _loadResemble: function (id, cb) {
            if (this._resemble === 'ready') return cb && cb();
            if (cb) this._resembleCbs.push(cb);
            if (this._resemble) return;
            this._resemble = 'loading';
            var self = this;
            var s = document.createElement('script');
            s.src = RESEMBLE_URL; s.async = true;
            s.onload = function () { self._resemble = 'ready'; self._resembleCbs.splice(0).forEach(function (f) { f(); }); };
            s.onerror = function () { self._resemble = 'failed'; self._notice(id, 'could not load resemble.js — live re-diff unavailable'); };
            document.head.appendChild(s);
        },
        _notice: function (id, msg) {
            var el = document.getElementById(id).querySelector('.ki-notice');
            if (el) { el.textContent = msg; }
        },

        // ---- ignore-box authoring (coords in image px) ----
        addBox: function (id) {
            var d = this._data[id];
            if (!d.advanced) this.toggleAdvanced(id);   // boxes are edited on the Diff view
            if (d.view !== 'diff') this.setView(id, 'diff');
            d.boxes.push({ id: d.boxSeq++, left: 10, top: 10, right: 110, bottom: 90 });
            this._renderBoxes(id); this._liveDiff(id);
        },
        removeBox: function (id, boxId) {
            var d = this._data[id];
            d.boxes = d.boxes.filter(function (b) { return b.id !== boxId; });
            this._renderBoxes(id); this._liveDiff(id);
        },
        _scale: function (id) {
            var img = document.getElementById(id).querySelector('.ki-canvas > .ki-img');
            return (img && img.naturalWidth) ? img.clientWidth / img.naturalWidth : 1;
        },
        _renderBoxes: function (id) {
            var d = this._data[id], dlg = document.getElementById(id);
            var layer = dlg.querySelector('.ki-boxlayer'), list = dlg.querySelector('.ki-boxlist');
            if (layer) {
                var sc = this._scale(id);
                layer.innerHTML = '';
                d.boxes.forEach(function (b) {
                    var el = document.createElement('div');
                    el.className = 'ki-box'; el.setAttribute('data-box', b.id);
                    el.style.left = (b.left * sc) + 'px'; el.style.top = (b.top * sc) + 'px';
                    el.style.width = ((b.right - b.left) * sc) + 'px'; el.style.height = ((b.bottom - b.top) * sc) + 'px';
                    ['nw', 'ne', 'sw', 'se'].forEach(function (hh) {
                        var hd = document.createElement('span'); hd.className = 'ki-h ki-h-' + hh; hd.setAttribute('data-h', hh); el.appendChild(hd);
                    });
                    el.addEventListener('pointerdown', function (ev) { KarateImage._boxDown(id, b.id, ev); });
                    layer.appendChild(el);
                });
                layer.onpointerdown = function (ev) { if (ev.target === layer) KarateImage._drawStart(id, ev); };
            }
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
            var handle = ev.target.getAttribute('data-h'), sc = this._scale(id), self = this;
            var sx = ev.clientX, sy = ev.clientY, o = { left: b.left, top: b.top, right: b.right, bottom: b.bottom };
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
            var up = function () { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); self._liveDiff(id); };
            window.addEventListener('pointermove', move); window.addEventListener('pointerup', up);
        },
        _drawStart: function (id, ev) {
            ev.preventDefault();
            var d = this._data[id], sc = this._scale(id), self = this;
            var layer = document.getElementById(id).querySelector('.ki-boxlayer'), r = layer.getBoundingClientRect();
            var x0 = (ev.clientX - r.left) / sc, y0 = (ev.clientY - r.top) / sc;
            var b = { id: d.boxSeq++, left: x0, top: y0, right: x0, bottom: y0 };
            d.boxes.push(b);
            var move = function (e) {
                var x = (e.clientX - r.left) / sc, y = (e.clientY - r.top) / sc;
                b.left = Math.min(x0, x); b.right = Math.max(x0, x); b.top = Math.min(y0, y); b.bottom = Math.max(y0, y);
                self._renderBoxes(id);
            };
            var up = function () {
                window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up);
                if (b.right - b.left < 5 || b.bottom - b.top < 5) { d.boxes = d.boxes.filter(function (x) { return x.id !== b.id; }); self._renderBoxes(id); }
                else self._liveDiff(id);
            };
            window.addEventListener('pointermove', move); window.addEventListener('pointerup', up);
        },

        // ---- slider / onion / blink (two-layer canvas) ----
        blend: function (id, v) {
            v = Math.max(0, Math.min(100, Number(v)));
            var dlg = document.getElementById(id), d = this._data[id];
            var top = dlg.querySelector('.ki-layer-top'), divider = dlg.querySelector('.ki-divider');
            var range = dlg.querySelector('.ki-range[data-for="blend"] input');
            if (range && Number(range.value) !== v) range.value = v;
            if (!top) return;
            if (d.view === 'slider') { top.style.clipPath = 'inset(0 ' + (100 - v) + '% 0 0)'; if (divider) divider.style.left = v + '%'; }
            else { top.style.opacity = (v / 100); }
        },
        _wipeDrag: function (id, ev) {
            var canvas = document.getElementById(id).querySelector('.ki-canvas'), self = this;
            ev.preventDefault();
            var pct = function (e) { var r = canvas.getBoundingClientRect(); return r.width ? (e.clientX - r.left) / r.width * 100 : 50; };
            self.blend(id, pct(ev));
            var move = function (e) { self.blend(id, pct(e)); };
            var up = function () { canvas.removeEventListener('pointermove', move); canvas.removeEventListener('pointerup', up); };
            canvas.addEventListener('pointermove', move); canvas.addEventListener('pointerup', up);
        },
        _startBlink: function (id) {
            var top = document.getElementById(id).querySelector('.ki-layer-top');
            if (!top) return;
            var on = true; top.style.opacity = '1';
            this._blinkTimers[id] = setInterval(function () { on = !on; top.style.opacity = on ? '1' : '0'; }, 600);
        },
        _stopBlink: function (id) {
            if (this._blinkTimers[id]) { clearInterval(this._blinkTimers[id]); delete this._blinkTimers[id]; }
        },

        // ---- actions: show options / rebase (Advanced only) ----
        showOptions: function (id) {
            var d = this._data[id], o = {};
            if (d.engine !== d.defaultEngine) o.engine = d.engine;
            if (d.threshold !== d.defaultThreshold) o.threshold = d.threshold;
            if (d.ignore && d.ignore !== 'less') o.ignore = d.ignore;
            if (d.errorType) o.errorType = d.errorType;
            if (d.boxes.length) o.ignoredBoxes = d.boxes.map(function (b) {
                return { top: Math.round(b.top), left: Math.round(b.left), bottom: Math.round(b.bottom), right: Math.round(b.right) };
            });
            if (Object.keys(o).length === 0) {
                this._showCopy(id, 'Write options', 'Nothing to write yet — turn on Advanced and change an option (Ignore / Error / Color) or add an ignore box first.');
                return;
            }
            var json = JSON.stringify(o, null, 2);
            this._showCopy(id, 'Write options', this._fill(d.optionsCommand || 'cat <<EOF > ${optionsPath}\n${json}\nEOF', d.paths, json));
        },
        rebase: function (id) {
            var d = this._data[id];
            this._showCopy(id, 'Rebase command', this._fill(d.rebaseCommand || 'cp ${latestPath} ${baselinePath}', d.paths, null));
        },
        _fill: function (tmpl, paths, json) {
            return String(tmpl)
                .replace(/\$\{baselinePath\}/g, paths.baseline || '<baselinePath>')
                .replace(/\$\{latestPath\}/g, paths.latest || '<latestPath>')
                .replace(/\$\{optionsPath\}/g, paths.options || '<optionsPath>')
                .replace(/\$\{json\}/g, json == null ? '' : json);
        },
        _showCopy: function (id, title, text) {
            var box = document.getElementById(id).querySelector('.ki-copy');
            box.querySelector('.ki-copy-title').textContent = title;
            box.querySelector('.ki-copy-pre').textContent = text;
            box.hidden = false;
        },
        hideCopy: function (id) { document.getElementById(id).querySelector('.ki-copy').hidden = true; },
        copy: function (id) {
            var txt = document.getElementById(id).querySelector('.ki-copy-pre').textContent;
            if (navigator.clipboard) { navigator.clipboard.writeText(txt).catch(function () { }); }
            else {
                var ta = document.createElement('textarea'); ta.value = txt; document.body.appendChild(ta);
                ta.select(); try { document.execCommand('copy'); } catch (e) { } document.body.removeChild(ta);
            }
        }
    };

    window.KarateImage = KarateImage;

    if (window.KarateReport && typeof window.KarateReport.registerEmbed === 'function') {
        window.KarateReport.registerEmbed('image-comparison', function (embed, api) {
            return KarateImage.render(embed, api);
        });
    }
})();
