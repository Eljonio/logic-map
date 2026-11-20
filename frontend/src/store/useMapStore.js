import { create } from 'zustand'
import { applyNodeChanges, applyEdgeChanges, addEdge } from '@xyflow/react'

const API = 'http://localhost:8080/api'

let nodeIdCounter = 0
const generateNodeId = () => `node_${Date.now()}_${++nodeIdCounter}`
const generateEdgeId = () => `edge_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

const MAX_HISTORY = 30

const syncToBackend = async (nodes, edges) => {
  try {
    await fetch(`${API}/map`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nodes, edges }),
    })
  } catch { /* backend offline */ }
}

const useMapStore = create((set, get) => ({
  nodes: [],
  edges: [],
  selectedNode: null,
  selectedEdge: null,
  _lastSyncHash: '',
  _history: [],
  _historyIndex: -1,

  _pushHistory: () => {
    const { nodes, edges, _history, _historyIndex } = get()
    const newHistory = _history.slice(0, _historyIndex + 1)
    newHistory.push({ nodes: structuredClone(nodes), edges: structuredClone(edges) })
    if (newHistory.length > MAX_HISTORY) newHistory.shift()
    set({ _history: newHistory, _historyIndex: newHistory.length - 1 })
  },

  undo: () => {
    const { _history, _historyIndex } = get()
    if (_historyIndex <= 0) return
    const prev = _history[_historyIndex - 1]
    syncToBackend(prev.nodes, prev.edges)
    set({ nodes: prev.nodes, edges: prev.edges, _historyIndex: _historyIndex - 1 })
  },

  redo: () => {
    const { _history, _historyIndex } = get()
    if (_historyIndex >= _history.length - 1) return
    const next = _history[_historyIndex + 1]
    syncToBackend(next.nodes, next.edges)
    set({ nodes: next.nodes, edges: next.edges, _historyIndex: _historyIndex + 1 })
  },

  canUndo: () => get()._historyIndex > 0,
  canRedo: () => get()._historyIndex < get()._history.length - 1,

  loadFromBackend: async () => {
    try {
      const res = await fetch(`${API}/map`)
      const data = await res.json()
      if (data.nodes?.length > 0 || data.edges?.length > 0) {
        const hash = JSON.stringify({ n: data.nodes.length, e: data.edges.length })
        set({ nodes: data.nodes, edges: data.edges, _lastSyncHash: hash })
        get()._pushHistory()
      }
    } catch { /* backend offline */ }
  },

  pollBackend: async () => {
    try {
      const res = await fetch(`${API}/map`)
      const data = await res.json()
      const hash = JSON.stringify({ n: data.nodes?.length || 0, e: data.edges?.length || 0 })
      const current = get()._lastSyncHash
      if (hash !== current && (data.nodes?.length > 0 || data.edges?.length > 0)) {
        set({ nodes: data.nodes || [], edges: data.edges || [], _lastSyncHash: hash })
      }
    } catch { /* backend offline */ }
  },

  onNodesChange: (changes) => {
    set((state) => {
      const nodes = applyNodeChanges(changes, state.nodes)
      syncToBackend(nodes, state.edges)
      return { nodes }
    })
  },

  onEdgesChange: (changes) => {
    set((state) => {
      const edges = applyEdgeChanges(changes, state.edges)
      syncToBackend(state.nodes, edges)
      return { edges }
    })
  },

  onConnect: (connection) => {
    get()._pushHistory()
    set((state) => {
      const edge = {
        ...connection,
        id: generateEdgeId(),
        label: '',
        data: { type: 'default', origin: 'manual' },
      }
      const edges = addEdge(edge, state.edges)
      syncToBackend(state.nodes, edges)
      return { edges }
    })
  },

  addNode: (nodeType, position) => {
    get()._pushHistory()
    const id = generateNodeId()
    const newNode = {
      id,
      type: 'dynamic',
      position,
      data: {
        label: nodeType.label,
        nodeType: nodeType.key,
        color: nodeType.color,
        description: '',
        origin: 'manual',
      },
    }
    set((state) => {
      const nodes = [...state.nodes, newNode]
      syncToBackend(nodes, state.edges)
      return { nodes }
    })
    return id
  },

  updateNodeData: (nodeId, updates) => {
    set((state) => {
      const nodes = state.nodes.map((n) =>
        n.id === nodeId ? { ...n, data: { ...n.data, ...updates } } : n
      )
      syncToBackend(nodes, state.edges)
      return { nodes }
    })
  },

  updateEdgeData: (edgeId, updates) => {
    set((state) => {
      const edges = state.edges.map((e) =>
        e.id === edgeId
          ? { ...e, ...updates, data: { ...e.data, ...updates.data } }
          : e
      )
      syncToBackend(state.nodes, edges)
      return { edges }
    })
  },

  deleteNode: (nodeId) => {
    get()._pushHistory()
    set((state) => {
      const nodes = state.nodes.filter((n) => n.id !== nodeId)
      const edges = state.edges.filter(
        (e) => e.source !== nodeId && e.target !== nodeId
      )
      syncToBackend(nodes, edges)
      return { nodes, edges, selectedNode: null, selectedEdge: null }
    })
  },

  deleteEdge: (edgeId) => {
    get()._pushHistory()
    set((state) => {
      const edges = state.edges.filter((e) => e.id !== edgeId)
      syncToBackend(state.nodes, edges)
      return { edges, selectedEdge: null }
    })
  },

  setSelectedNode: (nodeId) => set({ selectedNode: nodeId, selectedEdge: null }),
  setSelectedEdge: (edgeId) => set({ selectedEdge: edgeId, selectedNode: null }),

  clearMap: () => {
    get()._pushHistory()
    fetch(`${API}/map`, { method: 'DELETE' }).catch(() => {})
    set({ nodes: [], edges: [], selectedNode: null, selectedEdge: null, _lastSyncHash: '' })
  },

  importMap: (data, layoutFn) => {
    get()._pushHistory()
    let nodes = data.nodes || []
    const edges = data.edges || []
    if (layoutFn && nodes.length > 0) {
      nodes = layoutFn(nodes, edges)
    }
    const hash = JSON.stringify({ n: nodes.length, e: edges.length })
    syncToBackend(nodes, edges)
    set({ nodes, edges, selectedNode: null, selectedEdge: null, _lastSyncHash: hash })
  },

  exportMap: () => {
    const { nodes, edges } = get()
    return { nodes, edges }
  },
}))

export default useMapStore
