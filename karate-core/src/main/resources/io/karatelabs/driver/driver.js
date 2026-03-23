/**
 * Karate JS Runtime - Browser-side utilities for element discovery and interaction
 * Namespace: window.__kjs
 */
(function() {
    // Check if resolve already exists (may be extended by agent-look.js)
    if (window.__kjs && window.__kjs.resolve) return;

    // Initialize or extend __kjs
    var kjs = window.__kjs || (window.__kjs = {});

    // ==================== Logging ====================
    // LLMs can call __kjs.getLogs() to see what happened

    if (!kjs._logs) kjs._logs = [];

    kjs.log = function(msg, data) {
        var entry = {msg: msg, time: Date.now()};
        if (data) entry.data = data;
        this._logs.push(entry);
        if (this._logs.length > 100) this._logs.shift();
    };

    kjs.getLogs = function() {
        return this._logs;
    };

    kjs.clearLogs = function() {
        this._logs = [];
    };

    // ==================== Shadow DOM Utilities ====================

    kjs._hasShadowCache = undefined;

    /**
     * Check if page uses shadow DOM (cached per look() cycle).
     */
    kjs.hasShadowDOM = function() {
        if (this._hasShadowCache !== undefined) return this._hasShadowCache;
        var allElements = document.querySelectorAll('*');
        for (var i = 0; i < allElements.length; i++) {
            if (allElements[i].shadowRoot) {
                this._hasShadowCache = true;
                return true;
            }
        }
        this._hasShadowCache = false;
        return false;
    };

    /**
     * Recursively find ALL elements matching selector, including inside shadow roots.
     * Returns actual shadow elements (not hosts).
     */
    kjs.querySelectorAllDeep = function(selector, root) {
        root = root || document;
        var seen = new Set();
        var results = [];

        function collect(node) {
            var matches = node.querySelectorAll(selector);
            for (var i = 0; i < matches.length; i++) {
                if (!seen.has(matches[i])) {
                    seen.add(matches[i]);
                    results.push(matches[i]);
                }
            }
            // Recurse into shadow roots
            var allEls = node.querySelectorAll('*');
            for (var j = 0; j < allEls.length; j++) {
                if (allEls[j].shadowRoot) {
                    collect(allEls[j].shadowRoot);
                }
            }
        }

        collect(root);
        return results;
    };

    /**
     * Recursively find first element matching selector, including shadow roots.
     */
    kjs.querySelectorDeep = function(selector, root) {
        root = root || document;
        var el = root.querySelector(selector);
        if (el) return el;
        var allEls = root.querySelectorAll('*');
        for (var i = 0; i < allEls.length; i++) {
            if (allEls[i].shadowRoot) {
                el = kjs.querySelectorDeep(selector, allEls[i].shadowRoot);
                if (el) return el;
            }
        }
        return null;
    };

    /**
     * Convenience: querySelector with shadow DOM fallback.
     * Used by Locators.java for CSS selectors.
     */
    kjs.qsDeep = function(selector) {
        var el = document.querySelector(selector);
        if (el) return el;
        if (!this.hasShadowDOM()) return null;
        return this.querySelectorDeep(selector);
    };

    /**
     * Convenience: querySelectorAll with shadow DOM fallback.
     * Used by Locators.java for CSS selectors.
     */
    kjs.qsaDeep = function(selector) {
        if (!this.hasShadowDOM()) {
            return document.querySelectorAll(selector);
        }
        return this.querySelectorAllDeep(selector);
    };

    /**
     * Extract visible text from a shadow root's content.
     */
    kjs._getShadowText = function(shadowRoot) {
        if (!shadowRoot) return '';
        var text = '';
        var walker = document.createTreeWalker(shadowRoot, NodeFilter.SHOW_TEXT, null, false);
        var node;
        while ((node = walker.nextNode())) {
            var parent = node.parentElement;
            if (parent) {
                var style = window.getComputedStyle(parent);
                if (style.display === 'none' || style.visibility === 'hidden') continue;
            }
            text += node.textContent;
        }
        return text.trim().replace(/\s+/g, ' ');
    };

    // ==================== Shared Utilities ====================

    kjs.isVisible = function(el) {
        if (!el) return false;
        if (el.getAttribute('aria-hidden') === 'true') return false;
        var style = window.getComputedStyle(el);
        if (style.display === 'none') return false;
        if (style.visibility === 'hidden') return false;
        var rect = el.getBoundingClientRect();
        if (rect.width === 0 && rect.height === 0) return false;
        return true;
    };

    kjs.getVisibleText = function(el) {
        if (!el) return '';
        var text = '';
        var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null, false);
        var node;
        while ((node = walker.nextNode())) {
            var parent = node.parentElement;
            var hidden = false;
            while (parent && parent !== el) {
                if (parent.getAttribute('aria-hidden') === 'true') { hidden = true; break; }
                var style = window.getComputedStyle(parent);
                if (style.display === 'none' || style.visibility === 'hidden') { hidden = true; break; }
                parent = parent.parentElement;
            }
            if (!hidden) text += node.textContent;
        }
        var result = text.trim().replace(/\s+/g, ' ');
        // Fallback: if no light DOM text and element has shadow root, try shadow content
        if (!result && el.shadowRoot) {
            result = this._getShadowText(el.shadowRoot);
        }
        return result;
    };

    kjs.hasMatchingDescendant = function(el, text, contains, selector) {
        var descendants = el.querySelectorAll(selector);
        for (var i = 0; i < descendants.length; i++) {
            var desc = descendants[i];
            if (!this.isVisible(desc)) continue;
            var descText = this.getVisibleText(desc);
            if (!descText) continue;
            var matches = contains
                ? descText.indexOf(text) !== -1
                : descText === text;
            if (matches) return true;
        }
        return false;
    };

    kjs.getSelector = function(tag) {
        if (!tag || tag === '*') return '*';
        var map = {
            'button': 'button, [role="button"], input[type="submit"], input[type="button"]',
            'a': 'a[href], [role="link"]',
            'select': 'select, [role="combobox"], [role="listbox"]',
            'input': 'input:not([type="hidden"]), textarea, [role="textbox"]'
        };
        return map[tag] || tag;
    };

    // ==================== Wildcard Resolver ====================
    // Public API: __kjs.resolve(tag, text, index, contains)

    /**
     * Match candidates against text criteria.
     * Shared logic for light DOM and shadow DOM resolution.
     */
    kjs._resolveFromCandidates = function(candidates, tag, text, index, contains) {
        var selector = this.getSelector(tag);
        text = text.replace(/\s+/g, ' ').trim(); // normalize whitespace (including \u00a0)
        var count = 0;
        for (var i = 0; i < candidates.length; i++) {
            var el = candidates[i];
            if (!this.isVisible(el)) continue;
            var elText = this.getVisibleText(el);
            if (!elText) continue;
            var matches = contains
                ? elText.indexOf(text) !== -1
                : elText === text;
            if (matches) {
                if (this.hasMatchingDescendant(el, text, contains, selector)) continue;
                count++;
                if (count === index) return el;
            }
        }
        return null;
    };

    kjs.resolve = function(tag, text, index, contains) {
        var selector = this.getSelector(tag);
        // Try light DOM first
        var candidates = document.querySelectorAll(selector);
        var el = this._resolveFromCandidates(candidates, tag, text, index, contains);
        if (el) return el;
        // Shadow DOM fallback
        if (this.hasShadowDOM()) {
            var deepCandidates = this.querySelectorAllDeep(selector);
            el = this._resolveFromCandidates(deepCandidates, tag, text, index, contains);
            if (el) return el;
        }
        this.log('Wildcard not found', {tag: tag, text: text, index: index, contains: contains});
        return null;
    };
})();
