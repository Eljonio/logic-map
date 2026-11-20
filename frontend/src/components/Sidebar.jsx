import { GripVertical } from 'lucide-react'
import useProjectStore from '../store/useProjectStore'

function Sidebar() {
  const { project } = useProjectStore()

  const onDragStart = (event, nodeType) => {
    event.dataTransfer.setData('application/logicmap-nodetype', JSON.stringify(nodeType))
    event.dataTransfer.effectAllowed = 'move'
  }

  return (
    <div style={{
      position: 'absolute',
      top: 56,
      left: 12,
      zIndex: 10,
      background: '#1a1a2e',
      border: '1px solid #2a2a4a',
      borderRadius: 12,
      padding: '14px 12px',
      display: 'flex',
      flexDirection: 'column',
      gap: 6,
      minWidth: 150,
    }}>
      <div style={{
        color: '#666',
        fontSize: 10,
        textTransform: 'uppercase',
        letterSpacing: 1.5,
        fontWeight: 600,
        paddingBottom: 6,
        borderBottom: '1px solid #2a2a4a',
        marginBottom: 2,
      }}>
        Drag to canvas
      </div>
      {project.nodeTypes.map((nt) => (
        <div
          key={nt.key}
          draggable
          onDragStart={(e) => onDragStart(e, nt)}
          style={{
            background: nt.color + '22',
            border: '1px solid ' + nt.color + '44',
            color: nt.color,
            padding: '8px 12px',
            borderRadius: 8,
            cursor: 'grab',
            fontSize: 13,
            fontWeight: 500,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            userSelect: 'none',
            transition: 'all 0.15s',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = nt.color + '33'
            e.currentTarget.style.borderColor = nt.color + '66'
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = nt.color + '22'
            e.currentTarget.style.borderColor = nt.color + '44'
          }}
        >
          <GripVertical size={14} style={{ opacity: 0.5 }} />
          <div style={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            background: nt.color,
            flexShrink: 0,
          }} />
          {nt.label}
        </div>
      ))}
    </div>
  )
}

export default Sidebar
