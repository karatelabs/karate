// Karate report Tailwind config. See IMAGE_SPIKE.md D20 — utility classes only,
// no @layer components, slate-blue Karate Labs brand palette, system fonts only
// (zero CDN dependency for fonts).
//
// Two consumers:
//   1. Production CSS build: `mvn -pl karate-core karate:tailwind` (see IMAGE_SPIKE.md §3.1) —
//      not yet wired; this config will be used when the Maven mojo lands.
//   2. Dev preview harness: src/main/tailwind/preview/*.html via the Play CDN — same config
//      via inline `<script>tailwind.config = {...}</script>` (see IMAGE_SPIKE.md §3.1.1).

/** @type {import('tailwindcss').Config} */
module.exports = {
    content: [
        '../resources/io/karatelabs/output/*.html',
        './preview/*.html',
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
