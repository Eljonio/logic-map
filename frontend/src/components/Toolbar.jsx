import { useRef } from 'react'
import { Scan, Download, Upload, Settings, Trash2, LayoutGrid, Undo2, Redo2, Zap } from 'lucide-react'
import useMapStore from '../store/useMapStore'

const btnBase = {
  padding: '7px 12px',
  background: 'var(--lm-bg-secondary, #1a1a2e)',
  color: 'var(--lm-text-secondary, #ccc)',
  border: '1px solid var(--lm-border, #2a2a4a)',
  borderRadius: 8,
  cursor: 'pointer',
  fontSize: 12,
  fontWeight: 500,
  display: 'flex',
  alignItems: 'center',
  gap: 6,
  transition: 'all var(--lm-transition, 0.15s ease)',
}

function Toolbar({ onOpenSettings, onOpenScan, layoutMode, onLayoutDagre, onLayoutForce }) {
  const { exportMap, importMap, clearMap, undo, redo, canUndo, canRedo } = useMapStore()
  const fileInputRef = useRef(null)

  const handleExport = () => {
    const data = exportMap()
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'logic-map.json'
    a.click()
    URL.revokeObjectURL(url)
  }

  const handleImport = (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const data = JSON.parse(e.target.result)
        importMap(data)
      } catch { /* invalid json */ }
    }
    reader.readAsText(file)
    event.target.value = ''
  }

  return (
    <div style={{
      position: 'absolute',
      top: 12,
      right: 12,
      zIndex: 10,
      display: 'flex',
      gap: 4,
      alignItems: 'center',
      animation: 'slideUp 0.3s ease',
    }}>
      <ToolbarBtn icon={<Undo2 size={14} />} onClick={undo} disabled={!canUndo()} tooltip="Undo (Ctrl+Z)" />
      <ToolbarBtn icon={<Redo2 size={14} />} onClick={redo} disabled={!canRedo()} tooltip="Redo (Ctrl+Y)" />
      <Divider />

      {/* Layout switcher */}
      <ToolbarBtn
        icon={<LayoutGrid size={14} />}
        label="Dagre"
        onClick={onLayoutDagre}
        active={layoutMode === 'dagre'}
        accent="#8b5cf6"
        tooltip="Hierarchical layout (1)"
      />
      <ToolbarBtn
        icon={<Zap size={14} />}
        label="Force"
        onClick={onLayoutForce}
        active={layoutMode === 'force'}
        accent="#f59e0b"
        tooltip="Force-directed layout (2)"
      />
      <Divider />

      <ToolbarBtn icon={<Scan size={14} />} label="Scan" onClick={onOpenScan} accent="#22c55e" tooltip="Scan project" />
      <ToolbarBtn icon={<Download size={14} />} label="Export" onClick={handleExport} tooltip="Export JSON" />
      <ToolbarBtn icon={<Upload size={14} />} label="Import" onClick={() => fileInputRef.current?.click()} tooltip="Import JSON" />
      <ToolbarBtn icon={<Settings size={14} />} label="Settings" onClick={onOpenSettings} tooltip="Project settings" />
      <ToolbarBtn icon={<Trash2 size={14} />} label="Clear" onClick={clearMap} accent="#dc2626" tooltip="Clear all" />

      <input
        ref={fileInputRef}
        type="file"
        accept=".json"
        onChange={handleImport}
        style={{ display: 'none' }}
      />
    </div>
  )
}

function Divider() {
  return <div style={{ width: 1, height: 20, background: 'var(--lm-border, #2a2a4a)' }} />
}

function ToolbarBtn({ icon, label, onClick, accent, disabled, active, tooltip }) {
  const accentBg = accent ? accent + '18' : undefined
  const accentBorder = accent ? accent + '40' : undefined

  return (
    <button
      title={tooltip}
      style={{
        ...btnBase,
        ...(accent && { background: accentBg, borderColor: accentBorder, color: accent }),
        ...(active && {
          background: (accent || 'var(--lm-accent)') + '30',
          borderColor: (accent || 'var(--lm-accent)') + '60',
          boxShadow: `0 0 8px ${accent || 'var(--lm-accent)'}30`,
        }),
        ...(disabled && { opacity: 0.3, pointerEvents: 'none' }),
      }}
      onClick={onClick}
      disabled={disabled}
      onMouseEnter={(e) => {
        if (disabled) return
        e.currentTarget.style.background = accent ? accent + '30' : 'var(--lm-bg-tertiary, #252545)'
        e.currentTarget.style.color = accent || 'var(--lm-text-primary, #fff)'
        e.currentTarget.style.transform = 'translateY(-1px)'
      }}
      onMouseLeave={(e) => {
        if (disabled) return
        e.currentTarget.style.background = active
          ? (accent || 'var(--lm-accent)') + '30'
          : (accent ? accentBg : 'var(--lm-bg-secondary, #1a1a2e)')
        e.currentTarget.style.color = accent || 'var(--lm-text-secondary, #ccc)'
        e.currentTarget.style.transform = 'translateY(0)'
      }}
    >
      {icon}
      {label}
    </button>
  )
}

export default Toolbar
