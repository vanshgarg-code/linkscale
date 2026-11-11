// tailwind.config.cjs
/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}'
  ],
  theme: {
    extend: {
      colors: {
        // New theme colors
        pageBg: '#E9F1FA', // page background
        surface: '#FFFFFF', // surfaces like cards
        primary: '#00ABE4', // primary actions & accents
        accent: '#00ABE4', // spare accent color
        darkNavy: '#001F3F' // dark navy text
      },
      fontFamily: {
        sans: ['Helvetica Neue', 'Arial', 'sans-serif']
      }
    }
  },
  plugins: []
};


