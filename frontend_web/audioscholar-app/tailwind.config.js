/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./App.jsx",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        teal: {
          50: '#f0fdfa',
          100: '#ccfbf1',
          200: '#99f6e4',
          300: '#5eead4',
          400: '#2dd4bf',
          500: '#2A9D8F',
          600: '#0d9488',
          700: '#0f766e',
          800: '#115e59',
          900: '#134e4a',
        },
        light: {
          blue: '#CBD5E1',
        },
        dark: {
          blue: '#1E293B',
        },
      },
    },
  },
  plugins: [],
}