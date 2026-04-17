/**
 * Karate v2 Report - Shared Utilities
 * Centralized JS for all report pages (feature, summary, timeline)
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
     * Apply theme to document and save to localStorage
     */
    setTheme(theme) {
        document.documentElement.setAttribute('data-bs-theme', theme);
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

    // ========== Step Rendering (recursive) ==========

    _stepId: 0,

    /**
     * Toggle expand/collapse for a step's detail section.
     */
    toggleStep(btn) {
        const container = btn.closest('.k-step');
        if (!container) return;
        const detail = container.querySelector(':scope > .k-step-detail');
        if (!detail) return;
        const expanded = detail.style.display !== 'none';
        detail.style.display = expanded ? 'none' : 'block';
        // Update badges visibility
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

    _esc(s) {
        if (s == null) return '';
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    },

    _renderStep(step) {
        const id = 'ks-' + (this._stepId++);
        const hasDetail = step.hasLogs || step.hasEmbeds || step.hasCallResults;
        const clickable = hasDetail ? 'clickable' : '';
        const onclick = hasDetail ? `onclick="KarateReport.toggleStep(this)"` : '';
        const statusClass = step.status === 'passed' ? 'text-success' : step.status === 'failed' ? 'text-danger' : step.status === 'skipped' ? 'text-warning' : '';

        let html = `<div class="k-step" id="${id}">`;

        // Comments
        if (step.comments && step.comments.length) {
            html += `<div class="step-comments px-2 py-1 text-muted fst-italic small" style="background-color: var(--bs-tertiary-bg);">`;
            step.comments.forEach(c => { html += `<div>${this._esc(c)}</div>`; });
            html += `</div>`;
        }

        // Step row
        html += `<div class="step-row d-flex align-items-start px-2 ${clickable}" ${onclick}>`;
        html += `<div class="text-muted me-2" style="width: 40px; text-align: right;"><small>[${step.line}]</small></div>`;
        html += `<div class="flex-grow-1 ${statusClass}">`;
        html += `<span class="text-muted">${this._esc(step.prefix)}</span> `;
        html += `<span class="fw-bold">${this._esc(step.keyword)}</span> `;
        html += `<span>${this._esc(step.text)}</span>`;

        // Collapsed badges
        if (step.hasLogs) {
            html += ` <span class="badge bg-secondary ms-1 k-badge-collapsed" title="Has logs - click to expand">log</span>`;
        }
        if (step.hasEmbeds) {
            const n = step.embeds?.length || 0;
            html += ` <span class="badge bg-info ms-1 k-badge-collapsed" title="${n} embed(s)">${n} embed${n > 1 ? 's' : ''}</span>`;
        }
        if (step.hasCallResults) {
            const n = step.callResults?.length || 0;
            if (n === 1) {
                html += ` <span class="badge bg-info ms-1 k-badge-collapsed" title="Called feature - click to expand">call</span>`;
            } else if (n > 1) {
                html += ` <span class="badge bg-info ms-1 k-badge-collapsed" title="${n} call iterations">${n} calls</span>`;
            }
        }

        html += `</div>`;
        html += `<div class="text-muted ms-2" style="width: 70px; text-align: right;"><small>${step.durationMillis} ms</small></div>`;
        html += `</div>`; // end step-row

        // Detail section (hidden by default)
        if (hasDetail) {
            html += `<div class="k-step-detail" style="display: none;">`;

            // Logs
            if (step.logs) {
                html += `<div class="step-logs"><pre class="mb-0 text-body">${this._esc(step.logs)}</pre></div>`;
            }

            // Embeds
            if (step.embeds && step.embeds.length) {
                html += `<div class="step-embeds mx-2 my-2">`;
                step.embeds.forEach(embed => {
                    html += this._renderEmbed(embed);
                });
                html += `</div>`;
            }

            // Error
            if (step.error) {
                html += `<div class="alert alert-danger py-1 mx-2 my-1 small"><pre class="mb-0">${this._esc(step.error)}</pre></div>`;
            }

            // Call results (recursive)
            if (step.callResults && step.callResults.length) {
                html += `<div class="nested-call">`;
                step.callResults.forEach((cr, fidx) => {
                    html += `<div class="mb-3 pb-2${fidx < step.callResults.length - 1 ? ' border-bottom' : ''}">`;
                    // Header
                    if (step.callResults.length > 1) {
                        html += `<div class="d-flex align-items-center mb-2">`;
                        html += `<span class="badge bg-secondary me-2">#${fidx + 1}</span>`;
                        html += `<small class="text-muted">${this._esc(cr.path || cr.name)}</small>`;
                        html += `<small class="text-muted ms-2">${cr.durationMillis} ms</small>`;
                        html += cr.passed ? ' <span class="badge bg-success ms-2">PASS</span>' : ' <span class="badge bg-danger ms-2">FAIL</span>';
                        html += `</div>`;
                    } else {
                        html += `<div class="small text-muted mb-1">`;
                        html += `<span>${this._esc(cr.path || cr.name)}</span>`;
                        html += `<span class="ms-2">${cr.durationMillis} ms</span>`;
                        html += `</div>`;
                    }
                    // Nested scenarios
                    (cr.scenarios || []).forEach(nested => {
                        const nStatus = nested.passed ? 'border-success' : 'border-danger';
                        html += `<div class="nested-scenario border ${nStatus}">`;
                        html += `<div class="d-flex justify-content-between align-items-center mb-1">`;
                        html += `<span><span class="fw-bold">${this._esc(nested.refId || '')}</span> <span>${this._esc(nested.name)}</span></span>`;
                        html += `<span><small class="text-muted me-2">${nested.durationMillis} ms</small>`;
                        html += nested.passed ? '<span class="badge bg-success">PASS</span>' : '<span class="badge bg-danger">FAIL</span>';
                        html += `</span></div>`;
                        // Recursive step rendering
                        html += this.renderSteps(nested.steps);
                        // Scenario error
                        if (nested.error) {
                            html += `<div class="alert alert-danger py-1 my-1 small"><pre class="mb-0">${this._esc(nested.error)}</pre></div>`;
                        }
                        html += `</div>`;
                    });
                    html += `</div>`;
                });
                html += `</div>`;
            }

            html += `</div>`; // end k-step-detail
        } else if (step.error) {
            // Error without other detail (not hidden)
            html += `<div class="alert alert-danger py-1 mx-2 my-1 small"><pre class="mb-0">${this._esc(step.error)}</pre></div>`;
        }

        html += `</div>`; // end k-step
        return html;
    },

    _renderEmbed(embed) {
        let html = `<div class="embed-item border rounded p-2 mb-2 bg-body-secondary">`;
        if (embed.name) {
            html += `<div class="small text-muted mb-1">${this._esc(embed.name)}</div>`;
        }
        const mime = embed.mime_type || '';
        if (mime.startsWith('image/')) {
            html += `<img src="../embeds/${this._esc(embed.file)}" class="img-fluid" style="max-height: 400px;" alt="${this._esc(embed.name || 'embedded image')}">`;
        } else if (mime === 'text/html') {
            html += `<iframe src="../embeds/${this._esc(embed.file)}" class="w-100 border-0" style="height: 300px;"></iframe>`;
        } else if (mime.startsWith('video/')) {
            html += `<video controls class="w-100" style="max-height: 400px;"><source src="../embeds/${this._esc(embed.file)}" type="${this._esc(mime)}"></video>`;
        } else if (mime === 'application/pdf') {
            html += `<embed src="../embeds/${this._esc(embed.file)}" type="application/pdf" class="w-100" style="height: 400px;">`;
        } else if (mime === 'text/plain' || mime === 'application/json' || mime === 'application/xml') {
            if (embed.file) {
                html += `<a href="../embeds/${this._esc(embed.file)}" class="small" target="_blank">Open file</a>`;
            }
            if (embed.data) {
                html += `<pre class="bg-dark text-light p-2 rounded mb-0 small" style="max-height: 300px; overflow: auto;">${this._esc(atob(embed.data))}</pre>`;
            }
        } else if (embed.file) {
            html += `<a href="../embeds/${this._esc(embed.file)}" class="btn btn-sm btn-outline-secondary" download>Download (${this._esc(mime)})</a>`;
        }
        html += `</div>`;
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

            get featureSimpleName() {
                const p = (data.relativePath || data.path || data.name || '').toString();
                const base = p.split('/').pop().split('\\').pop();
                return base.replace(/\.feature$/, '') || 'Feature';
            },

            toggleTheme() {
                this.theme = self.toggleTheme();
            },

            truncate(str, len) {
                return self.truncate(str, len);
            },

            renderSteps(steps) {
                return self.renderSteps(steps);
            }
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

        return {
            data,
            allTags,
            selectedTags: [],
            highlightMode: false,
            sortField: 'name',
            sortDir: 'asc',
            theme: self.getTheme(),

            toggleTheme() {
                this.theme = self.toggleTheme();
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
                let features = (this.data.features || []).map(f => {
                    const p = f.passedCount || 0;
                    const fl = f.failedCount || 0;
                    const executed = p + fl;
                    const passRate = executed === 0 ? null : Math.round((p / executed) * 100);
                    return Object.assign({}, f, { passRate });
                });
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
                t.passRate = executed === 0 ? null : Math.round((t.passed / executed) * 100);
                return t;
            },

            formatPassRate(rate) {
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

            toggleTheme() {
                this.theme = self.toggleTheme();
            }
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
