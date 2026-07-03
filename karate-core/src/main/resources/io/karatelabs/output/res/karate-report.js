/**
 * Karate v2 Report - Shared Utilities
 * Centralized JS for all report pages (feature, summary, timeline).
 *
 * IMPORTANT: This file is scanned by Tailwind (see ../tailwind/tailwind.config.js
 * content globs) so that Tailwind classes emitted inside HTML-string template
 * literals get included in the generated CSS. For that to work, **every
 * Tailwind class must appear as a literal substring** — do NOT construct
 * class names from variables (e.g. `bg-${color}-600`), or Tailwind's content
 * scanner won't find them and styles will silently disappear at runtime.
 * Always use full literal class names, even in branches (e.g. ternaries:
 *   const cls = passed ? 'bg-green-600' : 'bg-red-600';
 * not:
 *   const cls = `bg-${passed ? 'green' : 'red'}-600`;).
 */

const KarateReport = {
    // ========== Data Parsing ==========

    /**
     * Parse JSON data from the #karate-data script element.
     */
    parseData() {
        const el = document.getElementById('karate-data');
        return el ? JSON.parse(el.textContent) : {};
    },

    // ========== Theme Management ==========

    THEME_KEY: 'karate-theme',

    /**
     * Get current theme from localStorage (default: 'light')
     */
    getTheme() {
        return localStorage.getItem(this.THEME_KEY) || 'light';
    },

    /**
     * Apply theme to document and save to localStorage. Uses data-theme
     * attribute (matched by Tailwind's darkMode: ['selector', '[data-theme="dark"]']
     * config; was data-bs-theme in the Bootstrap era).
     */
    setTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem(this.THEME_KEY, theme);
    },

    /**
     * Toggle between light and dark theme
     */
    toggleTheme() {
        const current = this.getTheme();
        const next = current === 'light' ? 'dark' : 'light';
        this.setTheme(next);
        return next;
    },

    /**
     * Initialize theme on page load (call early, before content renders)
     */
    initTheme() {
        this.setTheme(this.getTheme());
    },

    // ========== State Initialization ==========

    /**
     * Initialize expanded state for steps in scenarios (recursive).
     */
    initStepExpanded(data) {
        if (data.scenarios) {
            data.scenarios.forEach(s => this._initSteps(s.steps));
        }
    },

    _initSteps(steps) {
        if (!steps) return;
        steps.forEach(step => {
            step.expanded = false;
            if (step.callResults) {
                step.callResults.forEach(cr => {
                    if (cr.scenarios) {
                        cr.scenarios.forEach(s => this._initSteps(s.steps));
                    }
                });
            }
        });
    },

    /**
     * Initialize expanded state for features.
     * Sets feature.expanded = false for all features.
     */
    initFeatureExpanded(data) {
        if (data.features) {
            data.features.forEach(f => {
                f.expanded = false;
            });
        }
    },

    /**
     * Collect all unique tags from feature scenarios.
     * Returns sorted array of tag strings.
     */
    collectTags(data) {
        const tagSet = new Set();
        if (data.features) {
            data.features.forEach(f => {
                if (f.scenarios) {
                    f.scenarios.forEach(s => {
                        if (s.tags) {
                            s.tags.forEach(t => tagSet.add(t));
                        }
                    });
                }
            });
        }
        return Array.from(tagSet).sort();
    },

    // ========== Status mapping (single source of truth for the three states) ==========

    /**
     * Return {label, cls} for a scenario/feature based on flags. Used by both
     * Alpine templates (`x-text="statusOf(scenario).label"`) and the JS renderer.
     * Order of checks matches the existing UI: skipped > passed > failed.
     */
    statusOf(item) {
        if (item.skipped) return { label: 'SKIP', cls: 'bg-amber-500' };
        if (item.passed)  return { label: 'PASS', cls: 'bg-green-600' };
        return                       { label: 'FAIL', cls: 'bg-red-600' };
    },

    /**
     * Variant for feature-level rows (the summary table) — labels are
     * PASSED/FAILED (no SKIP since feature-level skip isn't surfaced there).
     */
    statusOfFeature(f) {
        if (f.failed) return { label: 'FAILED', cls: 'bg-red-600' };
        if (f.passed) return { label: 'PASSED', cls: 'bg-green-600' };
        return null;
    },

    /**
     * Hero status pill — input is a count triple {passed, failed, skipped}.
     * Returns label, dot fill class, and outer pill class. Used by the
     * page hero on summary / feature / timeline.
     */
    heroStatus(counts) {
        const failed = counts.failed || 0;
        const skipped = counts.skipped || 0;
        if (failed > 0) {
            return {
                label: failed + (failed === 1 ? ' FAILURE' : ' FAILURES'),
                dotCls: 'bg-red-500',
                pillCls: 'bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 border-red-200 dark:border-red-900'
            };
        }
        if (skipped > 0) {
            return {
                label: skipped + (skipped === 1 ? ' SKIPPED' : ' SKIPPED'),
                dotCls: 'bg-amber-500',
                pillCls: 'bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border-amber-200 dark:border-amber-900'
            };
        }
        return {
            label: 'ALL PASSED',
            dotCls: 'bg-green-500',
            pillCls: 'bg-green-50 dark:bg-green-950/40 text-green-700 dark:text-green-300 border-green-200 dark:border-green-900'
        };
    },

    // ========== Step Rendering (recursive) ==========

    _stepId: 0,

    /**
     * Open the shared screenshot lightbox <dialog> on the page. The element is
     * expected to be present in the feature template; embeds render onclick="..."
     * inside HTML strings (Alpine doesn't process @click inside x-html output),
     * so we go directly through the DOM here.
     */
    openLightbox(src, name) {
        const dialog = document.getElementById('img-lightbox');
        if (!dialog) return;
        const img = document.getElementById('img-lightbox-img');
        const cap = document.getElementById('img-lightbox-caption');
        if (img) { img.src = src; img.alt = name || ''; }
        if (cap) { cap.textContent = name || ''; }
        dialog.showModal();
    },

    /**
     * Toggle expand/collapse for a step's detail section. Marker classes
     * (.k-step, .k-step-detail, .k-badge-collapsed, .step-row) are kept
     * as DOM hooks for these queries — they carry no styling.
     */
    toggleStep(btn) {
        const container = btn.closest('.k-step');
        if (!container) return;
        const detail = container.querySelector(':scope > .k-step-detail');
        if (!detail) return;
        const expanded = detail.style.display !== 'none';
        detail.style.display = expanded ? 'none' : 'block';
        // A row click shows EVERYTHING — clear any per-kind filter a badge set, so all embeds show.
        detail.querySelectorAll('.k-embed').forEach(el => { el.style.display = ''; });
        delete detail.dataset.kindFilter;
        // The collapsed badges STAY as a persistent handle (they used to be hidden on expand — you then
        // lost track of what the row carried and had nothing to click to collapse). Just clear the
        // per-kind active highlight; the badges remain visible in both states.
        container.querySelectorAll(':scope > .step-row .k-embed-badge').forEach(el => {
            el.style.outline = '';
        });
    },

    /**
     * Expand ONLY the embeds of one kind (the row's kind badge was clicked). Opens the step
     * detail, shows just that kind's embeds (hiding sibling embeds — a row click clears the
     * filter), and outlines the active badge. Clicking the same kind badge again collapses.
     * `event.stopPropagation()` on the badge keeps this from also firing the row's toggleStep.
     */
    toggleEmbedKind(badge, kind) {
        const container = badge.closest('.k-step');
        if (!container) return;
        const detail = container.querySelector(':scope > .k-step-detail');
        if (!detail) return;
        const rowBadges = container.querySelectorAll(':scope > .step-row .k-embed-badge');
        const alreadyThisKind = detail.style.display !== 'none' && detail.dataset.kindFilter === kind;
        if (alreadyThisKind) {
            detail.style.display = 'none';
            delete detail.dataset.kindFilter;
            rowBadges.forEach(b => { b.style.outline = ''; });
            return;
        }
        detail.style.display = 'block';
        detail.dataset.kindFilter = kind;
        detail.querySelectorAll('.k-embed').forEach(el => {
            el.style.display = (el.dataset.embedKind === kind) ? '' : 'none';
        });
        rowBadges.forEach(b => {
            b.style.outline = (b.dataset.embedKind === kind) ? '2px solid rgba(255,255,255,.7)' : '';
        });
        // materialize any now-visible deferred embeds of this kind (they render on-view otherwise)
        detail.querySelectorAll('.k-embed[data-defer]').forEach(h => {
            if (h.style.display !== 'none') this._materializeEmbed(h);
        });
    },

    /**
     * Render an array of steps as HTML. Supports infinite nesting via callResults.
     */
    renderSteps(steps) {
        if (!steps || !steps.length) return '';
        return steps.map(step => this._renderStep(step)).join('');
    },

    /**
     * Map a Karate keyword to a text color class. Only a few keywords get colored:
     * `method` (the HTTP fire-the-request action) and the assertions `match` / `assert`.
     * Everything else stays slate. Class strings stay literal so Tailwind's
     * content scanner picks them up.
     */
    _keywordColor(keyword) {
        const k = (keyword || '').toLowerCase();
        if (k === 'method')              return 'text-orange-600 dark:text-orange-400';
        if (k === 'match' || k === 'assert') return 'text-blue-700 dark:text-blue-400';
        return                                'text-slate-700 dark:text-slate-300';
    },

    _esc(s) {
        if (s == null) return '';
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    },

    // Tailwind class snippets reused across step / call / scenario rendering.
    // Kept as constants so they don't get out of sync between sibling calls.
    _BADGE_BASE: 'inline-block px-1.5 py-0.5 text-[0.65rem] font-medium rounded text-white align-middle',

    /**
     * Common error-block markup (used 3x in _renderStep — step-level error
     * inside detail, step-level error outside detail, nested-scenario error).
     * Pass extra margin classes via marginCls; defaults to inline-step spacing.
     */
    _renderErrorBlock(errorText, marginCls = 'mx-3 my-1') {
        return `<div class="${marginCls} px-3 py-1.5 rounded bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 text-red-700 dark:text-red-300 text-xs"><pre class="m-0 whitespace-pre-wrap">${this._esc(errorText)}</pre></div>`;
    },

    _renderStep(step) {
        const id = 'ks-' + (this._stepId++);
        const hasDetail = step.hasLogs || step.hasEmbeds || step.hasCallResults;
        const clickable = hasDetail ? 'cursor-pointer' : '';
        const onclick = hasDetail ? `onclick="KarateReport.toggleStep(this)"` : '';
        // Hook steps (before/after) are infrastructure, not user-authored test logic —
        // mute them so the eye skims past to the real steps.
        const statusClass = step.hook
            ? 'text-slate-400 dark:text-slate-500'
            : step.status === 'passed'
                ? 'text-green-700 dark:text-green-400'
                : step.status === 'failed'
                    ? 'text-red-700 dark:text-red-400'
                    : step.status === 'skipped'
                        ? 'text-amber-700 dark:text-amber-400'
                        : '';
        const muted = 'text-slate-500 dark:text-slate-400';
        const badgeBase = this._BADGE_BASE;
        const keywordColor = this._keywordColor(step.keyword);

        let html = `<div class="k-step" id="${id}">`;

        // Comments
        if (step.comments && step.comments.length) {
            html += `<div class="px-3 py-1 italic text-xs ${muted} bg-slate-50 dark:bg-slate-800/50">`;
            step.comments.forEach(c => { html += `<div>${this._esc(c)}</div>`; });
            html += `</div>`;
        }

        // Step row
        html += `<div class="step-row flex items-start px-3 py-1 font-mono text-xs border-b border-slate-100 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/50 ${clickable}" ${onclick}>`;
        html += `<div class="${muted} mr-2 w-10 text-right shrink-0"><span class="text-xs">[${step.line}]</span></div>`;
        html += `<div class="flex-1 min-w-0 ${statusClass}">`;
        html += `<span class="${muted}">${this._esc(step.prefix)}</span> `;
        html += `<span class="font-bold ${keywordColor}">${this._esc(step.keyword)}</span> `;
        html += `<span>${this._esc(step.text)}</span>`;

        if (step.hook) {
            html += ` <span class="${badgeBase} bg-slate-600 ml-1" title="lifecycle hook">hook</span>`;
        }

        // Collapsed badges
        if (step.hasLogs) {
            html += ` <span class="${badgeBase} bg-slate-600 ml-1 k-badge-collapsed" title="Has logs - click to expand">log</span>`;
        }
        if (step.hasEmbeds) {
            // One badge PER embed KIND (screenshot / http / coverage / …) so the row says at a glance
            // WHAT evidence it carries, not just how many. Clicking a kind badge expands ONLY that kind;
            // clicking the row still expands everything (toggleStep). Badges persist while expanded — a
            // handle to collapse again — instead of being hidden.
            this._embedKinds(step.embeds || []).forEach(({ kind, count }) => {
                const label = count > 1 ? `${this._esc(kind)} ${count}` : this._esc(kind);
                html += ` <span class="${badgeBase} bg-accent ml-1 k-badge-collapsed k-embed-badge cursor-pointer"`
                    + ` data-embed-kind="${this._esc(kind)}"`
                    + ` onclick="event.stopPropagation(); KarateReport.toggleEmbedKind(this, '${this._esc(kind)}')"`
                    + ` title="${count} ${this._esc(kind)} embed${count > 1 ? 's' : ''} — click to expand just these">${label}</span>`;
            });
        }
        if (step.hasCallResults) {
            const n = step.callResults?.length || 0;
            if (n === 1) {
                html += ` <span class="${badgeBase} bg-accent ml-1 k-badge-collapsed" title="Called feature - click to expand">call</span>`;
            } else if (n > 1) {
                html += ` <span class="${badgeBase} bg-accent ml-1 k-badge-collapsed" title="${n} call iterations">${n} calls</span>`;
            }
        }

        html += `</div>`;
        html += `<div class="${muted} ml-2 w-20 text-right shrink-0"><span class="text-xs tabular-nums">${step.durationMillis} ms</span></div>`;
        html += `</div>`; // end step-row

        // Detail section (hidden by default)
        if (hasDetail) {
            html += `<div class="k-step-detail" style="display: none;">`;

            if (step.logSegments && step.logSegments.length) {
                // One contiguous <pre> so whitespace stays exactly as logged; code
                // segments (e.g. JSON bodies) are wrapped in a language-tagged <code>
                // that Prism colorizes in place (_highlightCode), leaving the request
                // and header lines around them as plain text.
                let inner = '';
                step.logSegments.forEach(seg => {
                    if (seg.lang) {
                        inner += `<code class="language-${this._esc(seg.lang)}">${this._esc(seg.code)}</code>`;
                    } else {
                        inner += this._esc(seg.text);
                    }
                });
                html += `<div class="mx-3 my-1 p-2 rounded bg-slate-100 dark:bg-slate-800 text-xs"><pre class="m-0 whitespace-pre-wrap">${inner}</pre></div>`;
            }

            if (step.embeds && step.embeds.length) {
                html += `<div class="mx-3 my-2 space-y-2">`;
                step.embeds.forEach(embed => {
                    html += this._renderEmbed(embed);
                });
                html += `</div>`;
            }

            if (step.error) {
                html += this._renderErrorBlock(step.error);
            }

            // Call results (recursive)
            if (step.callResults && step.callResults.length) {
                html += `<div class="ml-12 my-2 pl-3 border-l-4 border-accent bg-slate-50 dark:bg-slate-800/30">`;
                step.callResults.forEach((cr, fidx) => {
                    const sep = fidx < step.callResults.length - 1 ? ' border-b border-slate-200 dark:border-slate-700' : '';
                    html += `<div class="mb-3 pb-2${sep}">`;
                    if (step.callResults.length > 1) {
                        const cs = this.statusOf({ passed: cr.passed });
                        html += `<div class="flex items-center mb-2">`;
                        html += `<span class="${badgeBase} bg-slate-600 mr-2">#${fidx + 1}</span>`;
                        html += `<span class="text-xs ${muted}">${this._esc(cr.path || cr.name)}</span>`;
                        html += `<span class="text-xs ${muted} ml-2">${cr.durationMillis} ms</span>`;
                        html += ` <span class="${badgeBase} ${cs.cls} ml-2">${cs.label}</span>`;
                        html += `</div>`;
                    } else {
                        html += `<div class="text-xs ${muted} mb-1">`;
                        html += `<span>${this._esc(cr.path || cr.name)}</span>`;
                        html += `<span class="ml-2">${cr.durationMillis} ms</span>`;
                        html += `</div>`;
                    }
                    (cr.scenarios || []).forEach(nested => {
                        const ns = this.statusOf(nested);
                        const borderCls = nested.passed ? 'border-green-500' : 'border-red-500';
                        html += `<div class="rounded border-l-4 ${borderCls} my-2 p-2 bg-white dark:bg-slate-900">`;
                        html += `<div class="flex justify-between items-center mb-1">`;
                        html += `<span><span class="font-bold">${this._esc(nested.refId || '')}</span> <span>${this._esc(nested.name)}</span></span>`;
                        html += `<span class="flex items-center"><span class="text-xs ${muted} mr-2 tabular-nums">${nested.durationMillis} ms</span>`;
                        html += `<span class="${badgeBase} ${ns.cls}">${ns.label}</span>`;
                        html += `</span></div>`;
                        html += this.renderSteps(nested.steps);
                        if (nested.error) {
                            html += this._renderErrorBlock(nested.error, 'my-1');
                        }
                        html += `</div>`;
                    });
                    html += `</div>`;
                });
                html += `</div>`;
            }

            html += `</div>`; // end k-step-detail
        } else if (step.error) {
            html += this._renderErrorBlock(step.error);
        }

        html += `</div>`; // end k-step
        return html;
    },

    // ext-contributed embed renderers keyed by embed name, plus a record of every
    // embed rendered this page (so a late-registering ext can upgrade in place).
    _embedRenderers: {},
    _embeds: [],
    _embedSeq: 0,

    /**
     * Register a renderer for a named embed. An ext — loaded via the KARATE_EXTS
     * `<script defer>` splice — calls this to take over rendering of its own embed,
     * e.g. `KarateReport.registerEmbed('image-comparison', (embed, api) => '<...>')`.
     * The renderer is passed the embed ({name, parts, meta}) and `this` (so it can
     * reuse `_embedPartSrc` / `_esc`) and returns a markup string.
     *
     * Ordering: alpine.min.js (defer) starts and renders the report *before* the ext
     * `<script defer>` executes, so the ext's embeds are already in the DOM when this
     * runs. We therefore upgrade any already-rendered embeds of this name in place,
     * in addition to claiming future renders. Graceful: until (or unless) an ext
     * registers, the generic per-part fallback (`_renderEmbedGeneric`) is shown.
     */
    registerEmbed(name, fn) {
        this._embedRenderers[name] = fn;
        if (typeof document === 'undefined') return;
        // Re-upgrade hosts that were ALREADY materialized (e.g. with the generic fallback
        // before this ext registered). Still-deferred placeholders pick up `fn` when the
        // IntersectionObserver later materializes them — see _materializeEmbed.
        this._embeds.forEach(({ id, embed }) => {
            if (embed.name !== name) return;
            const host = document.querySelector(`[data-embed-id="${id}"]`);
            if (host && host.dataset.rendered) host.innerHTML = fn(embed, this);
        });
    },

    // Embeds render lazily: _renderEmbed emits an empty placeholder host; the renderer runs
    // only when the host scrolls into view (initDeferredEmbeds wires the observers). This is
    // what keeps very large reports from building every embed (and decoding every image) at
    // first paint. The min-height stops zero-height placeholders from all intersecting at once.
    _renderEmbed(embed) {
        const id = ++this._embedSeq;
        this._embeds.push({ id, embed });
        const kind = this._embedKind(embed);
        return `<div class="k-embed" data-embed-id="${id}" data-embed-kind="${this._esc(kind)}" data-defer style="min-height:2rem"></div>`;
    },

    /**
     * Classify an embed into a short, human-readable KIND for the collapsed row badge
     * (image / http / coverage / grpc / …). Precedence: an explicit ext-declared
     * `meta.kind` wins; else an image/video part is an image/video; else the embed
     * name with a coverage/exchange suffix stripped to its base (`http-exchange`→`http`,
     * `openapi-match`→`openapi`, `coverage-emit`→`coverage`); `evidence`→`assertion`.
     * Core knows no ext-specific names — this is pure shape/suffix derivation.
     */
    _embedKind(embed) {
        if (embed && embed.meta && embed.meta.kind) return String(embed.meta.kind);
        const mimes = ((embed && embed.parts) || []).map(p => (p.mime || '').toLowerCase());
        if (mimes.some(m => m.startsWith('image/'))) return 'image';
        if (mimes.some(m => m.startsWith('video/'))) return 'video';
        const n = (embed && embed.name || '').toLowerCase().trim();
        if (!n) return mimes.some(m => m === 'text/html') ? 'html' : 'embed';
        const m = n.match(/^([a-z0-9]+)-(exchange|match|emit)$/);
        if (m) return m[1];
        if (n === 'evidence') return 'assertion';
        if (/\.(png|jpe?g|gif|webp)$/.test(n)) return 'image';
        return n;
    },

    /** Distinct embed kinds for a step, in first-seen order, each with its count. */
    _embedKinds(embeds) {
        const order = [];
        const counts = {};
        (embeds || []).forEach(e => {
            const k = this._embedKind(e);
            if (!(k in counts)) { counts[k] = 0; order.push(k); }
            counts[k]++;
        });
        return order.map(k => ({ kind: k, count: counts[k] }));
    },

    _materializeEmbed(host) {
        if (!host || host.dataset.rendered) return;
        const id = Number(host.getAttribute('data-embed-id'));
        const rec = this._embeds.find(x => x.id === id);
        if (!rec) return;
        const renderer = rec.embed.name ? this._embedRenderers[rec.embed.name] : null;
        host.innerHTML = renderer ? renderer(rec.embed, this) : this._renderEmbedGeneric(rec.embed);
        host.dataset.rendered = '1';
        host.removeAttribute('data-defer');
        host.style.minHeight = '';
        if (this._deferIO) this._deferIO.unobserve(host);
    },

    _observeDeferred(host) {
        if (!host || host.dataset.rendered || host.dataset.observed) return;
        host.dataset.observed = '1';
        if (this._deferIO) this._deferIO.observe(host);
        else this._materializeEmbed(host);   // no IntersectionObserver support -> render now
    },

    /**
     * Wire up lazy embed rendering for the current page. A MutationObserver catches the
     * embed placeholders as Alpine inserts each scenario's `x-html`; an IntersectionObserver
     * materializes each host when it scrolls into view (or when a collapsed step is expanded
     * and becomes visible). Idempotent. On `beforeprint`, everything is force-rendered so
     * print / PDF / Ctrl-F don't miss off-screen embeds.
     */
    initDeferredEmbeds() {
        if (this._deferReady || typeof document === 'undefined') return;
        this._deferReady = true;
        const self = this;
        if (typeof IntersectionObserver !== 'undefined') {
            this._deferIO = new IntersectionObserver(entries => {
                entries.forEach(e => { if (e.isIntersecting) self._materializeEmbed(e.target); });
            }, { rootMargin: '200px' });
        }
        const scan = root => {
            if (root && root.querySelectorAll) {
                root.querySelectorAll('[data-embed-id][data-defer]').forEach(h => self._observeDeferred(h));
            }
            self._highlightCode(root);
        };
        if (typeof MutationObserver !== 'undefined' && document.body) {
            new MutationObserver(muts => {
                muts.forEach(m => m.addedNodes && m.addedNodes.forEach(n => {
                    if (n.nodeType !== 1) return;
                    if (n.matches && n.matches('[data-embed-id][data-defer]')) self._observeDeferred(n);
                    scan(n);
                }));
            }).observe(document.body, { childList: true, subtree: true });
        }
        scan(document);
        window.addEventListener('beforeprint', () => {
            document.querySelectorAll('[data-embed-id][data-defer]').forEach(h => self._materializeEmbed(h));
        });
    },

    /**
     * Run Prism over any language-tagged <code> blocks under `root` (HTTP JSON
     * bodies today, JS snippets later). Called as Alpine inserts each scenario's
     * x-html. Prism highlights hidden nodes fine, so we colorize at insert time
     * regardless of whether the step detail is expanded. No-op if Prism is absent.
     * Token colors come from prism-karate.css, keyed on the report's data-theme —
     * so the theme toggle re-colors instantly with no re-highlight needed.
     */
    _highlightCode(root) {
        if (typeof Prism === 'undefined' || !root) return;
        if (root.matches && root.matches('code[class*="language-"]')) {
            Prism.highlightElement(root);
            return;
        }
        if (root.querySelectorAll) {
            root.querySelectorAll('code[class*="language-"]').forEach(el => Prism.highlightElement(el));
        }
    },

    _deferIO: null,
    _deferReady: false,

    // Default per-part rendering when no ext claims the embed name — also the
    // graceful fallback shown until an ext upgrades it.
    _renderEmbedGeneric(embed) {
        const muted = 'text-slate-500 dark:text-slate-400';
        let html = `<div class="border border-slate-200 dark:border-slate-700 rounded p-2 bg-slate-50 dark:bg-slate-800/50">`;
        if (embed.name) {
            html += `<div class="text-xs ${muted} mb-1">${this._esc(embed.name)}</div>`;
        }
        // Wire shape is {name, parts:[{role, mime, data|url|file}], meta}. Core renders
        // each part generically by mime; ext-specific layouts come from the ext's own
        // slot script (loaded via KARATE_EXTS), not here.
        (embed.parts || []).forEach(part => { html += this._renderEmbedPart(part, embed.name); });
        html += `</div>`;
        return html;
    },

    // Resolve a part's src relative to a feature page (under feature-html/): inline
    // assets live in ../embeds/<file>; ext-written assets carry a report-relative url.
    _embedPartSrc(part) {
        if (part.file) return `../embeds/${this._esc(part.file)}`;
        if (part.url) return `../${this._esc(part.url)}`;
        return null;
    },

    _renderEmbedPart(part, embedName) {
        const mime = part.mime || '';
        const src = this._embedPartSrc(part);
        let html = '';
        if (mime.startsWith('image/')) {
            if (src) {
                const alt = this._esc(embedName || part.role || 'embedded image');
                html += `<img src="${src}" alt="${alt}" class="max-w-full h-auto max-h-96 cursor-zoom-in rounded transition-opacity hover:opacity-90" onclick="KarateReport.openLightbox(this.src, this.alt)">`;
            }
        } else if (mime === 'text/html') {
            if (src) html += `<iframe src="${src}" class="w-full border-0 h-72"></iframe>`;
        } else if (mime.startsWith('video/')) {
            if (src) html += `<video controls class="w-full max-h-96"><source src="${src}" type="${this._esc(mime)}"></video>`;
        } else if (mime === 'application/pdf') {
            if (src) html += `<embed src="${src}" type="application/pdf" class="w-full h-96">`;
        } else if (mime === 'text/plain' || mime === 'application/json' || mime === 'application/xml') {
            if (src) {
                html += `<a href="${src}" class="text-xs text-accent hover:underline" target="_blank">Open file</a>`;
            }
            if (part.data) {
                html += `<pre class="bg-slate-900 text-slate-100 p-2 rounded m-0 mt-1 text-xs max-h-72 overflow-auto whitespace-pre-wrap">${this._esc(atob(part.data))}</pre>`;
            }
        } else if (src) {
            html += `<a href="${src}" class="inline-block px-2 py-1 text-xs border border-slate-400 dark:border-slate-600 rounded hover:bg-slate-100 dark:hover:bg-slate-800" download>Download (${this._esc(mime)})</a>`;
        }
        return html;
    },

    // ========== Utilities ==========

    /**
     * Truncate string to max length with ellipsis.
     */
    truncate(str, len) {
        if (!str) return '';
        return str.length > len ? str.substring(0, len) + '...' : str;
    },

    // ========== Alpine.js Data Factories ==========

    /**
     * Create Alpine.js data object for feature page.
     * Usage: x-data="KarateReport.featureData()"
     */
    featureData() {
        const data = this.parseData();
        this.initStepExpanded(data);
        this.initTheme();
        this.initDeferredEmbeds();   // lazy-render step embeds as they scroll into view

        const self = this;
        return {
            data,
            theme: self.getTheme(),
            // Sidebar filter: 'all' shows every scenario, 'failed' restricts to
            // failures. Skipped count as non-failed (they're not actionable from
            // a sidebar nav perspective).
            sidebarFilter: 'all',

            get featureSimpleName() {
                const p = (data.relativePath || data.path || data.name || '').toString();
                const base = p.split('/').pop().split('\\').pop();
                return base.replace(/\.feature$/, '') || 'Feature';
            },

            get heroCounts() {
                const scenarios = data.scenarios || [];
                return scenarios.reduce((acc, s) => {
                    if (s.skipped) acc.skipped++;
                    else if (s.passed) acc.passed++;
                    else acc.failed++;
                    return acc;
                }, { passed: 0, failed: 0, skipped: 0 });
            },

            get heroStatus() { return self.heroStatus(this.heroCounts); },

            get filteredSidebar() {
                const scenarios = data.scenarios || [];
                if (this.sidebarFilter === 'failed') {
                    return scenarios.filter(s => !s.passed && !s.skipped);
                }
                return scenarios;
            },

            toggleTheme()              { this.theme = self.toggleTheme(); },
            truncate(str, len)         { return self.truncate(str, len); },
            renderSteps(steps)         { return self.renderSteps(steps); },
            statusOf(item)             { return self.statusOf(item); },
        };
    },

    /**
     * Create Alpine.js data object for summary page.
     * Usage: x-data="KarateReport.summaryData()"
     */
    summaryData() {
        const data = this.parseData();
        this.initFeatureExpanded(data);
        this.initTheme();

        const allTags = this.collectTags(data);
        const self = this;

        // Single source of truth for the sortable feature table headers.
        // Used by the <template x-for="col in sortableColumns"> block.
        const sortableColumns = [
            { field: 'name',           label: 'Feature' },
            { field: 'scenarioCount',  label: 'Scenarios',  thClass: 'w-28' },
            { field: 'passedCount',    label: 'Passed',     thClass: 'w-24' },
            { field: 'failedCount',    label: 'Failed',     thClass: 'w-24' },
            { field: 'passedRate',     label: 'Pass %',     thClass: 'w-24' },
            { field: 'durationMillis', label: 'Duration',   thClass: 'w-32' },
        ];

        return {
            data,
            allTags,
            sortableColumns,
            selectedTags: [],
            highlightMode: false,
            sortField: 'name',
            sortDir: 'asc',
            theme: self.getTheme(),

            toggleTheme()        { this.theme = self.toggleTheme(); },
            statusOf(item)       { return self.statusOf(item); },
            statusOfFeature(f)   { return self.statusOfFeature(f); },

            // accent colour for an ext KPI tile's value, from its optional `status` field
            summaryCardColor(status) {
                return {
                    ok:   'text-green-600 dark:text-green-400',
                    warn: 'text-amber-600 dark:text-amber-400',
                    fail: 'text-red-600 dark:text-red-400',
                }[status] || '';
            },

            get heroStatus() {
                const s = data.summary || {};
                return self.heroStatus({
                    passed: s.scenario_passed || 0,
                    failed: s.scenario_failed || 0,
                    skipped: s.scenario_skipped || 0,
                });
            },

            // Donut segments. r=50, pathLength="100" normalises math to percentages
            // so stroke-dasharray is "<len> <gap>". Unrolled (not x-for) because
            // <template x-for> doesn't work inside <svg> (namespace mismatch:
            // Alpine can't importNode an HTML <template> into the SVG DOM).
            // Three segments — pass (green) / skip (amber) / fail (red) — laid out
            // contiguously in that order; each getter returns {offset, dash} or null
            // if that count is 0. scenario_passed already INCLUDES skipped scenarios
            // (a skipped scenario counts as passed), so:
            //   - the total is passed + failed (= every scenario; no double count),
            //   - the green pass arc shows only the REAL passes (passed − skipped) so
            //     it doesn't overlap the amber skip arc,
            //   - but the center % (donutPct) still counts skipped as passed, matching
            //     the per-feature/totals rows and getScenarioPassedRate().
            get donutTotal() {
                const s = data.summary || {};
                return (s.scenario_passed || 0) + (s.scenario_failed || 0);
            },
            get realPassed() {
                return (data.summary?.scenario_passed || 0) - (data.summary?.scenario_skipped || 0);
            },
            get donutPass() {
                const total = this.donutTotal;
                const n = this.realPassed;
                if (total === 0 || n <= 0) return null;
                const len = (n / total) * 100;
                return { dash: len + ' ' + (100 - len), offset: 0 };
            },
            get donutSkip() {
                const total = this.donutTotal;
                const n = data.summary?.scenario_skipped || 0;
                if (total === 0 || n === 0) return null;
                const before = this.realPassed; // real passes precede the skip arc
                const len = (n / total) * 100;
                return { dash: len + ' ' + (100 - len), offset: -(before / total) * 100 };
            },
            get donutFail() {
                const total = this.donutTotal;
                const n = data.summary?.scenario_failed || 0;
                if (total === 0 || n === 0) return null;
                const before = data.summary?.scenario_passed || 0; // real passes + skipped precede fail
                const len = (n / total) * 100;
                return { dash: len + ' ' + (100 - len), offset: -(before / total) * 100 };
            },

            get donutPct() {
                const total = this.donutTotal;
                const p = data.summary?.scenario_passed || 0; // includes skipped
                return total === 0 ? 0 : Math.round((p / total) * 100);
            },

            // Flatten failed scenarios across features with click-through metadata.
            get failedScenarios() {
                const out = [];
                (data.features || []).forEach(f => {
                    (f.scenarios || []).forEach(s => {
                        if (!s.passed && !s.skipped) {
                            out.push({
                                refId: s.refId,
                                name: s.name,
                                durationMillis: s.durationMillis,
                                featureName: f.name || f.relativePath,
                                featureFile: f.fileName,
                                relativePath: f.relativePath,
                            });
                        }
                    });
                });
                return out;
            },

            toggleTag(tag) {
                const idx = this.selectedTags.indexOf(tag);
                if (idx > -1) {
                    this.selectedTags.splice(idx, 1);
                } else {
                    this.selectedTags.push(tag);
                }
            },

            sortBy(field) {
                if (this.sortField === field) {
                    this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
                } else {
                    this.sortField = field;
                    this.sortDir = 'asc';
                }
            },

            get filteredFeatures() {
                // passedRate is supplied by HtmlReportWriter#buildFeatureSummaryList
                let features = (this.data.features || []).slice();
                features.sort((a, b) => {
                    let aVal = a[this.sortField];
                    let bVal = b[this.sortField];
                    if (aVal == null) aVal = '';
                    if (bVal == null) bVal = '';
                    let cmp = typeof aVal === 'string' ? aVal.localeCompare(bVal) : aVal - bVal;
                    return this.sortDir === 'asc' ? cmp : -cmp;
                });
                return features;
            },

            get totals() {
                const list = this.data.features || [];
                const t = list.reduce((acc, f) => {
                    acc.scenarios += (f.scenarioCount || 0);
                    acc.passed += (f.passedCount || 0);
                    acc.failed += (f.failedCount || 0);
                    acc.skipped += (f.skippedCount || 0);
                    acc.durationMillis += (f.durationMillis || 0);
                    return acc;
                }, { scenarios: 0, passed: 0, failed: 0, skipped: 0, durationMillis: 0 });
                const executed = t.passed + t.failed;
                t.passedRate = executed === 0 ? null : Math.round((t.passed / executed) * 100);
                return t;
            },

            formatPassedRate(rate) {
                return rate == null ? '\u2014' : rate + '%';
            },

            featureMatchesTags(feature) {
                if (!this.selectedTags.length) return true;
                if (!feature.scenarios) return false;
                return feature.scenarios.some(s =>
                    s.tags && s.tags.some(t => this.selectedTags.includes(t))
                );
            },

            getMatchingScenarios(feature) {
                if (!this.selectedTags.length) return feature.scenarios || [];
                return (feature.scenarios || []).filter(s =>
                    s.tags && s.tags.some(t => this.selectedTags.includes(t))
                );
            },

            getFeatureTagCounts(feature) {
                const counts = {};
                (feature.scenarios || []).forEach(s => {
                    (s.tags || []).forEach(t => {
                        counts[t] = (counts[t] || 0) + 1;
                    });
                });
                return Object.entries(counts).sort((a, b) => b[1] - a[1]);
            }
        };
    },

    /**
     * Create Alpine.js data object for timeline page.
     * Usage: x-data="KarateReport.timelineData()"
     */
    timelineData() {
        const data = this.parseData();
        this.initTheme();

        // Initialize vis-timeline after Alpine mounts
        setTimeout(() => this.initVisTimeline(data), 0);

        const self = this;
        return {
            data,
            theme: self.getTheme(),
            toggleTheme()  { this.theme = self.toggleTheme(); },

            get heroStatus() {
                const s = data.summary || {};
                return self.heroStatus({
                    passed: s.scenario_passed || 0,
                    failed: s.scenario_failed || 0,
                    skipped: s.scenario_skipped || 0,
                });
            },

            // Speedup metric: sum of per-scenario wall time vs actual suite wall time.
            // "1.0x" = perfectly serial, "4.0x" = 4 threads fully utilised.
            // Hidden when wall clock is sub-half-second (timing noise dominates) or
            // when only one thread was used (nothing to speedup against).
            get speedup() {
                const wall = data.summary?.duration_millis || 0;
                const serial = data.serialDurationMillis || 0;
                if (wall < 500 || serial === 0 || (data.threads || 1) < 2) return null;
                return (serial / wall).toFixed(1) + 'x';
            },

            get wallClock() {
                const ms = data.summary?.duration_millis || 0;
                if (ms < 1000) return ms + ' ms';
                return (ms / 1000).toFixed(2) + ' s';
            },

            // Top-5 slowest scenarios. data.scenarios is the flat list built by
            // HtmlReportWriter.buildTimelineData.
            get slowestScenarios() {
                return (data.scenarios || [])
                    .slice()
                    .sort((a, b) => (b.durationMillis || 0) - (a.durationMillis || 0))
                    .slice(0, 5);
            },

            // Link to a scenario's anchor inside its feature page.
            scenarioHref(scenario) {
                return 'feature-html/' + scenario.featureHtmlName + '.html#' + scenario.refId;
            },
        };
    },

    /**
     * Initialize vis-timeline component.
     */
    initVisTimeline(data) {
        const container = document.getElementById('timeline');
        if (!container || !data.groups || !data.items) return;
        if (typeof vis === 'undefined') return;

        const groups = new vis.DataSet(data.groups);
        const items = new vis.DataSet(data.items);
        const options = {
            groupOrder: 'content',
            // Require Ctrl/Cmd key to zoom with mouse wheel - allows normal page scrolling
            zoomKey: 'ctrlKey'
        };
        const timeline = new vis.Timeline(container, items, groups, options);
        timeline.fit();
    },

    /**
     * Render the shared report TOP-NAV into a mount element (default {@code #k-topnav}) — the one nav
     * bar every contributed ext page (Coverage, Traceability, …) can reuse so they read as ONE report,
     * with the current tab highlighted. "Non-UI mode": pure DOM, no Alpine report app needed — an ext's
     * standalone/static page just includes this script, drops a {@code <div id="k-topnav">}, and calls
     * this. Self-contained styling (injected once) works under the ext pages' {@code [data-theme]} theming
     * without Tailwind. opts: {@code current} (active tab key, highlighted), {@code summary} (href back to
     * the test report), {@code tabs:[{key,title,href}]} (the contributed tabs), {@code cta:{label,href}} (optional).
     */
    renderTopNav(opts) {
        opts = opts || {};
        const mount = document.querySelector(opts.mount || '#k-topnav');
        if (!mount) return;
        const esc = s => this._esc(s == null ? '' : String(s));
        const items = [];
        if (opts.summary) items.push({ key: 'summary', title: 'Test report', href: opts.summary, back: true });
        (opts.tabs || []).forEach(t => items.push(t));
        const links = items.map(t => {
            const active = t.key && t.key === opts.current;
            return `<a class="k-tnav-link${active ? ' k-tnav-active' : ''}${t.back ? ' k-tnav-back' : ''}"`
                + ` href="${esc(t.href)}"${active ? ' aria-current="page"' : ''}>`
                + `${t.back ? '&#8592; ' : ''}${esc(t.title)}</a>`;
        }).join('');
        const cta = (opts.cta && opts.cta.href)
            ? `<a class="k-tnav-cta" href="${esc(opts.cta.href)}" target="_blank" rel="noopener">${esc(opts.cta.label || 'Learn more')}</a>`
            : '';
        this._ensureTopNavStyle();
        mount.innerHTML = `<nav class="k-tnav"><div class="k-tnav-links">${links}</div><div class="k-tnav-right">${cta}</div></nav>`;
    },

    /** Inject the top-nav stylesheet once. Plain CSS (no Tailwind) + {@code [data-theme="dark"]} so it
     *  themes correctly on the ext pages, which don't ship the report's Tailwind build. */
    _ensureTopNavStyle() {
        if (document.getElementById('k-tnav-style')) return;
        const css = `
.k-tnav{display:flex;align-items:center;justify-content:space-between;gap:1rem;flex-wrap:wrap;`
            + `padding:.5rem 1rem;margin-bottom:1rem;border-bottom:1px solid #e2e8f0;`
            + `font:500 13px/1.2 system-ui,-apple-system,Segoe UI,sans-serif}`
            + `.k-tnav-links{display:flex;align-items:center;gap:.25rem;flex-wrap:wrap}`
            + `.k-tnav-link{padding:.35rem .65rem;border-radius:.375rem;color:#475569;text-decoration:none;transition:background .12s,color .12s}`
            + `.k-tnav-link:hover{background:rgba(100,116,139,.14);color:#0f172a}`
            + `.k-tnav-active{background:rgba(59,130,246,.14);color:#1d4ed8;font-weight:600}`
            + `.k-tnav-back{color:#64748b}`
            + `.k-tnav-cta{padding:.3rem .65rem;border:1px solid #cbd5e1;border-radius:.375rem;color:#64748b;text-decoration:none;font-size:12px}`
            + `.k-tnav-cta:hover{color:#0f172a;border-color:#94a3b8}`
            + `[data-theme="dark"] .k-tnav{border-color:rgba(255,255,255,.1)}`
            + `[data-theme="dark"] .k-tnav-link{color:rgba(255,255,255,.75)}`
            + `[data-theme="dark"] .k-tnav-link:hover{background:rgba(255,255,255,.1);color:#fff}`
            + `[data-theme="dark"] .k-tnav-active{background:rgba(59,130,246,.28);color:#fff}`
            + `[data-theme="dark"] .k-tnav-back{color:rgba(255,255,255,.6)}`
            + `[data-theme="dark"] .k-tnav-cta{border-color:rgba(255,255,255,.2);color:rgba(255,255,255,.6)}`
            + `[data-theme="dark"] .k-tnav-cta:hover{color:#fff;border-color:rgba(255,255,255,.4)}`;
        const style = document.createElement('style');
        style.id = 'k-tnav-style';
        style.textContent = css;
        document.head.appendChild(style);
    }
};

// Initialize theme immediately (before Alpine loads)
// Expose on window so ext scripts can register embed renderers via
// `window.KarateReport.registerEmbed(...)`. A top-level `const` is a global lexical
// binding (visible to inline on* handlers and Alpine expressions) but is NOT a window
// property — without this, a separate ext <script> reaching for window.KarateReport
// would see undefined and silently skip registration.
window.KarateReport = KarateReport;

KarateReport.initTheme();
