import { useState } from 'react'
import useProjectStore from '../store/useProjectStore'

const inputStyle = {
  width: '100%',
  padding: '6px 8px',
  background: '#0f0f1a',
  border: '1px solid #2a2a4a',
  borderRadius: 4,
  color: '#fff',
  fontSize: 13,
}

const smallBtnStyle = {
  padding: '4px 8px',
  background: '#0f0f1a',
  color: '#fff',
  border: '1px solid #2a2a4a',
  borderRadius: 4,
  cursor: 'pointer',
  fontSize: 11,
}

function ProjectSettings({ onClose }) {
  const {
    project,
    setProjectName,
    addNodeType,
    removeNodeType,
    updateNodeType,
    addEdgeType,
    removeEdgeType,
    updateEdgeType,
  } = useProjectStore()

  const [newNodeLabel, setNewNodeLabel] = useState('')
  const [newNodeColor, setNewNodeColor] = useState('#6366f1')
  const [newEdgeLabel, setNewEdgeLabel] = useState('')
  const [newEdgeStyle, setNewEdgeStyle] = useState('solid')

  const handleAddNodeType = () => {
    if (!newNodeLabel.trim()) return
    const key = newNodeLabel.trim().toLowerCase().replace(/\s+/g, '_')
    addNodeType({ key, label: newNodeLabel.trim(), color: newNodeColor })
    setNewNodeLabel('')
    setNewNodeColor('#6366f1')
  }

  const handleAddEdgeType = () => {
    if (!newEdgeLabel.trim()) return
    const key = newEdgeLabel.trim().toLowerCase().replace(/\s+/g, '_')
    addEdgeType({ key, label: newEdgeLabel.trim(), style: newEdgeStyle })
    setNewEdgeLabel('')
    setNewEdgeStyle('solid')
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
        padding: 24,
        width: 480,
        maxHeight: '80vh',
        overflowY: 'auto',
        color: '#fff',
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
          <h2 style={{ fontSize: 18, margin: 0 }}>Project Settings</h2>
          <button
            onClick={onClose}
            style={{ background: 'none', border: 'none', color: '#aaa', cursor: 'pointer', fontSize: 18 }}
          >
            ✕
          </button>
        </div>

        {/* Project Name */}
        <label style={{ fontSize: 12, color: '#aaa' }}>
          Project Name
          <input
            type="text"
            value={project.name}
            onChange={(e) => setProjectName(e.target.value)}
            style={{ ...inputStyle, marginTop: 4, marginBottom: 20 }}
          />
        </label>

        {/* Node Types */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: 12, color: '#aaa', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 8 }}>
            Node Types
          </div>

          {project.nodeTypes.map((nt) => (
            <div key={nt.key} style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginBottom: 6,
            }}>
              <input
                type="color"
                value={nt.color}
                onChange={(e) => updateNodeType(nt.key, { color: e.target.value })}
                style={{ width: 32, height: 32, border: 'none', cursor: 'pointer', background: 'none' }}
              />
              <input
                type="text"
                value={nt.label}
                onChange={(e) => updateNodeType(nt.key, { label: e.target.value })}
                style={{ ...inputStyle, flex: 1 }}
              />
              <button
                onClick={() => removeNodeType(nt.key)}
                style={{ ...smallBtnStyle, borderColor: '#dc2626', color: '#dc2626' }}
              >
                ✕
              </button>
            </div>
          ))}

          <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
            <input
              type="color"
              value={newNodeColor}
              onChange={(e) => setNewNodeColor(e.target.value)}
              style={{ width: 32, height: 32, border: 'none', cursor: 'pointer', background: 'none' }}
            />
            <input
              type="text"
              value={newNodeLabel}
              onChange={(e) => setNewNodeLabel(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleAddNodeType()}
              placeholder="New node type..."
              style={{ ...inputStyle, flex: 1 }}
            />
            <button onClick={handleAddNodeType} style={{ ...smallBtnStyle, borderColor: '#22c55e', color: '#22c55e' }}>
              + Add
            </button>
          </div>
        </div>

        {/* Edge Types */}
        <div>
          <div style={{ fontSize: 12, color: '#aaa', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 8 }}>
            Edge Types
          </div>

          {project.edgeTypes.map((et) => (
            <div key={et.key} style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginBottom: 6,
            }}>
              <select
                value={et.style}
                onChange={(e) => updateEdgeType(et.key, { style: e.target.value })}
                style={{ ...inputStyle, width: 80, flex: 'none' }}
              >
                <option value="solid">Solid</option>
                <option value="dashed">Dashed</option>
                <option value="dotted">Dotted</option>
              </select>
              <input
                type="text"
                value={et.label}
                onChange={(e) => updateEdgeType(et.key, { label: e.target.value })}
                style={{ ...inputStyle, flex: 1 }}
              />
              <button
                onClick={() => removeEdgeType(et.key)}
                style={{ ...smallBtnStyle, borderColor: '#dc2626', color: '#dc2626' }}
              >
                ✕
              </button>
            </div>
          ))}

          <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
            <select
              value={newEdgeStyle}
              onChange={(e) => setNewEdgeStyle(e.target.value)}
              style={{ ...inputStyle, width: 80, flex: 'none' }}
            >
              <option value="solid">Solid</option>
              <option value="dashed">Dashed</option>
              <option value="dotted">Dotted</option>
            </select>
            <input
              type="text"
              value={newEdgeLabel}
              onChange={(e) => setNewEdgeLabel(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleAddEdgeType()}
              placeholder="New edge type..."
              style={{ ...inputStyle, flex: 1 }}
            />
            <button onClick={handleAddEdgeType} style={{ ...smallBtnStyle, borderColor: '#22c55e', color: '#22c55e' }}>
              + Add
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default ProjectSettings
