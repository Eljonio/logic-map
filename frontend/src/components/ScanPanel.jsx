import { useState, useEffect } from 'react'
import { Scan, Play, X, FolderSearch, History, Loader } from 'lucide-react'
import useMapStore from '../store/useMapStore'
import { applyDagreLayout } from '../utils/layout'

function ScanPanel({ onClose }) {
  const { importMap } = useMapStore()
  const [scanPath, setScanPath] = useState('')
  const [scanning, setScanning] = useState(false)
  const [projects, setProjects] = useState([])
  const [history, setHistory] = useState([])
  const [discovering, setDiscovering] = useState(false)
  const [tab, setTab] = useState('discover') // 'discover' | 'history'

  useEffect(() => {
    // Load history
    fetch('http://localhost:8080/api/projects/history')
      .then((r) => r.json())
      .then(setHistory)
      .catch(() => {})

    // Auto-discover projects
    handleDiscover()
  }, [])

  const handleDiscover = async () => {
    setDiscovering(true)
    try {
      const res = await fetch('http://localhost:8080/api/projects/discover')
      const data = await res.json()
      setProjects(data)
    } catch {
      // offline
    } finally {
      setDiscovering(false)
    }
  }

  const handleScan = async (path) => {
    if (!path.trim()) return
    setScanning(true)
    setScanPath(path)
    try {
      const res = await fetch('http://localhost:8080/api/scan', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          projectPath: path.trim(),
          fileExtensions: ['.js', '.ts'],
        }),
      })
      const data = await res.json()
      if (data.error) {
        alert('Scan error: ' + data.error)
      } else {
        // Save to history
        const name = path.split('/').filter(Boolean).pop() || path
        fetch('http://localhost:8080/api/projects/history', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ path: path.trim(), name }),
        }).catch(() => {})

        importMap(data, applyDagreLayout)
        onClose()
      }
    } catch {
      alert('Backend is offline')
    } finally {
      setScanning(false)
    }
  }

  const typeColors = {
    node: '#22c55e',
    'java-maven': '#ef4444',
    'java-gradle': '#ef4444',
    python: '#3b82f6',
    go: '#06b6d4',
    rust: '#f97316',
    php: '#8b5cf6',
    ruby: '#dc2626',
    flutter: '#06b6d4',
  }

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      zIndex: 100,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'rgba(0,0,0,0.6)',
    }}>
      <div style={{
        background: '#1a1a2e',
        border: '1px solid #2a2a4a',
        borderRadius: 16,
        padding: 20,
        width: 520,
        maxHeight: '70vh',
        display: 'flex',
        flexDirection: 'column',
        color: '#fff',
      }}>
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Scan size={18} color="#22c55e" />
            <h2 style={{ fontSize: 16, margin: 0 }}>Scan Project</h2>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: '#555', cursor: 'pointer' }}>
            <X size={18} />
          </button>
        </div>

        {/* Manual input */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
          <input
            type="text"
            value={scanPath}
            onChange={(e) => setScanPath(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleScan(scanPath)}
            placeholder="/absolute/path/to/project"
            style={{
              flex: 1,
              padding: '8px 12px',
              background: '#0f0f1a',
              border: '1px solid #2a2a4a',
              borderRadius: 8,
              color: '#fff',
              fontSize: 13,
              outline: 'none',
            }}
            autoFocus
          />
          <button
            onClick={() => handleScan(scanPath)}
            disabled={scanning || !scanPath.trim()}
            style={{
              padding: '8px 14px',
              background: '#22c55e22',
              border: '1px solid #22c55e44',
              borderRadius: 8,
              color: '#22c55e',
              cursor: 'pointer',
              fontSize: 13,
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              opacity: scanning ? 0.5 : 1,
            }}
          >
            {scanning ? <Loader size={14} className="spin" /> : <Play size={14} />}
            {scanning ? 'Scanning...' : 'Scan'}
          </button>
        </div>

        {/* Tabs */}
        <div style={{ display: 'flex', gap: 4, marginBottom: 12 }}>
          <TabBtn active={tab === 'discover'} onClick={() => setTab('discover')}
            icon={<FolderSearch size={13} />} label={`Projects (${projects.length})`} />
          <TabBtn active={tab === 'history'} onClick={() => setTab('history')}
            icon={<History size={13} />} label={`History (${history.length})`} />
        </div>

        {/* Content */}
        <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 4 }}>
          {tab === 'discover' && (
            <>
              {discovering && (
                <div style={{ color: '#555', fontSize: 12, padding: 20, textAlign: 'center' }}>
                  Searching for projects...
                </div>
              )}
              {!discovering && projects.length === 0 && (
                <div style={{ color: '#555', fontSize: 12, padding: 20, textAlign: 'center' }}>
                  No projects found
                </div>
              )}
              {projects.map((p, i) => (
                <button
                  key={i}
                  onClick={() => handleScan(p.path)}
                  disabled={scanning}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '8px 12px',
                    background: '#0f0f1a',
                    border: '1px solid #2a2a4a',
                    borderRadius: 8,
                    cursor: 'pointer',
                    textAlign: 'left',
                    width: '100%',
                    transition: 'border-color 0.15s',
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.borderColor = '#6366f1'}
                  onMouseLeave={(e) => e.currentTarget.style.borderColor = '#2a2a4a'}
                >
                  <div style={{
                    padding: '2px 6px',
                    borderRadius: 4,
                    fontSize: 10,
                    fontWeight: 600,
                    background: (typeColors[p.type] || '#555') + '22',
                    color: typeColors[p.type] || '#555',
                    textTransform: 'uppercase',
                    flexShrink: 0,
                  }}>
                    {p.type}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ color: '#fff', fontSize: 13, fontWeight: 500 }}>{p.name}</div>
                    <div style={{ color: '#555', fontSize: 11, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {p.path}
                    </div>
                  </div>
                </button>
              ))}
            </>
          )}

          {tab === 'history' && (
            <>
              {history.length === 0 && (
                <div style={{ color: '#555', fontSize: 12, padding: 20, textAlign: 'center' }}>
                  No scan history yet
                </div>
              )}
              {history.map((h, i) => (
                <button
                  key={i}
                  onClick={() => handleScan(h.path)}
                  disabled={scanning}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '8px 12px',
                    background: '#0f0f1a',
                    border: '1px solid #2a2a4a',
                    borderRadius: 8,
                    cursor: 'pointer',
                    textAlign: 'left',
                    width: '100%',
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.borderColor = '#6366f1'}
                  onMouseLeave={(e) => e.currentTarget.style.borderColor = '#2a2a4a'}
                >
                  <History size={14} color="#555" />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ color: '#fff', fontSize: 13 }}>{h.name}</div>
                    <div style={{ color: '#555', fontSize: 11, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {h.path}
                    </div>
                  </div>
                  <div style={{ color: '#444', fontSize: 10 }}>
                    {new Date(h.timestamp).toLocaleDateString()}
                  </div>
                </button>
              ))}
            </>
          )}
        </div>
      </div>
    </div>
  )
}

function TabBtn({ active, onClick, icon, label }) {
  return (
    <button
      onClick={onClick}
      style={{
        padding: '6px 12px',
        background: active ? '#6366f122' : 'transparent',
        border: `1px solid ${active ? '#6366f144' : '#2a2a4a'}`,
        borderRadius: 6,
        color: active ? '#6366f1' : '#888',
        cursor: 'pointer',
        fontSize: 12,
        display: 'flex',
        alignItems: 'center',
        gap: 6,
      }}
    >
      {icon}
      {label}
    </button>
  )
}

export default ScanPanel
