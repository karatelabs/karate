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
        detail.querySelectorAll('[data-deferred]').forEach(deferred => {
            const script = document.createElement('script')
            script.type = 'text/javascript'
            script.src = deferred.dataset.src
            deferred.parentNode.replaceChild(script, deferred)
        })
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

        if (step.hook) {
            html += ` <span class="badge bg-secondary ms-1" title="lifecycle hook">hook</span>`;
        }

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
        } else if (mime === 'text/x-deferred-javascript') {
            html += `<div data-deferred="true" data-src="../embeds/${this._esc(embed.file)}">Loading...</div>`;
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

function newDiffUI(targetElement, diffResult, diffConfig, onShowRebase, onShowConfig) {
    diffConfig = diffConfig || {}
    diffConfig.tolerances = diffConfig.tolerances || {}

    // setup common vars
    const $el = $('<div class="diff-ui"></div>').appendTo($(targetElement).parent());
    const ignoredBoxes = (diffConfig.ignoredBoxes || []).map((ignoredBox, i) => Object.assign({ id: i }, ignoredBox))
    const tolerances = ['red', 'green', 'blue', 'alpha', 'minBrightness', 'maxBrightness'].reduce((tols, toleranceName) => {
        if (diffConfig.tolerances[toleranceName] !== undefined) tols[toleranceName] = diffConfig.tolerances[optionName]
        return tols
    }, {})
    let firstPaintComplete = false
    let resembleControl

    // add UI dynamically to avoid repeating html on-disk for each screenshot
    createHtml()

    // create baseline and latest images
    $el.find('.baseline').append($('<img/>').attr('src', diffResult.baseline))
    $el.find('.img-container').append($('<img class="hidden"/>').attr('src', diffResult.baseline))
    $el.find('.latest').append($('<img/>').attr('src', diffResult.latest))
    $el.find('.compareContainer').prepend($('<img class="hidden"/>').attr('src', diffResult.baseline))
    $el.find('.baselineImgContainer').css('background-image', `url(${diffResult.baseline})`)
    $el.find('.latestImgContainer').css('background-image', `url(${diffResult.latest})`)

    // bind dropdown config options
    $el.find('.error-type').val(diffConfig.errorType || 'flat')
    $el.find('.ignore-config').val(diffConfig.ignore || 'less')
    $el.find('.resemble-select').change(function() {
        const val = this.value

        const ignorePresets = {
            'nothing':      resembleControl.ignoreNothing,
            'less':         resembleControl.ignoreLess,
            'colors':       resembleControl.ignoreColors,
            'antialiasing': resembleControl.ignoreAntialiasing,
            'alpha':        resembleControl.ignoreAlpha
        }
        if (ignorePresets.hasOwnProperty(val)) {
            diffConfig.ignore = val
            ignorePresets[val]();
        }

        if (['flat', 'movement', 'flatDifferenceIntensity', 'movementDifferenceIntensity', 'diffOnly'].includes(val)) {
            resembleControl.outputSettings({ errorType: val }).repaint()
            diffConfig.errorType = val
        }
    })

    // bind button toggle config options
    if (diffConfig.errorColor && Object.keys(diffConfig.errorColor).length > 0) {
        $('.pink').removeClass('active')
        $('.yellow').removeClass('active')
        if (diffConfig.errorColor.red === 255 && diffConfig.errorColor.green === 0 && diffConfig.errorColor.blue === 255) {
            $('.pink').addClass('active')
        } else if (diffConfig.errorColor.red === 255 && diffConfig.errorColor.green === 255 && diffConfig.errorColor.blue === 0) {
            $('.yellow').addClass('active')
        }
    }
    if (diffConfig.transparency !== undefined && diffConfig.transparency < 1) {
        $('.opaque').removeClass('active')
        $('.transparent').addClass('active')
    }
    $el.find('.btn').click((e) => {
        const $this = $(e.currentTarget)

        $this.parent().find('button').removeClass('active')
        $this.addClass('active')

        if ($this.hasClass('pink')) {
            diffConfig.errorColor = { red: 255, green: 0, blue: 255, alpha: 255 }
            resembleControl
                .outputSettings({
                    errorColor: {
                        red: 255,
                        green: 0,
                        blue: 255
                    }
                })
                .repaint()
        } else if ($this.hasClass('yellow')) {
            diffConfig.errorColor = { red: 255, green: 255, blue: 0, alpha: 255 }
            resembleControl
                .outputSettings({
                    errorColor: {
                        red: 255,
                        green: 255,
                        blue: 0
                    }
                })
                .repaint()
        } else if ($this.hasClass('opaque')) {
            resembleControl.outputSettings({ transparency: 1 }).repaint()
            diffConfig.transparency = 1.0
        } else if ($this.hasClass('transparent')) {
            resembleControl.outputSettings({ transparency: 0.3 }).repaint()
            diffConfig.transparency = 0.3
        }
    })

    // bind ignored box actions
    $el.on('click', '.ignored-box-ui:not(.active)', (e) => activateIgnoredBox(parseInt($(e.currentTarget).data('box-id'), 10)))
    $el.on('click', '.ignored-box-ui.active', (e) => {
        const $this = $(e.currentTarget)
        // ignore click events that bubble up while we're resizing / dragging an ignore box
        if ($this.hasClass('ui-resizable-resizing') || $this.hasClass('ui-draggable-dragging')) return
        updateIgnoredBox(parseInt($(e.currentTarget).data('box-id'), 10))
    })
    $el.on('contextmenu', '.ignored-box-ui', removeIgnoredBox)
    $el.on('click', '.removeIgnoredBox', removeIgnoredBox)

    // bind 'show config' button click
    $el.find('.showConfig').click(() => {
        const diffOptions = {}

        if (diffConfig.name) {
            diffOptions.name = diffConfig.name
        }

        if (diffResult.engine !== diffResult.defaultEngine) {
            diffOptions.engine = diffResult.engine
        }

        if (diffResult.failureThreshold !== diffResult.defaultFailureThreshold) {
            diffOptions.failureThreshold = diffResult.failureThreshold
        }

        const ignoreOption = $el.find('.ignore-config').val()
        if (ignoreOption !== 'less') diffOptions.ignore = ignoreOption

        if (ignoreOption === diffConfig.ignore && Object.keys(tolerances).length > 0) {
            diffOptions.tolerances = tolerances
        }

        if (diffConfig.ignoreAreasColoredWith) {
            diffOptions.ignoreAreasColoredWith = diffConfig.ignoreAreasColoredWith
        }

        if (diffConfig.transparency !== undefined && diffConfig.transparency >= 0 && diffConfig.transparency < 1.0) {
            diffOptions.transparency = diffConfig.transparency
        }

        if (diffConfig.errorType && diffConfig.errorType !== 'flat') {
            diffOptions.errorType = diffConfig.errorType
        }


        if (diffConfig.errorColor && Object.keys(diffConfig.errorColor).length > 0) {
            const isPink = diffConfig.errorColor.red === 255 && diffConfig.errorColor.green === 0 && diffConfig.errorColor.blue === 255
            if (!isPink) {
                diffOptions.errorColor = diffConfig.errorColor
            }
        }

        const boxes = ignoredBoxes.map((ignoredBox) => {
            return {
                top: ignoredBox.top,
                left: ignoredBox.left,
                bottom: ignoredBox.bottom,
                right: ignoredBox.right
            }
        })

        if (boxes.length) {
            diffOptions.ignoredBoxes = boxes
        }

        const formatFn = onShowConfig || ((x) => x);

        $el.find('.configModal pre').text(formatFn(JSON.stringify(diffOptions, null, 2), diffConfig))
        $el.find('.configModal .copyConfig').addClass('btn-light').removeClass('btn-success')
        $el.find('.configModal').modal('toggle')
    })

    // bind 'rebase' button click
    $el.find('.rebase').click(() => {
        if (!onShowRebase) return downloadLatest()

        const txt = onShowRebase(diffConfig, downloadLatest)
        if (!txt) return

        $el.find('.rebaseModal pre').text(txt)
        $el.find('.rebaseModal .copyCmd').addClass('btn-light').removeClass('btn-success')
        $el.find('.rebaseModal').modal('toggle')
    })

    // bind 'copy' button click for modals
    $el.on('click', '.copy', (e) => {
        const $this = $(e.currentTarget)
        const $tmpTextArea = $('<textarea/>')
        $('body').append($tmpTextArea);
        $tmpTextArea.val($this.closest('.modal').find('pre').text()).select()
        try { document.execCommand('copy') } catch (err) {}
        try { navigator.clipboard.writeText($tmpTextArea.val()) } catch (err) {}
        $tmpTextArea.remove()
        $this.removeClass('btn-light').addClass('btn-success')
    })

    // bind baseline / latest image click events
    const $slider = $el.find('.compareModal .slider')
    $el.find('.baseline img, .latest img').click(() => {
        $slider.css('left', 'calc(50% - 4px)')
        $el.find('.compareModal .modal-body').removeClass('zoomed')
        $el.find('.compareModal').modal('toggle')
    })
    $el.find('.compareContainer').on('click', (e) => $(e.currentTarget).closest('.modal-body').toggleClass('zoomed'))

    // bind comparison slider to mouse movement
    const $baselineImgContainer = $el.find('.baselineImgContainer')
    $el.find('.compareContainer').on('mousemove', function (e) {
        const $this = $(this)
        const maxWidth = $this.find('img:first')[0].clientWidth - 4
        const offsetX = e.pageX - $this.offset().left

        let sliderX = offsetX <= 4 ? 0 : offsetX - 4
        if (sliderX > maxWidth) sliderX = maxWidth

        $slider.css('left', sliderX)
        $baselineImgContainer.css('right', maxWidth - sliderX)
    })

    // redraw ignore boxes on window resize
    let windowResizeThrottle = { timer: null, isPending: false }
    $(window).resize((e) => {
        if (e.target !== window) return

        if (windowResizeThrottle.timer) {
            windowResizeThrottle.isPending = true
            return
        }

        redrawIgnoreBoxes()

        windowResizeThrottle.timer = setInterval(() => {
            if (!windowResizeThrottle.isPending) {
                clearInterval(windowResizeThrottle.timer)
                windowResizeThrottle.timer = null
                return
            }

            windowResizeThrottle.isPending = false
            redrawIgnoreBoxes()
        }, 50)
    })

    // wait for step contents animation to complete and then execute the diff
    setTimeout(compareImg, 300)


    // -- begin helper function definitions -- \\

    function downloadLatest(filename) {
        const format = diffResult.latest.substring(11, diffResult.latest.indexOf(';'))
        const a = document.createElement('a');
        a.href = diffResult.latest;
        a.download = filename || ('latest.' + format);
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }

    function addIgnoredBox(id, isActive) {
        $el.find('.image-diff').append(`<div data-box-id="${id}" class="ignored-box-ui ignored-box-ui-${id}"></div>`)

        updateIgnoredBox(id, isActive)

        if (isActive) activateIgnoredBox(id)
    }

    function removeIgnoredBox(e) {
        e.preventDefault()

        const boxId = parseInt($(e.currentTarget).data('box-id'), 10)
        $el.find(`.ignored-box-ui-${boxId}`).remove()

        const boxIndex = ignoredBoxes.findIndex((ignoredBox) => ignoredBox.id === boxId)
        ignoredBoxes.splice(boxIndex, 1)

        resembleControl.outputSettings({ ignoredBoxes }).repaint()
    }

    function redrawIgnoreBoxes () {
        $el.find('.ignored-box-ui').each(function () {
            const boxId = parseInt($(this).data('box-id'), 10)
            updateIgnoredBox(boxId, true)
        })
    }

    function updateIgnoredBox(e, suppressRepaint) {
        const boxId = $.isNumeric(e) ? e : parseInt($(e.currentTarget).data('box-id'), 10)
        const ignoredBox = ignoredBoxes.find((ignoredBox) => ignoredBox.id === boxId)
        const { scale, maxWidth, maxHeight } = calcScale()

        ignoredBox.left = greaterOf(0, ignoredBox.left)
        ignoredBox.top = greaterOf(0, ignoredBox.top)
        ignoredBox.right = lessOf(maxWidth, ignoredBox.right)
        ignoredBox.bottom = lessOf(maxHeight, ignoredBox.bottom)

        // force sane values
        if (ignoredBox.left >= ignoredBox.right) {
            ignoredBox.right = lessOf(maxWidth, ignoredBox.left + 5)
            ignoredBox.left = greaterOf(0, ignoredBox.right - 5)
        }

        if (ignoredBox.top >= ignoredBox.bottom) {
            ignoredBox.bottom = lessOf(maxHeight, ignoredBox.top + 5)
            ignoredBox.top = greaterOf(0, ignoredBox.bottom - 5)
        }

        $el.find(`.ignored-box-ui-${boxId}`).css({
            width: (ignoredBox.right - ignoredBox.left) / scale,
            height: (ignoredBox.bottom - ignoredBox.top) / scale,
            top: ignoredBox.top / scale,
            left: ignoredBox.left / scale
        })

        if (!suppressRepaint) {
            resembleControl.outputSettings({ ignoredBoxes }).repaint()
            activateIgnoredBox(null)
        }
    }

    function activateIgnoredBox(id) {
        $el.find('.ignored-box-ui.active').removeClass('active').each(function () {
            $(this).resizable('destroy').draggable('destroy')
        })

        if (id === null) return

        $el.find(`.ignored-box-ui-${id}`)
            .addClass('active')
            .resizable({
                handles: 'all',
                containment: 'parent',
                minHeight: 10,
                minWidth: 10,
                resize(e, ui) {
                    const ignoredBox = ignoredBoxes.find((ignoredBox) => ignoredBox.id === id)
                    const { scale, maxWidth, maxHeight } = calcScale()

                    ignoredBox.left = greaterOf(0, ui.position.left * scale)
                    ignoredBox.top = greaterOf(0, ui.position.top * scale)
                    ignoredBox.right = lessOf(maxWidth, (ui.position.left + ui.size.width) * scale)
                    ignoredBox.bottom = lessOf(maxHeight, (ui.position.top + ui.size.height) * scale)
                }
            })
            .draggable({
                containment: 'parent',
                drag(e, ui) {
                    const ignoredBox = ignoredBoxes.find((ignoredBox) => ignoredBox.id === id)
                    const { scale, maxWidth, maxHeight } = calcScale()
                    const width = (parseInt(ignoredBox.right, 10) - parseInt(ignoredBox.left, 10))
                    const height = (parseInt(ignoredBox.bottom, 10) - parseInt(ignoredBox.top, 10))

                    if (width === maxWidth) {
                        ignoredBox.left = 0
                        ignoredBox.right = width
                    } else {
                        const scaledWidth = width / scale
                        ignoredBox.left = greaterOf(0, ui.position.left * scale)
                        ignoredBox.right = lessOf(maxWidth, (ui.position.left + scaledWidth) * scale)
                    }

                    if (height === maxHeight) {
                        ignoredBox.top = 0
                        ignoredBox.bottom = height
                    } else {
                        const scaledHeight = height / scale
                        ignoredBox.top = greaterOf(0, ui.position.top * scale)
                        ignoredBox.bottom = lessOf(maxHeight, (ui.position.top + scaledHeight) * scale)
                    }
                }
            })
    }

    function lessOf(a, b) {
        a = parseInt(a, 10)
        b = parseInt(b, 10)
        return a < b ? a : b
    }

    function greaterOf(a, b) {
        a = parseInt(a, 10)
        b = parseInt(b, 10)
        return a > b ? a : b
    }

    function calcScale() {
        const diffImg = $el.find('.img-container img')[0]
        return {
            scale: diffImg.naturalWidth / diffImg.clientWidth,
            maxHeight: diffImg.naturalHeight,
            maxWidth: diffImg.naturalWidth
        }
    }

    function compareImg() {
        const outputSettings = { ignoredBoxes, largeImageThreshold: 0}
        if (diffConfig.ignoreAreasColoredWith) outputSettings.ignoreAreasColoredWith = diffConfig.ignoreAreasColoredWith
        if (diffConfig.transparency !== undefined) outputSettings.transparency = diffConfig.transparency
        if (diffConfig.errorColor) outputSettings.errorColor = diffConfig.errorColor
        if (diffConfig.errorType) outputSettings.errorType = diffConfig.errorType

        resembleControl = resemble(diffResult.baseline)
            .compareTo(diffResult.latest)
            .outputSettings(outputSettings)

        switch ($el.find('.ignore-config').val()) {
            case 'nothing':
                resembleControl.ignoreNothing()
                break
            case 'antialiasing':
                resembleControl.ignoreAntialiasing()
                break
            case 'colors':
                resembleControl.ignoreColors()
                break
            case 'alpha':
                resembleControl.ignoreAlpha()
                break
            default:
                resembleControl.ignoreLess()
        }

        resembleControl.setupCustomTolerance(tolerances)

        resembleControl = resembleControl.onComplete((data) => {
            const outputImage = new Image()
            if (!firstPaintComplete) {
                firstPaintComplete = true
                outputImage.onload = () => {
                    ignoredBoxes.forEach((ignoredBox) => addIgnoredBox(ignoredBox.id, false))
                }
            }

            outputImage.src = data.getImageDataUrl()

            $el.find('.img-container').html(outputImage)

            // open full-size diff image in modal
            $(outputImage).click(() => {
                $el.find('.fullScreenModal .modal-body').html($('<img/>').attr('src', outputImage.src))
                $el.find('.fullScreenModal .modal-body img').click((e) => $(e.currentTarget).closest('.modal-body').toggleClass('zoomed'))
                $el.find('.fullScreenModal .modal-body').removeClass('zoomed')
                $el.find('.fullScreenModal').modal('toggle')
            })

            // right-click adds new ignore box
            $(outputImage).contextmenu(function (e) {
                e.preventDefault()

                const $this = $(this)
                const { scale } = calcScale()
                const offsetX = e.pageX - $this.offset().left - 50
                const offsetY = e.pageY - $this.offset().top - 50
                const boxId = ignoredBoxes.length

                ignoredBoxes.push({
                    id: boxId,
                    left: offsetX * scale,
                    top: offsetY * scale,
                    right: (offsetX + 100) * scale,
                    bottom: (offsetY + 100) * scale
                })

                addIgnoredBox(boxId, true)
            })

            $el.find('.mismatch').text(data.misMatchPercentage)
        })
    }

    function createHtml() {
        $el.html(`
    <div class="diff-ui-screenshots">
      <div class="baseline">
        <h3>Baseline</h3>
      </div>
      <div class="diff">
        <h3>Diff</h3>
        <div class="image-diff">
          <div class="img-container"></div>
        </div>
      </div>
      <div class="latest">
        <h3>Latest</h3>
      </div>
    </div>
    <div class="diff-ui-inset">
      <div class="diff-results">
        <div>
          <strong>
            The second image is <span class="mismatch">0.00</span>% different compared to the first.
          </strong>
          <em>
            ${diffResult.ssimMismatchPercentage === undefined ? '' : '(SSIM reported ' + diffResult.ssimMismatchPercentage.toFixed(2) + '% difference)'}
          </em>
        </div>
      </div>
      <div class="diff-ui-controls">
        <div class="form-row">
          <div class="form-group col-sm-4">
            <select class="form-control form-control-sm resemble-select ignore-config">
              <option value="less">Ignore less</option>
              <option value="nothing">Ignore nothing</option>
              <option value="colors">Ignore colors</option>
              <option value="antialiasing">Ignore antialiasing</option>
              <option value="alpha">Ignore alpha</option>
            </select>
          </div>
          <div class="form-group col-sm-4">
            <select class="form-control form-control-sm resemble-select error-type">
              <option value="flat" selected>Flat</option>
              <option value="movement">Movement</option>
              <option value="flatDifferenceIntensity">Flat with diff intensity</option>
              <option value="movementDifferenceIntensity">Movement with diff intensity</option>
              <option value="diffOnly">Diff portion from the input</option>
            </select>
          </div>
          <div class="form-group col-sm-4">
            <button class="btn btn-sm btn-secondary form-control form-control-sm rebase">Rebase</button>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group col-sm-4 btn-group" role="group">
            <button class="btn btn-sm active pink">Pink</button>
            <button class="btn btn-sm light yellow">Yellow</button>
          </div>
          <div class="form-group col-sm-4 btn-group" role="group">
            <button class="btn btn-sm active opaque">Opaque</button>
            <button class="btn btn-sm light transparent">Transparent</button>
          </div>
          <div class="form-group col-sm-4">
            <button class="btn btn-sm btn-info form-control form-control-sm showConfig">Show config</button>
          </div>
        </div>
      </div>
    </div>
    <div class="modal modal-dialog-centered fit-content configModal" tabindex="-1" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Image Comparison Config</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body"><pre></pre></div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
            <button type="button" class="btn btn-light copy">Copy</button>
          </div>
        </div>
      </div>
    </div>
    <div class="modal modal-dialog-centered fit-content rebaseModal" tabindex="-1" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Rebase</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body">
            <pre></pre>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
            <button type="button" class="btn btn-light copy">Copy</button>
          </div>
        </div>
      </div>
    </div>
    <div class="modal modal-dialog-centered full-screen fullScreenModal" tabindex="-1" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Diff</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body"></div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
          </div>
        </div>
      </div>
    </div>
    <div class="modal modal-dialog-centered full-screen compareModal" tabindex="-1" role="dialog">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Diff</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div class="compareContainer">
              <div class="latestImgContainer"></div>
              <div class="baselineImgContainer"></div>
              <div class="slider"><div></div></div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
          </div>
        </div>
      </div>
    </div>`)
    }
}
