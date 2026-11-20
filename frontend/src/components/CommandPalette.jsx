import { useEffect, useState, useMemo } from 'react'
import { Command } from 'cmdk'
import {
  Search, LayoutGrid, Zap, Download, Upload, Trash2,
  Undo2, Redo2, Scan, Sun, Moon, Maximize,
} from 'lucide-react'
import useMapStore from '../store/useMapStore'
import { getNodeIcon } from '../utils/nodeIcons'

function CommandPalette({ open, onClose, onAction }) {
  const [search, setSearch] = useState('')
  const { nodes } = useMapStore()

  useEffect(() => {
    if (open) setSearch('')
  }, [open])

  // Filter nodes by search
  const matchedNodes = useMemo(() => {
    if (!search) return nodes.slice(0, 20)
    const q = search.toLowerCase()
    return nodes
      .filter((n) => {
        const label = (n.data?.label || '').toLowerCase()
        const desc = (n.data?.description || '').toLowerCase()
        return label.includes(q) || desc.includes(q)
      })
      .slice(0, 15)
  }, [nodes, search])

  if (!open) return null

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 100,
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'center',
        paddingTop: '15vh',
        background: 'rgba(0, 0, 0, 0.5)',
        backdropFilter: 'blur(4px)',
        animation: 'fadeIn 0.1s ease',
      }}
      onClick={onClose}
    >
      <div onClick={(e) => e.stopPropagation()}>
        <Command
          label="Command palette"
          style={{
            width: 520,
            background: 'var(--lm-bg-secondary, #1a1a2e)',
            border: '1px solid var(--lm-border, #2a2a4a)',
            borderRadius: 12,
            boxShadow: '0 16px 64px rgba(0, 0, 0, 0.5)',
            overflow: 'hidden',
            animation: 'slideUp 0.15s ease',
          }}
        >
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '12px 16px',
            borderBottom: '1px solid var(--lm-border, #2a2a4a)',
          }}>
            <Search size={16} color="var(--lm-text-muted, #888)" />
            <Command.Input
              value={search}
              onValueChange={setSearch}
              placeholder="Search nodes or type a command..."
              style={{
                flex: 1,
                background: 'transparent',
                border: 'none',
                outline: 'none',
                color: 'var(--lm-text-primary, #fff)',
                fontSize: 14,
                fontFamily: 'var(--lm-font-sans)',
              }}
            />
            <kbd style={{
              padding: '2px 6px',
              background: 'var(--lm-bg-tertiary, #252545)',
              border: '1px solid var(--lm-border, #2a2a4a)',
              borderRadius: 4,
              fontSize: 10,
              color: 'var(--lm-text-muted, #888)',
            }}>ESC</kbd>
          </div>

          <Command.List style={{
            maxHeight: 360,
            overflowY: 'auto',
            padding: 6,
          }}>
            <Command.Empty style={{
              padding: '24px 16px',
              textAlign: 'center',
              color: 'var(--lm-text-muted, #888)',
              fontSize: 13,
            }}>
              No results found
            </Command.Empty>

            {/* Nodes */}
            {matchedNodes.length > 0 && (
              <Command.Group heading={
                <span style={{ fontSize: 11, color: 'var(--lm-text-dim, #555)', padding: '4px 8px', display: 'block' }}>
                  Nodes ({matchedNodes.length})
                </span>
              }>
                {matchedNodes.map((node) => {
                  const Icon = getNodeIcon(node.data?.nodeType)
                  return (
                    <Command.Item
                      key={node.id}
                      value={node.data?.label}
                      onSelect={() => { onAction('focus-node', node.id); onClose() }}
                      style={itemStyle}
                    >
                      <Icon size={14} color={node.data?.color || '#6366f1'} />
                      <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {node.data?.label}
                      </span>
                      <span style={{ fontSize: 10, color: 'var(--lm-text-dim, #555)' }}>
                        {node.data?.nodeType}
                      </span>
                    </Command.Item>
                  )
                })}
              </Command.Group>
            )}

            {/* Commands */}
            <Command.Group heading={
              <span style={{ fontSize: 11, color: 'var(--lm-text-dim, #555)', padding: '4px 8px', display: 'block' }}>
                Commands
              </span>
            }>
              <PaletteItem icon={<LayoutGrid size={14} />} label="Layout: Dagre" shortcut="1" onSelect={() => { onAction('layout-dagre'); onClose() }} />
              <PaletteItem icon={<Zap size={14} />} label="Layout: Force" shortcut="2" onSelect={() => { onAction('layout-force'); onClose() }} />
              <PaletteItem icon={<Maximize size={14} />} label="Fit View" shortcut="F" onSelect={() => { onAction('fit-view'); onClose() }} />
              <PaletteItem icon={<Scan size={14} />} label="Scan Project" onSelect={() => { onAction('scan'); onClose() }} />
              <PaletteItem icon={<Download size={14} />} label="Export Map" onSelect={() => { onAction('export'); onClose() }} />
              <PaletteItem icon={<Upload size={14} />} label="Import Map" onSelect={() => { onAction('import'); onClose() }} />
              <PaletteItem icon={<Undo2 size={14} />} label="Undo" shortcut="Ctrl+Z" onSelect={() => { onAction('undo'); onClose() }} />
              <PaletteItem icon={<Redo2 size={14} />} label="Redo" shortcut="Ctrl+Y" onSelect={() => { onAction('redo'); onClose() }} />
              <PaletteItem icon={<Sun size={14} />} label="Toggle Theme" onSelect={() => { onAction('toggle-theme'); onClose() }} />
              <PaletteItem icon={<Trash2 size={14} color="var(--lm-danger, #dc2626)" />} label="Clear Map" onSelect={() => { onAction('clear'); onClose() }} />
            </Command.Group>
          </Command.List>
        </Command>
      </div>
    </div>
  )
}

const itemStyle = {
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  padding: '8px 10px',
  borderRadius: 6,
  cursor: 'pointer',
  fontSize: 13,
  color: 'var(--lm-text-secondary, #ccc)',
  transition: 'background 0.1s ease',
}

function PaletteItem({ icon, label, shortcut, onSelect }) {
  return (
    <Command.Item onSelect={onSelect} style={itemStyle} value={label}>
      {icon}
      <span style={{ flex: 1 }}>{label}</span>
      {shortcut && (
        <kbd style={{
          padding: '1px 5px',
          background: 'var(--lm-bg-tertiary, #252545)',
          border: '1px solid var(--lm-border, #2a2a4a)',
          borderRadius: 3,
          fontSize: 10,
          color: 'var(--lm-text-muted, #888)',
        }}>{shortcut}</kbd>
      )}
    </Command.Item>
  )
}

export default CommandPalette
