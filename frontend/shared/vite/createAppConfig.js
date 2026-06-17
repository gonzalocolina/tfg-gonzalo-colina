import { resolve } from 'node:path'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

function replaceGlobalPlugin() {
  return {
    name: 'replace-global',
    renderChunk(code) {
      if (typeof code !== 'string' || !code.includes('global')) {
        return null
      }

      return code.replaceAll(/\bglobal\b/g, 'globalThis')
    },
  }
}

function normalizeBasePath(basePath) {
  if (!basePath || basePath === '/') {
    return '/'
  }

  const withLeadingSlash = basePath.startsWith('/') ? basePath : `/${basePath}`
  return withLeadingSlash.endsWith('/') ? withLeadingSlash : `${withLeadingSlash}/`
}

function createProxyConfig() {
  return {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/ws': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      ws: true,
    },
  }
}

function getHmrPath() {
  return '/__vite_ws'
}

export function createAppConfig({ appRoot, appName, basePath, port }) {
  const normalizedBasePath = normalizeBasePath(process.env.FRONTEND_BASE_PATH || basePath)
  const publicPort = Number(process.env.FRONTEND_PUBLIC_PORT || 5175)
  const devHost = process.env.FRONTEND_DEV_HOST || '127.0.0.1'

  return defineConfig({
    root: appRoot,
    base: normalizedBasePath,
    plugins: [vue(), replaceGlobalPlugin()],
    define: {
      __FRONTEND_PUBLIC_PORT__: JSON.stringify(publicPort),
      global: 'globalThis',
    },
    resolve: {
      alias: {
        '@': resolve(appRoot, 'src'),
      },
    },
    optimizeDeps: {
      rolldownOptions: {
        transform: {
          define: {
            global: 'globalThis',
          },
        },
        plugins: [replaceGlobalPlugin()],
      },
    },
    test: {
      environment: 'happy-dom',
      globals: true,
      root: appRoot,
    },
    cacheDir: resolve(appRoot, '../../.vite', appName),
    build: {
      outDir: resolve(appRoot, '../../dist', appName),
      emptyOutDir: true,
    },
    server: {
      host: devHost,
      port,
      strictPort: true,
      proxy: createProxyConfig(),
      hmr: {
        clientPort: publicPort,
        path: getHmrPath(),
      },
    },
  })
}
