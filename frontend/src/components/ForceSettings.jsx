import { useState } from 'react'
import { Settings2, ChevronDown, ChevronUp } from 'lucide-react'

const sliderStyle = {
  width: '100%',
  height: 4,
  appearance: 'none',
  background: 'var(--lm-bg-tertiary, #252545)',
  borderRadius: 2,
  outline: 'none',
  cursor: 'pointer',
  accentColor: 'var(--lm-accent, #6366f1)',
}

function ForceSettings({ settings, onChange }) {
  const [collapsed, setCollapsed] = useState(true)

  return (
    <div style={{
      position: 'absolute',
      bottom: 12,
      right: 12,
      zIndex: 10,
      background: 'var(--lm-bg-secondary, #1a1a2e)',
      border: '1px solid var(--lm-border, #2a2a4a)',
      borderRadius: 'var(--lm-radius-md, 10px)',
      boxShadow: 'var(--lm-shadow-md)',
      width: collapsed ? 'auto' : 220,
      overflow: 'hidden',
      transition: 'width 0.2s ease',
    }}>
      <button
        onClick={() => setCollapsed((c) => !c)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          width: '100%',
          padding: '8px 12px',
          background: 'transparent',
          border: 'none',
          color: 'var(--lm-text-secondary, #ccc)',
          fontSize: 11,
          fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        <Settings2 size={13} />
        <span>Graph Forces</span>
        {collapsed ? <ChevronUp size={12} style={{ marginLeft: 'auto' }} /> : <ChevronDown size={12} style={{ marginLeft: 'auto' }} />}
      </button>

      {!collapsed && (
        <div style={{ padding: '4px 12px 12px', display: 'flex', flexDirection: 'column', gap: 10 }}>
          <SliderRow
            label="Repulsion"
            value={settings.charge}
            min={-2000}
            max={-100}
            step={50}
            onChange={(v) => onChange({ ...settings, charge: v })}
            display={Math.abs(settings.charge)}
          />
          <SliderRow
            label="Distance"
            value={settings.distance}
            min={50}
            max={500}
            step={10}
            onChange={(v) => onChange({ ...settings, distance: v })}
            display={settings.distance}
          />
          <SliderRow
            label="Center pull"
            value={settings.center}
            min={0}
            max={0.2}
            step={0.01}
            onChange={(v) => onChange({ ...settings, center: v })}
            display={settings.center.toFixed(2)}
          />
          <SliderRow
            label="Link strength"
            value={settings.linkStrength}
            min={0.05}
            max={1}
            step={0.05}
            onChange={(v) => onChange({ ...settings, linkStrength: v })}
            display={settings.linkStrength.toFixed(2)}
          />
          <SliderRow
            label="Label zoom"
            value={settings.labelZoom}
            min={0.3}
            max={3}
            step={0.1}
            onChange={(v) => onChange({ ...settings, labelZoom: v })}
            display={settings.labelZoom.toFixed(1)}
          />
        </div>
      )}
    </div>
  )
}

function SliderRow({ label, value, min, max, step, onChange, display }) {
  return (
    <div>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        fontSize: 10,
        color: 'var(--lm-text-muted, #888)',
        marginBottom: 3,
      }}>
        <span>{label}</span>
        <span style={{ color: 'var(--lm-text-secondary, #ccc)', fontFamily: 'var(--lm-font-mono)' }}>{display}</span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))}
        style={sliderStyle}
      />
    </div>
  )
}

export default ForceSettings
