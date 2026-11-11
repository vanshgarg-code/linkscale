import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Proxy API requests to backend
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        // Preserve cookies for session handling
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            const cookies = proxyRes.headers['set-cookie'];
            if (cookies) {
              // Ensure cookie path is '/' for frontend access
              proxyRes.headers['set-cookie'] = cookies.map((c: string) => c.replace(/;\s*Path=\/[^;]*/, '; Path=/'));
            }
          });
        },
      },
    },
  },
});

