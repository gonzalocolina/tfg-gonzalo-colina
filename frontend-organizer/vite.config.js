import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(() => {
  // Replace occurrences of `global` with `globalThis` during dependency optimization
  const replaceGlobalPlugin = () => ({
    name: 'replace-global',
    renderChunk(code) {
      if (typeof code !== 'string') return null
      if (!code.includes('global')) return null
      return code.replaceAll(/\bglobal\b/g, 'globalThis')
    },
  })

  return {
    plugins: [vue()],
    // Note: `define` removed to avoid passing invalid options to Rolldown during optimizeDeps
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      },
    },
    optimizeDeps: {
      rolldownOptions: {
        plugins: [replaceGlobalPlugin()],
      },
    },
    test: {
      environment: 'happy-dom',
      globals: true,
    },
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/ws': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          ws: true,
        },
      },
    },
  }
})
