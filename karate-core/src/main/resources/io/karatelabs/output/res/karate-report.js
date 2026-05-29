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
        container.querySelectorAll(':scope > .step-row .k-badge-collapsed').forEach(el => {
            el.style.display = expanded ? '' : 'none';
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
     * Map a Karate keyword to a text color class. Only two keywords get colored:
     * `method` (the HTTP fire-the-request action) and `match` (the assertion).
     * Everything else stays slate. Class strings stay literal so Tailwind's
     * content scanner picks them up.
     */
    _keywordColor(keyword) {
        const k = (keyword || '').toLowerCase();
        if (k === 'method') return 'text-orange-600 dark:text-orange-400';
        if (k === 'match')  return 'text-blue-700 dark:text-blue-400';
        return                     'text-slate-700 dark:text-slate-300';
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
        html += `<div class="step-row flex items-start px-3 py-1 font-mono text-sm border-b border-slate-100 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/50 ${clickable}" ${onclick}>`;
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
            const n = step.embeds?.length || 0;
            html += ` <span class="${badgeBase} bg-accent ml-1 k-badge-collapsed" title="${n} embed(s)">${n} embed${n > 1 ? 's' : ''}</span>`;
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

            if (step.logs) {
                html += `<div class="mx-3 my-1 p-2 rounded bg-slate-100 dark:bg-slate-800 text-xs"><pre class="m-0 whitespace-pre-wrap">${this._esc(step.logs)}</pre></div>`;
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

    _renderEmbed(embed) {
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
            // Three possible segments: pass / fail / skip; each getter returns
            // {len, offset, dash} or null if that count is 0.
            get donutTotal() {
                const s = data.summary || {};
                return (s.scenario_passed || 0) + (s.scenario_failed || 0) + (s.scenario_skipped || 0);
            },
            get donutPass() {
                const total = this.donutTotal;
                const n = data.summary?.scenario_passed || 0;
                if (total === 0 || n === 0) return null;
                const len = (n / total) * 100;
                return { dash: len + ' ' + (100 - len), offset: 0 };
            },
            get donutFail() {
                const total = this.donutTotal;
                const n = data.summary?.scenario_failed || 0;
                if (total === 0 || n === 0) return null;
                const passed = data.summary?.scenario_passed || 0;
                const len = (n / total) * 100;
                return { dash: len + ' ' + (100 - len), offset: -(passed / total) * 100 };
            },
            get donutSkip() {
                const total = this.donutTotal;
                const n = data.summary?.scenario_skipped || 0;
                if (total === 0 || n === 0) return null;
                const before = (data.summary?.scenario_passed || 0) + (data.summary?.scenario_failed || 0);
                const len = (n / total) * 100;
                return { dash: len + ' ' + (100 - len), offset: -(before / total) * 100 };
            },

            get donutPct() {
                const s = data.summary || {};
                const p = s.scenario_passed || 0;
                const f = s.scenario_failed || 0;
                const k = s.scenario_skipped || 0;
                const total = p + f + k;
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
    }
};

// Initialize theme immediately (before Alpine loads)
KarateReport.initTheme();
