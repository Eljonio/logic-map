import { X, Trash2 } from 'lucide-react'
import useMapStore from '../store/useMapStore'

const inputStyle = {
  width: '100%',
  marginTop: 4,
  padding: '8px 10px',
  background: '#0f0f1a',
  border: '1px solid #2a2a4a',
  borderRadius: 6,
  color: '#fff',
  fontSize: 13,
  outline: 'none',
}

function NodeEditor() {
  const { nodes, selectedNode, updateNodeData, deleteNode, setSelectedNode } = useMapStore()

  const node = nodes.find((n) => n.id === selectedNode)
  if (!node) return null

  const handleChange = (field, value) => {
    updateNodeData(node.id, { [field]: value })
  }

  return (
    <div style={{
      position: 'absolute',
      top: 56,
      right: 12,
      zIndex: 10,
      background: '#1a1a2e',
      border: '1px solid #2a2a4a',
      borderRadius: 12,
      padding: 16,
      width: 260,
      color: '#fff',
      display: 'flex',
      flexDirection: 'column',
      gap: 12,
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{
            width: 10,
            height: 10,
            borderRadius: '50%',
            background: node.data.color || '#6366f1',
          }} />
          <span style={{
            fontSize: 11,
            textTransform: 'uppercase',
            letterSpacing: 1,
            color: '#666',
            fontWeight: 600,
          }}>
            Edit Node
          </span>
        </div>
        <button
          onClick={() => setSelectedNode(null)}
          style={{
            background: 'none',
            border: 'none',
            color: '#555',
            cursor: 'pointer',
            padding: 4,
            display: 'flex',
          }}
        >
          <X size={16} />
        </button>
      </div>

      <label style={{ fontSize: 12, color: '#888' }}>
        Label
        <input
          type="text"
          value={node.data.label}
          onChange={(e) => handleChange('label', e.target.value)}
          style={inputStyle}
        />
      </label>

      <label style={{ fontSize: 12, color: '#888' }}>
        Description
        <textarea
          value={node.data.description || ''}
          onChange={(e) => handleChange('description', e.target.value)}
          rows={3}
          style={{ ...inputStyle, resize: 'vertical' }}
        />
      </label>

      <button
        onClick={() => deleteNode(node.id)}
        style={{
          marginTop: 4,
          padding: '8px 12px',
          background: '#dc262618',
          color: '#dc2626',
          border: '1px solid #dc262640',
          borderRadius: 8,
          cursor: 'pointer',
          fontSize: 13,
          fontWeight: 500,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 6,
          transition: 'all 0.15s',
        }}
        onMouseEnter={(e) => e.currentTarget.style.background = '#dc262630'}
        onMouseLeave={(e) => e.currentTarget.style.background = '#dc262618'}
      >
        <Trash2 size={14} />
        Delete Node
      </button>
    </div>
  )
}

export default NodeEditor
