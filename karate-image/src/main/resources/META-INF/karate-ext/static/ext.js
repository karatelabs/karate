/*
 * karate-image report ext — client-side renderer for the `image-comparison` embed.
 *
 * Milestone 2 stub: the multi-part embed ({name:"image-comparison", parts:[baseline,
 * latest, diff], meta}) already renders its images via core's generic _renderEmbedPart.
 * Milestone 3 replaces this with the interactive lightbox (slider / blink / onion-skin,
 * Resemble.js loaded lazily from CDN) registered as an Alpine component keyed by the
 * embed name. Kept as a valid no-op so the asset pipeline (copy + <script defer> splice)
 * is exercised end-to-end now.
 */
(function () {
    'use strict';
    // Milestone 3: document.addEventListener('alpine:init', () => Alpine.data('imageComparison', ...))
})();
