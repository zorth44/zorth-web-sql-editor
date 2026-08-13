/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  theme: {
    extend: {
      colors: {
        brand: 'var(--color-brand)',
        ink: 'var(--color-ink)',
        muted: 'var(--color-muted)',
        canvas: 'var(--color-canvas)',
        panel: 'var(--color-panel)',
        line: 'var(--color-line)',
        danger: 'var(--color-danger)',
        success: 'var(--color-success)',
      },
      boxShadow: { panel: '0 10px 30px rgb(15 23 42 / 0.08)' },
    },
  },
  plugins: [],
}
