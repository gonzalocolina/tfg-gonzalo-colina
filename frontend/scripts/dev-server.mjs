import { spawn } from 'node:child_process'
import { createServer } from 'node:http'
import { dirname, resolve } from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

import httpProxy from 'http-proxy'

const currentDir = dirname(fileURLToPath(import.meta.url))
const frontendRoot = resolve(currentDir, '..')
const viteBin = resolve(frontendRoot, 'node_modules', 'vite', 'bin', 'vite.js')
const publicPort = Number(process.env.FRONTEND_PUBLIC_PORT || 5175)
const frontendDevHost = process.env.FRONTEND_DEV_HOST || '127.0.0.1'
const backendTarget = process.env.BACKEND_TARGET || 'http://127.0.0.1:8080'

const services = [
  {
    name: 'portal',
    port: 5176,
    configPath: resolve(frontendRoot, 'apps', 'portal', 'vite.config.js'),
    basePath: '/',
    wsPath: '/__vite_ws',
  },
  {
    name: 'organizer',
    port: 5173,
    configPath: resolve(frontendRoot, 'apps', 'organizer', 'vite.config.js'),
    basePath: '/organizer',
    wsPath: '/organizer/__vite_ws',
  },
  {
    name: 'player',
    port: 5174,
    configPath: resolve(frontendRoot, 'apps', 'player', 'vite.config.js'),
    basePath: '/player',
    wsPath: '/player/__vite_ws',
  },
]

const childProcesses = []
let shuttingDown = false

function isHttpResponse(stream) {
  return Boolean(stream && typeof stream.writeHead === 'function' && typeof stream.end === 'function')
}

function isWritableSocket(stream) {
  return Boolean(stream && typeof stream.write === 'function' && typeof stream.end === 'function')
}

function sendHttpProxyError(response, message) {
  if (!response.headersSent) {
    response.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
  }

  if (!response.writableEnded) {
    response.end(message)
  }
}

function sendSocketProxyError(socket, message) {
  if (socket.destroyed || socket.writableEnded) {
    return
  }

  socket.write(
    [
      'HTTP/1.1 502 Bad Gateway',
      'Content-Type: text/plain; charset=utf-8',
      'Connection: close',
      '',
      message,
    ].join('\r\n'),
  )
  socket.end()
}

function prefixOutput(name, data, writer = console.log) {
  const text = data.toString()
  for (const line of text.split(/\r?\n/)) {
    if (line.trim()) {
      writer(`[${name}] ${line}`)
    }
  }
}

function killChildren() {
  for (const child of childProcesses) {
    if (!child.killed) {
      child.kill('SIGTERM')
    }
  }
}

function shutdown(exitCode = 0) {
  if (shuttingDown) {
    return
  }

  shuttingDown = true
  killChildren()
  server.close(() => {
    process.exit(exitCode)
  })

  setTimeout(() => process.exit(exitCode), 2000).unref()
}

function startViteService(service) {
  const child = spawn(process.execPath, [viteBin, '--config', service.configPath, '--clearScreen', 'false'], {
    cwd: frontendRoot,
    env: {
      ...process.env,
      FRONTEND_PUBLIC_PORT: String(publicPort),
      FRONTEND_DEV_HOST: frontendDevHost,
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  })

  child.stdout.on('data', (data) => prefixOutput(service.name, data))
  child.stderr.on('data', (data) => prefixOutput(service.name, data, console.error))
  child.on('exit', (code, signal) => {
    if (shuttingDown) {
      return
    }

    const suffix = signal ? ` (signal ${signal})` : ''
    console.error(`[${service.name}] finalizó con código ${code ?? 'desconocido'}${suffix}`)
    shutdown(code ?? 1)
  })

  childProcesses.push(child)
}

const proxies = {
  backend: httpProxy.createProxyServer({
    target: backendTarget,
    changeOrigin: true,
    ws: true,
  }),
  portal: httpProxy.createProxyServer({
    target: `http://${frontendDevHost}:5176`,
    changeOrigin: true,
    ws: true,
  }),
  organizer: httpProxy.createProxyServer({
    target: `http://${frontendDevHost}:5173`,
    changeOrigin: true,
    ws: true,
  }),
  player: httpProxy.createProxyServer({
    target: `http://${frontendDevHost}:5174`,
    changeOrigin: true,
    ws: true,
  }),
}

for (const [name, proxy] of Object.entries(proxies)) {
  proxy.on('error', (error, req, res) => {
    const message = `Proxy ${name} no disponible: ${error.message}`

    if (isHttpResponse(res)) {
      sendHttpProxyError(res, message)
      return
    }

    if (isWritableSocket(res)) {
      sendSocketProxyError(res, message)
      console.error(message)
      return
    }

    console.error(message)
  })
}

function selectHttpProxy(url = '/') {
  if (url.startsWith('/api') || url.startsWith('/ws')) {
    return proxies.backend
  }

  if (url === '/organizer' || url.startsWith('/organizer/')) {
    return proxies.organizer
  }

  if (url === '/player' || url.startsWith('/player/')) {
    return proxies.player
  }

  return proxies.portal
}

function selectWsProxy(url = '') {
  if (url.startsWith('/ws')) {
    return proxies.backend
  }

  if (url.startsWith('/organizer/__vite_ws')) {
    return proxies.organizer
  }

  if (url.startsWith('/player/__vite_ws')) {
    return proxies.player
  }

  if (url.startsWith('/__vite_ws')) {
    return proxies.portal
  }

  return null
}

const server = createServer((req, res) => {
  const proxy = selectHttpProxy(req.url || '/')
  proxy.web(req, res)
})

server.on('upgrade', (req, socket, head) => {
  const proxy = selectWsProxy(req.url || '')
  if (!proxy) {
    socket.destroy()
    return
  }

  proxy.ws(req, socket, head)
})

process.on('SIGINT', () => shutdown(0))
process.on('SIGTERM', () => shutdown(0))

for (const service of services) {
  startViteService(service)
}

server.listen(publicPort, () => {
  console.log('Frontend unificado listo en:')
  console.log(`  Portal:     http://localhost:${publicPort}/`)
  console.log(`  Organizer:  http://localhost:${publicPort}/organizer/login`)
  console.log(`  Player:     http://localhost:${publicPort}/player/join`)
  console.log(`  Backend:    ${backendTarget}`)
})
