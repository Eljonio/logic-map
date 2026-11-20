// Starts the MCP server over stdio, does the handshake and checks the tool list. No backend needed.
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const EXPECTED = 18
const server = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src', 'index.js')
const child = spawn(process.execPath, [server], { stdio: ['pipe', 'pipe', 'inherit'] })
const send = (m) => child.stdin.write(JSON.stringify(m) + '\n')
const finish = (code, text) => { clearTimeout(timer); child.kill(); (code ? console.error : console.log)(text); process.exit(code) }
const timer = setTimeout(() => finish(1, 'timeout: no answer from the server in 15 s'), 15000)

let buf = ''
child.stdout.on('data', (d) => {
  buf += d
  for (let i = buf.indexOf('\n'); i >= 0; i = buf.indexOf('\n')) {
    const line = buf.slice(0, i).trim()
    buf = buf.slice(i + 1)
    if (!line) continue
    const msg = JSON.parse(line)
    if (msg.id === 1) {
      send({ jsonrpc: '2.0', method: 'notifications/initialized' })
      send({ jsonrpc: '2.0', id: 2, method: 'tools/list' })
    }
    if (msg.id === 2) {
      const tools = msg.result.tools
      const bad = tools.filter((t) => !t.name.startsWith('logic_map_') || !t.description || !t.inputSchema)
      if (tools.length !== EXPECTED || bad.length) finish(1, `unexpected tool list: ${tools.map((t) => t.name).join(', ')}`)
      finish(0, `ok: ${tools.length} tools, each with a description and an input schema`)
    }
  }
})
child.on('exit', (code) => { if (code) finish(1, `server exited with code ${code}`) })
send({ jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'tools-list-test', version: '0' } } })
