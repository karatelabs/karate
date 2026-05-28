// Karate report Tailwind config. See IMAGE_SPIKE.md D20 — utility classes only,
// no @layer components, slate-blue Karate Labs brand palette, system fonts only
// (zero CDN dependency for fonts).
//
// Used by:
//   - Production CSS build (future Maven mojo `mvn -pl karate-core karate:tailwind` —
//     IMAGE_SPIKE.md §3.1). Until the mojo lands, regen by hand:
//       npx tailwindcss@3.4.17 -i input.css -o ../resources/io/karatelabs/output/res/karate-report.css --minify
//   - Dev iteration loop (`npx tailwindcss --watch …` per IMAGE_SPIKE.md §3.1.1).

/** @type {import('tailwindcss').Config} */
module.exports = {
    // data-theme="dark" on <html> toggles dark mode (set by karate-report.js).
    // Uses Tailwind v3 selector-based dark mode via custom selector strategy.
    darkMode: ['selector', '[data-theme="dark"]'],
    content: [
        // Class names used in templates (Alpine bindings inline classes).
        '../resources/io/karatelabs/output/*.html',
        // karate-report.js emits class names as HTML string fragments
        // (renderSteps / renderEmbed); Tailwind must scan it too or those classes
        // won't be in the generated CSS.
        '../resources/io/karatelabs/output/res/karate-report.js',
    ],
    theme: {
        extend: {
            colors: {
                brand: {
                    900: '#0f172a',
                    800: '#1e293b',
                    700: '#334155',
                    600: '#475569',
                    navbar: '#212529',
                },
                accent: {
                    DEFAULT: '#60a5fa',
                    light: '#93c5fd',
                    dark: '#3b82f6',
                },
                amber: {
                    DEFAULT: '#f59e0b',
                    light: '#fbbf24',
                },
                surface: {
                    50: '#f8fafc',
                    100: '#f1f5f9',
                    200: '#e2e8f0',
                    300: '#d1d5db',
                    700: '#334155',
                    800: '#1e293b',
                    900: '#0f172a',
                },
            },
            fontFamily: {
                sans: ['system-ui', '-apple-system', '"Segoe UI"', 'Roboto', '"Helvetica Neue"', 'Arial', 'sans-serif'],
                mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', 'monospace'],
            },
        },
    },
    plugins: [],
};
