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
     * Initialize expanded state for steps in scenarios.
     * Sets step.expanded = false for all steps.
     */
    initStepExpanded(data) {
        if (data.scenarios) {
            data.scenarios.forEach(s => {
                if (s.steps) {
                    s.steps.forEach(step => {
                        step.expanded = false;
                    });
                }
            });
        }
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

            toggleTheme() {
                this.theme = self.toggleTheme();
            },

            truncate(str, len) {
                return self.truncate(str, len);
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
                let features = [...(this.data.features || [])];
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
