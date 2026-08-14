/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: 'var(--color-brand)',
          fill: 'var(--color-brand-fill)',
          'fill-hover': 'var(--color-brand-fill-hover)',
          soft: 'var(--color-brand-soft)',
          muted: 'var(--color-brand-muted)',
        },
        ink: 'var(--color-ink)',
        muted: 'var(--color-muted)',
        canvas: 'var(--color-canvas)',
        panel: 'var(--color-panel)',
        subtle: 'var(--color-subtle)',
        wash: 'var(--color-hover)',
        line: 'var(--color-line)',
        overlay: 'var(--color-overlay)',
        placeholder: 'var(--color-placeholder)',
        danger: {
          DEFAULT: 'var(--color-danger)',
          fill: 'var(--color-danger-fill)',
          soft: 'var(--color-danger-soft)',
        },
        success: {
          DEFAULT: 'var(--color-success)',
          soft: 'var(--color-success-soft)',
        },
        warning: {
          DEFAULT: 'var(--color-warning)',
          soft: 'var(--color-warning-soft)',
          line: 'var(--color-warning-line)',
        },
      },
      boxShadow: { panel: 'var(--shadow-panel)' },
    },
  },
  plugins: [],
}
