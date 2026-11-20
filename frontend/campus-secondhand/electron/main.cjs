'use strict'

const { app, BrowserWindow, Menu, shell } = require('electron')
const fs = require('fs')
const http = require('http')
const path = require('path')

const isDev = process.env.ELECTRON_DEV === '1' || process.argv.includes('--dev')
const DEV_URL = process.env.VITE_DEV_SERVER_URL || 'http://127.0.0.1:5173'

const MIME = {
  '.css': 'text/css; charset=utf-8',
  '.gif': 'image/gif',
  '.htm': 'text/html; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.jpeg': 'image/jpeg',
  '.jpg': 'image/jpeg',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.map': 'application/json',
  '.mjs': 'text/javascript; charset=utf-8',
  '.mp4': 'video/mp4',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain; charset=utf-8',
  '.ttf': 'font/ttf',
  '.webm': 'video/webm',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
}

let mainWindow = null
let staticServer = null

function distDir() {
  return path.join(__dirname, '..', 'dist')
}

function iconPath() {
  const png = path.join(__dirname, 'icon.png')
  return fs.existsSync(png) ? png : undefined
}

/** 把请求路径限制在 dist 目录内，防止路径穿越。 */
function safePath(root, urlPath) {
  let pathname
  try {
    pathname = decodeURIComponent((urlPath || '/').split('?')[0].split('#')[0])
  } catch {
    return null
  }
  const rel = pathname === '/' ? 'index.html' : pathname.replace(/^\/+/, '')
  const abs = path.normalize(path.join(root, rel))
  const rootWithSep = path.normalize(root + path.sep)
  if (abs !== path.normalize(root) && !abs.startsWith(rootWithSep)) return null
  return abs
}

function startStaticServer(root) {
  return new Promise((resolve, reject) => {
    if (!fs.existsSync(path.join(root, 'index.html'))) {
      reject(new Error(`找不到前端构建产物：${path.join(root, 'index.html')}。请先执行 vite build --mode desktop。`))
      return
    }
    const server = http.createServer((req, res) => {
      if (req.method !== 'GET' && req.method !== 'HEAD') {
        res.writeHead(405, { Allow: 'GET, HEAD' })
        res.end()
        return
      }
      const file = safePath(root, req.url)
      if (!file) {
        res.writeHead(403)
        res.end()
        return
      }
      const pathname = (req.url || '/').split('?')[0]
      const ext = path.extname(pathname)
      const trySend = (fp, spaFallback) => {
        fs.stat(fp, (err, st) => {
          if (!err && st.isDirectory()) {
            trySend(path.join(fp, 'index.html'), spaFallback)
            return
          }
          if (err || !st.isFile()) {
            if (spaFallback) {
              trySend(path.join(root, 'index.html'), false)
              return
            }
            res.writeHead(404)
            res.end('Not found')
            return
          }
          const type = MIME[path.extname(fp).toLowerCase()] || 'application/octet-stream'
          res.writeHead(200, {
            'Content-Type': type,
            'Content-Length': st.size,
            'Cache-Control': path.extname(fp) === '.html' ? 'no-cache' : 'public, max-age=31536000, immutable',
          })
          if (req.method === 'HEAD') {
            res.end()
            return
          }
          fs.createReadStream(fp).pipe(res)
        })
      }
      // 没有扩展名的路径走 Vue history 回退；缺文件的静态资源返回 404。
      trySend(file, !ext || pathname.endsWith('/'))
    })
    server.on('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address()
      resolve({ server, url: `http://127.0.0.1:${port}` })
    })
  })
}

function isAppOrigin(url, appOrigin) {
  try {
    const u = new URL(url)
    const a = new URL(appOrigin)
    return u.origin === a.origin
  } catch {
    return false
  }
}

function attachExternalLinkHandlers(win, appOrigin) {
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (!isAppOrigin(url, appOrigin)) {
      shell.openExternal(url)
    }
    return { action: 'deny' }
  })
  win.webContents.on('will-navigate', (event, url) => {
    if (!isAppOrigin(url, appOrigin)) {
      event.preventDefault()
      shell.openExternal(url)
    }
  })
}

async function createMainWindow() {
  let appOrigin
  if (isDev) {
    appOrigin = DEV_URL
  } else {
    const started = await startStaticServer(distDir())
    staticServer = started.server
    appOrigin = started.url
  }

  mainWindow = new BrowserWindow({
    title: '校园二手交易平台',
    width: 1280,
    height: 800,
    minWidth: 1024,
    minHeight: 700,
    icon: iconPath(),
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      devTools: isDev,
    },
  })

  if (!isDev) {
    Menu.setApplicationMenu(null)
  }

  attachExternalLinkHandlers(mainWindow, appOrigin)

  mainWindow.once('ready-to-show', () => {
    mainWindow.setTitle('校园二手交易平台')
    mainWindow.show()
  })
  mainWindow.on('closed', () => {
    mainWindow = null
  })

  await mainWindow.loadURL(appOrigin)
  return mainWindow
}

function stopStaticServer() {
  if (staticServer) {
    staticServer.close()
    staticServer = null
  }
}

const gotLock = app.requestSingleInstanceLock()
if (!gotLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return
    if (mainWindow.isMinimized()) mainWindow.restore()
    mainWindow.show()
    mainWindow.focus()
  })
  app.whenReady().then(createMainWindow).catch((err) => {
    console.error(err)
    app.quit()
  })
  app.on('window-all-closed', () => {
    stopStaticServer()
    app.quit()
  })
  app.on('before-quit', stopStaticServer)
}
