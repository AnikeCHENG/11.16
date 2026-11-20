'use strict'

const { contextBridge } = require('electron')

// 给渲染进程一个明确的桌面壳标记（apiBase.isNativeApp 也会再用 userAgent 兜底）。
contextBridge.exposeInMainWorld('campusDesktop', true)
