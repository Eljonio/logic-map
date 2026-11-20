import { X, Trash2 } from 'lucide-react'
import useMapStore from '../store/useMapStore'
import useProjectStore from '../store/useProjectStore'

function EdgeEditor() {
  const { edges, selectedEdge, updateEdgeData, deleteEdge, setSelectedEdge } = useMapStore()
  const { project } = useProjectStore()

  const edge = edges.find((e) => e.id === selectedEdge)
  if (!edge) return null

  const handleLabelChange = (value) => {
    updateEdgeData(edge.id, { label: value })
  }

  const handleTypeChange = (typeKey) => {
    const edgeType = project.edgeTypes.find((t) => t.key === typeKey)
    if (!edgeType) return
    updateEdgeData(edge.id, {
      data: { ...edge.data, type: typeKey },
      style: getEdgeStyle(edgeType.style),
    })
  }

  return (
    <div style={{
      position: 'absolute',
      bottom: 20,
      left: '50%',
      transform: 'translateX(-50%)',
      zIndex: 10,
      background: '#1a1a2e',
      border: '1px solid #2a2a4a',
      borderRadius: 10,
      padding: '10px 14px',
      color: '#fff',
      display: 'flex',
      alignItems: 'center',
      gap: 10,
    }}>
      <span style={{
        fontSize: 10,
        color: '#666',
        textTransform: 'uppercase',
        letterSpacing: 1,
        fontWeight: 600,
      }}>
        Edge
      </span>

      <input
        type="text"
        value={edge.label || ''}
        onChange={(e) => handleLabelChange(e.target.value)}
        placeholder="Label..."
        style={{
          padding: '6px 10px',
          background: '#0f0f1a',
          border: '1px solid #2a2a4a',
          borderRadius: 6,
          color: '#fff',
          fontSize: 13,
          width: 180,
          outline: 'none',
        }}
      />

      <select
        value={edge.data?.type || 'default'}
        onChange={(e) => handleTypeChange(e.target.value)}
        style={{
          padding: '6px 10px',
          background: '#0f0f1a',
          border: '1px solid #2a2a4a',
          borderRadius: 6,
          color: '#fff',
          fontSize: 13,
          outline: 'none',
        }}
      >
        {project.edgeTypes.map((et) => (
          <option key={et.key} value={et.key}>{et.label}</option>
        ))}
      </select>

      <button
        onClick={() => deleteEdge(edge.id)}
        style={{
          padding: '6px 10px',
          background: '#dc262618',
          color: '#dc2626',
          border: '1px solid #dc262640',
          borderRadius: 6,
          cursor: 'pointer',
          fontSize: 12,
          display: 'flex',
          alignItems: 'center',
          gap: 4,
        }}
      >
        <Trash2 size={12} />
        Delete
      </button>

      <button
        onClick={() => setSelectedEdge(null)}
        style={{
          background: 'none',
          border: 'none',
          color: '#555',
          cursor: 'pointer',
          padding: 4,
          display: 'flex',
        }}
      >
        <X size={14} />
      </button>
    </div>
  )
}

function getEdgeStyle(styleType) {
  switch (styleType) {
    case 'dashed':
      return { strokeDasharray: '8 4' }
    case 'dotted':
      return { strokeDasharray: '2 4' }
    default:
      return {}
  }
}

export default EdgeEditor
