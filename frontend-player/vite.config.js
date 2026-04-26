import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export default defineConfig(() => {
  const isTest = Boolean(process.env.VITEST)
  const plugins = isTest ? [vue()] : [vue(), vueDevTools()]

  return {
    plugins,
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    test: {
      environment: 'happy-dom',
      globals: true,
    },
    server: {
      port: 5174,
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
