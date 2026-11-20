import { MousePointerClick, ArrowRight, Scan, GripVertical } from 'lucide-react'

function Welcome() {
  return (
    <div style={{
      position: 'absolute',
      inset: 0,
      zIndex: 5,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      pointerEvents: 'none',
    }}>
      <div style={{
        background: 'rgba(15, 15, 26, 0.9)',
        border: '1px solid #2a2a4a',
        borderRadius: 16,
        padding: '40px 48px',
        maxWidth: 420,
        textAlign: 'center',
      }}>
        <div style={{
          width: 56,
          height: 56,
          borderRadius: 12,
          background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          margin: '0 auto 20px',
        }}>
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2">
            <circle cx="6" cy="6" r="3" />
            <circle cx="18" cy="6" r="3" />
            <circle cx="6" cy="18" r="3" />
            <circle cx="18" cy="18" r="3" />
            <line x1="9" y1="6" x2="15" y2="6" />
            <line x1="6" y1="9" x2="6" y2="15" />
            <line x1="18" y1="9" x2="18" y2="15" />
            <line x1="9" y1="18" x2="15" y2="18" />
          </svg>
        </div>

        <h2 style={{ color: '#fff', fontSize: 20, fontWeight: 600, marginBottom: 8 }}>
          Logic Map
        </h2>
        <p style={{ color: '#666', fontSize: 13, marginBottom: 28, lineHeight: 1.5 }}>
          Visual logic &amp; architecture mapping tool
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14, textAlign: 'left' }}>
          <Step
            icon={<GripVertical size={16} />}
            text="Drag nodes from sidebar to canvas"
          />
          <Step
            icon={<MousePointerClick size={16} />}
            text="Connect nodes by dragging between dots"
          />
          <Step
            icon={<Scan size={16} />}
            text="Or scan a project to auto-generate map"
          />
        </div>
      </div>
    </div>
  )
}

function Step({ icon, text }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
      <div style={{
        width: 32,
        height: 32,
        borderRadius: 8,
        background: '#1a1a2e',
        border: '1px solid #2a2a4a',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: '#6366f1',
        flexShrink: 0,
      }}>
        {icon}
      </div>
      <span style={{ color: '#aaa', fontSize: 13 }}>{text}</span>
    </div>
  )
}

export default Welcome
