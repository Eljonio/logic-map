import { useCallback, useEffect, useState, useRef, useMemo } from 'react'
import ForceGraph2D from 'react-force-graph-2d'
import './styles/theme.css'
import './styles/animations.css'
import './App.css'

import Sidebar from './components/Sidebar'
import NodeEditor from './components/NodeEditor'
import EdgeEditor from './components/EdgeEditor'
import Toolbar from './components/Toolbar'
import ProjectSettings from './components/ProjectSettings'
import ScanPanel from './components/ScanPanel'
import Welcome from './components/Welcome'
import FilterPanel from './components/FilterPanel'
import CommandPalette from './components/CommandPalette'
import ForceSettings from './components/ForceSettings'
import useMapStore from './store/useMapStore'
import useProjectStore from './store/useProjectStore'
import { getConnectedIds } from './utils/layout'
import { applyDagreLayout } from './utils/layout'

function App() {
  const {
    nodes: rawNodes,
    edges: rawEdges,
    setSelectedNode,
    setSelectedEdge,
    selectedNode,
    selectedEdge,
    importMap,
    exportMap,
    clearMap,
    undo,
    redo,
    addNode,
    deleteNode,
    deleteEdge,
  } = useMapStore()

  const { project } = useProjectStore()
  const [backendStatus, setBackendStatus] = useState('checking...')
  const [showSettings, setShowSettings] = useState(false)
  const [showScan, setShowScan] = useState(false)
  const [showPalette, setShowPalette] = useState(false)
  const [focusedNode, setFocusedNode] = useState(null)
  const [activeFilter, setActiveFilter] = useState(null)
  const [showUnlinked, setShowUnlinked] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [semanticIds, setSemanticIds] = useState(new Set())
  const handleSemanticResults = useCallback((ids) => setSemanticIds(new Set(ids)), [])
  const [layoutMode, setLayoutMode] = useState('force')
  const [theme, setTheme] = useState(() => localStorage.getItem('lm-theme') || 'dark')
  const [hoveredNode, setHoveredNode] = useState(null)
  const [dimensions, setDimensions] = useState({ width: window.innerWidth, height: window.innerHeight })
  const [currentZoom, setCurrentZoom] = useState(1)
  const graphRef = useRef(null)
  const fileInputRef = useRef(null)

  // Force settings (sliders)
  const [forceSettings, setForceSettings] = useState(() => {
    const saved = localStorage.getItem('lm-force-settings')
    return saved ? JSON.parse(saved) : {
      charge: -800,
      distance: 180,
      center: 0.03,
      linkStrength: 0.3,
      labelZoom: 0.8,
    }
  })

  const loadFromBackend = useMapStore((s) => s.loadFromBackend)
  const pollBackend = useMapStore((s) => s.pollBackend)

  // Persist force settings
  useEffect(() => {
    localStorage.setItem('lm-force-settings', JSON.stringify(forceSettings))
  }, [forceSettings])

  // Apply force settings to graph
  useEffect(() => {
    const fg = graphRef.current
    if (!fg) return
    fg.d3Force('charge')?.strength(forceSettings.charge).distanceMax(800)
    fg.d3Force('link')?.distance(forceSettings.distance).strength(forceSettings.linkStrength)
    fg.d3Force('center')?.strength(forceSettings.center)
    fg.d3ReheatSimulation()
  }, [forceSettings])

  // Window resize
  useEffect(() => {
    const handler = () => setDimensions({ width: window.innerWidth, height: window.innerHeight })
    window.addEventListener('resize', handler)
    return () => window.removeEventListener('resize', handler)
  }, [])

  // Theme
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    localStorage.setItem('lm-theme', theme)
  }, [theme])

  // Backend
  useEffect(() => {
    fetch('http://localhost:8080/api/health')
      .then((res) => res.json())
      .then((data) => { setBackendStatus(data.status); loadFromBackend() })
      .catch(() => setBackendStatus('offline'))
  }, [loadFromBackend])

  useEffect(() => {
    const interval = setInterval(pollBackend, 3000)
    return () => clearInterval(interval)
  }, [pollBackend])

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
        if (e.key === 'Escape') { setShowPalette(false); e.target.blur() }
        return
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 'p') { e.preventDefault(); setShowPalette((v) => !v); return }
      if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key === 'z') { e.preventDefault(); undo(); return }
      if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.shiftKey && (e.key === 'z' || e.key === 'Z')))) { e.preventDefault(); redo(); return }
      if (e.key === 'Escape') { setShowPalette(false); setFocusedNode(null); setHoveredNode(null); setSelectedNode(null); setSelectedEdge(null); return }
      if (e.key === 'f' || e.key === 'F') { graphRef.current?.zoomToFit(300, 50); return }
      if (e.key === 'Delete' && selectedNode) { deleteNode(selectedNode); setFocusedNode(null); return }
      if (e.key === 'Delete' && selectedEdge) { deleteEdge(selectedEdge); return }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [undo, redo, setSelectedNode, setSelectedEdge, selectedNode, selectedEdge, deleteNode, deleteEdge])

  // ─── Computed data ────────────────────────────────

  // Count connections per node
  const connectionCounts = useMemo(() => {
    const counts = new Map()
    rawEdges.forEach((e) => {
      counts.set(e.source, (counts.get(e.source) || 0) + 1)
      counts.set(e.target, (counts.get(e.target) || 0) + 1)
    })
    return counts
  }, [rawEdges])

  const maxConnections = useMemo(() => {
    let max = 1
    connectionCounts.forEach((v) => { if (v > max) max = v })
    return max
  }, [connectionCounts])

  const linkedNodeIds = useMemo(() => {
    const ids = new Set()
    rawEdges.forEach((e) => { ids.add(e.source); ids.add(e.target) })
    return ids
  }, [rawEdges])

  // Connected nodes for hover/focus highlight — stored in ref to avoid graphData rebuild
  const highlightIdsRef = useRef(null)
  useMemo(() => {
    const target = hoveredNode || focusedNode
    highlightIdsRef.current = target ? getConnectedIds(target, rawEdges) : null
  }, [hoveredNode, focusedNode, rawEdges])

  // Build graph data
  const graphData = useMemo(() => {
    const filteredNodes = rawNodes
      .filter((node) => {
        if (!showUnlinked && !linkedNodeIds.has(node.id)) return false
        return true
      })
      .map((node) => {
        let dimmed = false

        if (searchQuery) {
          const q = searchQuery.toLowerCase()
          const label = (node.data?.label || '').toLowerCase()
          const desc = (node.data?.description || '').toLowerCase()
          if (!label.includes(q) && !desc.includes(q)) dimmed = true
        }

        // Semantic search results — only show matched
        if (semanticIds.size > 0 && !semanticIds.has(node.id)) {
          dimmed = true
        }
        // Note: hover/focus highlighting is handled in paintNode via ref, not here
        if (activeFilter) {
          const label = node.data?.label || ''
          const nodeType = node.data?.nodeType || ''
          const moduleMatch = label.match(/(?:GET|POST|PUT|DELETE|PATCH)\s+\/([^/]+)/)
          const module = moduleMatch ? moduleMatch[1] : null
          if (activeFilter.startsWith('type:')) {
            if (nodeType !== activeFilter.slice(5)) dimmed = true
          } else {
            if (nodeType === 'route' && module !== activeFilter) dimmed = true
          }
        }

        const conns = connectionCounts.get(node.id) || 0
        // Size: 4 (leaf) to 20 (hub)
        const nodeSize = 4 + (conns / maxConnections) * 16

        return {
          id: node.id,
          label: node.data?.label || '',
          nodeType: node.data?.nodeType || '',
          color: node.data?.color || '#6366f1',
          description: node.data?.description || '',
          dimmed,
          connections: conns,
          nodeSize,
          x: node.position?.x,
          y: node.position?.y,
          _originalNode: node,
        }
      })

    const nodeIds = new Set(filteredNodes.map((n) => n.id))
    const links = rawEdges
      .filter((e) => nodeIds.has(e.source) && nodeIds.has(e.target))
      .map((e) => ({ source: e.source, target: e.target, id: e.id, label: e.label || '' }))

    return { nodes: filteredNodes, links }
  }, [rawNodes, rawEdges, showUnlinked, linkedNodeIds, searchQuery, semanticIds, activeFilter, connectionCounts, maxConnections])

  // ─── Canvas renderers (Obsidian style) ─────────────

  const paintNode = useCallback((node, ctx, globalScale) => {
    const isSelected = node.id === selectedNode
    const isHovered = node.id === hoveredNode
    const isFocusTarget = node.id === focusedNode || node.id === hoveredNode
    const hl = highlightIdsRef.current
    const isActive = isSelected || isHovered

    // Gentle dimming — non-highlighted still clearly visible
    const effectiveDimmed = node.dimmed || (hl && !hl.has(node.id))
    const alpha = node.dimmed ? 0.2 : (hl && !hl.has(node.id)) ? 0.5 : 1

    const r = node.nodeSize / globalScale
    const showLabel = globalScale >= forceSettings.labelZoom || isActive || isFocusTarget

    ctx.globalAlpha = alpha

    // ─── Node circle ───
    ctx.beginPath()
    ctx.arc(node.x, node.y, r, 0, Math.PI * 2)

    if (effectiveDimmed) {
      ctx.fillStyle = hexToRgba(node.color, 0.35)
    } else if (isSelected) {
      ctx.fillStyle = hexToRgba(node.color, 0.7)
    } else {
      ctx.fillStyle = hexToRgba(node.color, 0.55)
    }
    ctx.fill()

    // Thin border on select only
    if (isSelected) {
      ctx.strokeStyle = hexToRgba('#ffffff', 0.25)
      ctx.lineWidth = 1 / globalScale
      ctx.stroke()
    }

    ctx.shadowColor = 'transparent'
    ctx.shadowBlur = 0

    // ─── Connection count badge for big hubs ───
    if (node.connections >= 8 && !effectiveDimmed && globalScale > 0.3) {
      const badgeR = Math.max(5 / globalScale, 2)
      ctx.beginPath()
      ctx.arc(node.x + r * 0.7, node.y - r * 0.7, badgeR, 0, Math.PI * 2)
      ctx.fillStyle = '#fff'
      ctx.fill()
      ctx.font = `600 ${Math.max(7 / globalScale, 2)}px sans-serif`
      ctx.fillStyle = '#000'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText(node.connections, node.x + r * 0.7, node.y - r * 0.7)
    }

    // ─── Label (semantic zoom) ───
    if (showLabel && !effectiveDimmed) {
      const fontSize = Math.max(10 / globalScale, 2.5)
      ctx.font = `500 ${fontSize}px -apple-system, BlinkMacSystemFont, sans-serif`
      ctx.textAlign = 'center'
      ctx.textBaseline = 'top'
      ctx.fillStyle = isActive ? '#fff' : '#ccc'

      const labelY = node.y + r + 3 / globalScale
      const label = node.label.length > 35 ? node.label.slice(0, 35) + '…' : node.label

      // Text background for readability
      const textWidth = ctx.measureText(label).width
      const pad = 3 / globalScale
      ctx.fillStyle = hexToRgba('#0f0f1a', 0.7)
      ctx.fillRect(node.x - textWidth / 2 - pad, labelY - pad / 2, textWidth + pad * 2, fontSize + pad)

      ctx.fillStyle = isActive ? '#ddd' : '#999'
      ctx.fillText(label, node.x, labelY)
    }

    ctx.globalAlpha = 1
  }, [selectedNode, hoveredNode, focusedNode, forceSettings.labelZoom])

  const paintLink = useCallback((link, ctx, globalScale) => {
    const src = link.source
    const tgt = link.target
    if (!src || !tgt) return

    const hl = highlightIdsRef.current
    const srcHighlighted = hl?.has(src.id)
    const tgtHighlighted = hl?.has(tgt.id)
    const isHighlightedLink = hl && srcHighlighted && tgtHighlighted
    const isDimmed = src.dimmed || tgt.dimmed || (hl && !isHighlightedLink)

    if (isDimmed) {
      ctx.globalAlpha = 0.06
      ctx.strokeStyle = '#555'
      ctx.lineWidth = 0.3 / globalScale
    } else if (isHighlightedLink) {
      ctx.globalAlpha = 0.35
      ctx.strokeStyle = src.color || '#6366f1'
      ctx.lineWidth = 0.8 / globalScale
    } else if (hl) {
      // Not part of highlight — slightly faded but visible
      ctx.globalAlpha = 0.12
      ctx.strokeStyle = '#666'
      ctx.lineWidth = 0.3 / globalScale
    } else {
      ctx.globalAlpha = 0.25
      ctx.strokeStyle = hexToRgba(src.color || '#6366f1', 0.5)
      ctx.lineWidth = 0.5 / globalScale
    }

    // Slight curve
    const dx = tgt.x - src.x
    const dy = tgt.y - src.y
    const cx = (src.x + tgt.x) / 2 + dy * 0.08
    const cy = (src.y + tgt.y) / 2 - dx * 0.08

    ctx.beginPath()
    ctx.moveTo(src.x, src.y)
    ctx.quadraticCurveTo(cx, cy, tgt.x, tgt.y)
    ctx.stroke()

    // Arrow for highlighted links
    if (isHighlightedLink) {
      const arrowLen = 5 / globalScale
      const angle = Math.atan2(tgt.y - cy, tgt.x - cx)
      ctx.beginPath()
      ctx.moveTo(tgt.x, tgt.y)
      ctx.lineTo(tgt.x - arrowLen * Math.cos(angle - 0.4), tgt.y - arrowLen * Math.sin(angle - 0.4))
      ctx.lineTo(tgt.x - arrowLen * Math.cos(angle + 0.4), tgt.y - arrowLen * Math.sin(angle + 0.4))
      ctx.closePath()
      ctx.fillStyle = src.color || '#6366f1'
      ctx.globalAlpha = 0.3
      ctx.fill()
    }

    ctx.globalAlpha = 1
  }, [hoveredNode, focusedNode])

  const nodePointerArea = useCallback((node, color, ctx, globalScale) => {
    const r = Math.max(node.nodeSize / globalScale + 4 / globalScale, 5)
    ctx.beginPath()
    ctx.arc(node.x, node.y, r, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()
  }, [])

  // ─── Handlers ─────────────────────────────────────

  const handleNodeClick = useCallback((node) => {
    setSelectedNode(node.id)
    setSelectedEdge(null)
    setFocusedNode((prev) => prev === node.id ? null : node.id)
    // repaint canvas
  }, [setSelectedNode, setSelectedEdge])

  const handleNodeHover = useCallback((node) => {
    setHoveredNode(node?.id || null)
    document.body.style.cursor = node ? 'pointer' : 'default'
    // paintNode reads hoveredNode from closure — ForceGraph repaints on animation frame
  }, [])

  const handleLinkClick = useCallback((link) => {
    setSelectedEdge(link.id)
    setSelectedNode(null)
  }, [setSelectedEdge, setSelectedNode])

  const handleBackgroundClick = useCallback(() => {
    setSelectedNode(null)
    setSelectedEdge(null)
    setFocusedNode(null)
    // repaint canvas
  }, [setSelectedNode, setSelectedEdge])

  // Obsidian-style drag: low alpha so only neighbors move, not the whole graph
  const handleNodeDrag = useCallback((node) => {
    const fg = graphRef.current
    if (!fg) return
    // Keep simulation barely warm — only direct neighbors react
    const engine = fg.d3Force('link')
    if (engine) fg.d3Force('charge')?.strength(forceSettings.charge)
    fg.d3Force('center')?.strength(0) // disable center pull during drag
  }, [forceSettings.charge])

  const handleNodeDragEnd = useCallback((node) => {
    // Restore center force after drag
    const fg = graphRef.current
    if (fg) fg.d3Force('center')?.strength(forceSettings.center)
    // Sync position to backend quietly
    if (node._originalNode) {
      const store = useMapStore.getState()
      const updatedNodes = store.nodes.map((n) =>
        n.id === node.id ? { ...n, position: { x: node.x, y: node.y } } : n
      )
      // Direct backend sync without triggering importMap (which rebuilds graph)
      fetch('http://localhost:8080/api/map', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nodes: updatedNodes, edges: store.edges }),
      }).catch(() => {})
    }
  }, [])

  const zoomTimeoutRef = useRef(null)
  const handleZoom = useCallback(({ k }) => {
    // Debounce to avoid setState during render
    clearTimeout(zoomTimeoutRef.current)
    zoomTimeoutRef.current = setTimeout(() => setCurrentZoom(k), 50)
  }, [])

  // Layout
  const handleLayoutDagre = useCallback(() => {
    if (rawNodes.length === 0) return
    setLayoutMode('dagre')
    const positioned = applyDagreLayout(rawNodes, rawEdges)
    importMap({ nodes: positioned, edges: rawEdges })
    setTimeout(() => graphRef.current?.zoomToFit(300, 50), 100)
  }, [rawNodes, rawEdges, importMap])

  const handleLayoutForce = useCallback(() => {
    if (rawNodes.length === 0) return
    setLayoutMode('force')
    graphRef.current?.d3ReheatSimulation()
  }, [])

  // Command palette
  const handlePaletteAction = useCallback((action, payload) => {
    switch (action) {
      case 'focus-node': {
        setFocusedNode(payload)
        setSelectedNode(payload)
        const node = graphData.nodes.find((n) => n.id === payload)
        if (node && graphRef.current) {
          graphRef.current.centerAt(node.x, node.y, 300)
          graphRef.current.zoom(2, 300)
        }
        break
      }
      case 'layout-dagre': handleLayoutDagre(); break
      case 'layout-force': handleLayoutForce(); break
      case 'fit-view': graphRef.current?.zoomToFit(300, 50); break
      case 'scan': setShowScan(true); break
      case 'export': {
        const data = exportMap()
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url; a.download = 'logic-map.json'; a.click()
        URL.revokeObjectURL(url)
        break
      }
      case 'import': fileInputRef.current?.click(); break
      case 'undo': undo(); break
      case 'redo': redo(); break
      case 'toggle-theme': setTheme((t) => t === 'dark' ? 'light' : 'dark'); break
      case 'clear': clearMap(); break
    }
  }, [graphData.nodes, handleLayoutDagre, handleLayoutForce, exportMap, undo, redo, clearMap, setSelectedNode])

  const handleImport = (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = (e) => {
      try { importMap(JSON.parse(e.target.result), applyDagreLayout) } catch {}
    }
    reader.readAsText(file)
    event.target.value = ''
  }

  const isDark = theme === 'dark'
  const bgColor = isDark ? '#0f0f1a' : '#f8f9fa'
  const isEmpty = rawNodes.length === 0

  return (
    <div style={{
      width: '100vw', height: '100vh',
      background: bgColor,
      fontFamily: 'var(--lm-font-sans)',
      overflow: 'hidden',
    }}>
      {/* Header */}
      <div style={{
        position: 'absolute', top: 12, left: 12, zIndex: 10,
        background: 'var(--lm-bg-secondary)', border: '1px solid var(--lm-border)',
        color: 'var(--lm-text-primary)', padding: '8px 14px',
        borderRadius: 'var(--lm-radius-md)', fontSize: 13,
        display: 'flex', alignItems: 'center', gap: 10,
        boxShadow: 'var(--lm-shadow-sm)',
      }}>
        <div style={{
          width: 24, height: 24, borderRadius: 6,
          background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5">
            <circle cx="6" cy="6" r="3" /><circle cx="18" cy="18" r="3" />
            <line x1="9" y1="6" x2="18" y2="15" />
          </svg>
        </div>
        <span style={{ fontWeight: 600 }}>{project.name}</span>
        <span style={{ color: 'var(--lm-text-dim)', fontSize: 11 }}>
          {rawNodes.length} nodes · {rawEdges.length} edges
        </span>
        <div style={{
          width: 6, height: 6, borderRadius: '50%',
          background: backendStatus === 'ok' ? 'var(--lm-success)' : 'var(--lm-danger)',
        }} />
        <span style={{
          fontSize: 10, color: 'var(--lm-text-dim)',
          background: 'var(--lm-bg-tertiary)', padding: '2px 6px', borderRadius: 4,
        }}>
          zoom: {currentZoom.toFixed(1)}x
        </span>
        {focusedNode && (
          <span style={{ color: 'var(--lm-accent)', fontSize: 11 }}>
            → {rawNodes.find((n) => n.id === focusedNode)?.data?.label?.slice(0, 25)}
          </span>
        )}
        {activeFilter && (
          <button onClick={() => setActiveFilter(null)} style={{
            background: '#6366f118', border: '1px solid #6366f144',
            borderRadius: 4, color: '#6366f1', fontSize: 11, padding: '2px 8px', cursor: 'pointer',
          }}>
            {activeFilter} ✕
          </button>
        )}
        <button onClick={() => setShowPalette(true)} style={{
          background: 'var(--lm-bg-tertiary)', border: '1px solid var(--lm-border)',
          borderRadius: 4, color: 'var(--lm-text-muted)', fontSize: 10,
          padding: '2px 8px', cursor: 'pointer',
        }}>
          Ctrl+P
        </button>
      </div>

      <Sidebar />
      <FilterPanel
        activeFilter={activeFilter} onFilterChange={setActiveFilter}
        showUnlinked={showUnlinked} onToggleUnlinked={() => setShowUnlinked(!showUnlinked)}
        linkedCount={linkedNodeIds.size} totalCount={rawNodes.length}
        searchQuery={searchQuery} onSearchChange={setSearchQuery}
        onSemanticResults={handleSemanticResults}
      />
      <NodeEditor />
      <EdgeEditor />
      <Toolbar
        onOpenSettings={() => setShowSettings(true)} onOpenScan={() => setShowScan(true)}
        layoutMode={layoutMode} onLayoutDagre={handleLayoutDagre} onLayoutForce={handleLayoutForce}
      />
      {showSettings && <ProjectSettings onClose={() => setShowSettings(false)} />}
      {showScan && <ScanPanel onClose={() => setShowScan(false)} />}
      {isEmpty && <Welcome />}
      <CommandPalette open={showPalette} onClose={() => setShowPalette(false)} onAction={handlePaletteAction} />
      <ForceSettings settings={forceSettings} onChange={setForceSettings} />

      <input ref={fileInputRef} type="file" accept=".json" onChange={handleImport} style={{ display: 'none' }} />

      {/* Canvas Graph */}
      <ForceGraph2D
        ref={graphRef}
        graphData={graphData}
        width={dimensions.width}
        height={dimensions.height}
        backgroundColor={bgColor}
        nodeCanvasObject={paintNode}
        nodePointerAreaPaint={nodePointerArea}
        linkCanvasObject={paintLink}
        onNodeClick={handleNodeClick}
        onNodeHover={handleNodeHover}
        onNodeDrag={handleNodeDrag}
        onNodeDragEnd={handleNodeDragEnd}
        onLinkClick={handleLinkClick}
        onBackgroundClick={handleBackgroundClick}
        onZoom={handleZoom}
        enableNodeDrag={true}
        enableZoomInteraction={true}
        enablePanInteraction={true}
        cooldownTicks={layoutMode === 'force' ? 300 : 0}
        d3AlphaDecay={0.015}
        d3VelocityDecay={0.25}
        d3AlphaMin={0.001}
        warmupTicks={layoutMode === 'dagre' ? 0 : 150}
        autoPauseRedraw={false}
        linkDirectionalArrowLength={0}
        nodeLabel=""
        d3Force={(name, force) => {
          if (name === 'charge') force.strength(forceSettings.charge).distanceMax(800)
          if (name === 'link') force.distance(forceSettings.distance).strength(forceSettings.linkStrength)
          if (name === 'center') force.strength(forceSettings.center)
        }}
        nodeRelSize={1}
      />
    </div>
  )
}

// ─── Canvas Helpers ─────────────────────────────────

function hexToRgba(hex, alpha) {
  const r = parseInt(hex.slice(1, 3), 16) || 0
  const g = parseInt(hex.slice(3, 5), 16) || 0
  const b = parseInt(hex.slice(5, 7), 16) || 0
  return `rgba(${r},${g},${b},${alpha})`
}

export default App
