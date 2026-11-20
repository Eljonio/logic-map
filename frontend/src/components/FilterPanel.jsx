import { useState, useMemo } from 'react'
import { Filter, X, Search } from 'lucide-react'
import useMapStore from '../store/useMapStore'
import SmartSearch from './SmartSearch'

function FilterPanel({ activeFilter, onFilterChange, showUnlinked, onToggleUnlinked, linkedCount, totalCount, searchQuery, onSearchChange, onSemanticResults }) {
  const { nodes, edges } = useMapStore()
  const [showPanel, setShowPanel] = useState(false)

  const modules = useMemo(() => {
    const mods = {}
    nodes.forEach((node) => {
      if (node.data?.nodeType === 'route') {
        const match = node.data.label.match(/(?:GET|POST|PUT|DELETE|PATCH)\s+\/([^/]+)/)
        if (match) {
          mods[match[1]] = (mods[match[1]] || 0) + 1
        }
      }
    })
    return Object.entries(mods).sort((a, b) => b[1] - a[1])
  }, [nodes])

  const typeCounts = useMemo(() => {
    const counts = {}
    nodes.forEach((node) => {
      const t = node.data?.nodeType || 'unknown'
      counts[t] = (counts[t] || 0) + 1
    })
    return Object.entries(counts).sort((a, b) => b[1] - a[1])
  }, [nodes])

  const typeColors = {
    route: '#22c55e',
    database: '#ef4444',
    event: '#f97316',
    cache: '#a855f7',
    http: '#3b82f6',
    file: '#f59e0b',
    env: '#06b6d4',
    middleware: '#ec4899',
  }

  if (nodes.length === 0) return null

  return (
    <>
      <button
        onClick={() => setShowPanel(!showPanel)}
        style={{
          position: 'absolute',
          top: 50,
          left: 12,
          zIndex: 11,
          background: activeFilter ? '#6366f122' : '#1a1a2e',
          border: `1px solid ${activeFilter ? '#6366f144' : '#2a2a4a'}`,
          borderRadius: 8,
          padding: '6px 10px',
          color: activeFilter ? '#6366f1' : '#888',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          fontSize: 12,
        }}
      >
        <Filter size={14} />
        Filter
      </button>

      {showPanel && (
        <div style={{
          position: 'absolute',
          top: 84,
          left: 12,
          zIndex: 10,
          background: '#1a1a2e',
          border: '1px solid #2a2a4a',
          borderRadius: 12,
          padding: 12,
          width: 200,
          maxHeight: 'calc(100vh - 100px)',
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
          gap: 4,
        }}>
          {/* Smart Search (semantic) */}
          <SmartSearch
            onResults={onSemanticResults}
            onClear={() => onSemanticResults?.([])}
          />

          {/* Text Search */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            background: '#0f0f1a',
            border: '1px solid #2a2a4a',
            borderRadius: 6,
            padding: '5px 8px',
            marginBottom: 4,
            marginTop: 4,
          }}>
            <Search size={12} color="#555" />
            <input
              type="text"
              value={searchQuery || ''}
              onChange={(e) => onSearchChange(e.target.value)}
              placeholder="Search nodes..."
              style={{
                background: 'none',
                border: 'none',
                outline: 'none',
                color: '#fff',
                fontSize: 12,
                width: '100%',
              }}
            />
            {searchQuery && (
              <button
                onClick={() => onSearchChange('')}
                style={{ background: 'none', border: 'none', color: '#555', cursor: 'pointer', padding: 0, display: 'flex' }}
              >
                <X size={12} />
              </button>
            )}
          </div>

          {/* Clear filter */}
          <button
            onClick={() => onFilterChange(null)}
            style={{
              ...btnStyle,
              color: !activeFilter ? '#6366f1' : '#888',
              background: !activeFilter ? '#6366f122' : 'transparent',
              borderColor: !activeFilter ? '#6366f144' : 'transparent',
              marginBottom: 4,
            }}
          >
            Show All ({nodes.length})
          </button>

          {/* Unlinked toggle */}
          <button
            onClick={onToggleUnlinked}
            style={{
              ...btnStyle,
              color: showUnlinked ? '#f59e0b' : '#555',
              background: showUnlinked ? '#f59e0b18' : 'transparent',
              borderColor: showUnlinked ? '#f59e0b44' : 'transparent',
              marginBottom: 4,
            }}
          >
            <span>{showUnlinked ? 'Hide' : 'Show'} unlinked</span>
            <span style={{ color: '#555', fontSize: 10 }}>{totalCount - linkedCount}</span>
          </button>

          {/* Divider */}
          <div style={{ fontSize: 10, color: '#555', textTransform: 'uppercase', letterSpacing: 1, padding: '4px 0' }}>
            Modules
          </div>

          {modules.map(([mod, count]) => (
            <button
              key={mod}
              onClick={() => onFilterChange(activeFilter === mod ? null : mod)}
              style={{
                ...btnStyle,
                color: activeFilter === mod ? '#6366f1' : '#ccc',
                background: activeFilter === mod ? '#6366f122' : 'transparent',
                borderColor: activeFilter === mod ? '#6366f144' : 'transparent',
              }}
            >
              <span>{mod}</span>
              <span style={{ color: '#555', fontSize: 10 }}>{count}</span>
            </button>
          ))}

          {/* Node types */}
          <div style={{ fontSize: 10, color: '#555', textTransform: 'uppercase', letterSpacing: 1, padding: '4px 0', marginTop: 4 }}>
            Types
          </div>

          {typeCounts.map(([type, count]) => (
            <button
              key={type}
              onClick={() => onFilterChange(activeFilter === `type:${type}` ? null : `type:${type}`)}
              style={{
                ...btnStyle,
                color: activeFilter === `type:${type}` ? (typeColors[type] || '#ccc') : '#aaa',
                background: activeFilter === `type:${type}` ? (typeColors[type] || '#666') + '18' : 'transparent',
                borderColor: activeFilter === `type:${type}` ? (typeColors[type] || '#666') + '44' : 'transparent',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <div style={{ width: 8, height: 8, borderRadius: '50%', background: typeColors[type] || '#666' }} />
                <span>{type}</span>
              </div>
              <span style={{ color: '#555', fontSize: 10 }}>{count}</span>
            </button>
          ))}
        </div>
      )}
    </>
  )
}

const btnStyle = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 6,
  padding: '6px 8px',
  border: '1px solid transparent',
  borderRadius: 6,
  cursor: 'pointer',
  fontSize: 12,
  textAlign: 'left',
  width: '100%',
  background: 'transparent',
}

export default FilterPanel
