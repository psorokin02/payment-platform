import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api/antifraud': {
        target: 'http://127.0.0.1:8082',
        rewrite: (p) => p.replace('/api/antifraud', ''),
      },
      '/api/audit': {
        target: 'http://127.0.0.1:8083',
        rewrite: (p) => p.replace('/api/audit', ''),
      },
      '/api': { target: 'http://127.0.0.1:8081' },
    },
  },
});
