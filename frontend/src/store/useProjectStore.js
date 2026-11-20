import { create } from 'zustand'

const DEFAULT_PROJECT = {
  name: 'New Project',
  nodeTypes: [
    { key: 'default', label: 'Node', color: '#6366f1', icon: 'circle' },
  ],
  edgeTypes: [
    { key: 'default', label: 'Connection', style: 'solid' },
  ],
}

const loadProject = () => {
  try {
    const saved = localStorage.getItem('logicmap-project')
    return saved ? JSON.parse(saved) : DEFAULT_PROJECT
  } catch {
    return DEFAULT_PROJECT
  }
}

const useProjectStore = create((set, get) => ({
  project: loadProject(),

  setProjectName: (name) => {
    set((state) => {
      const project = { ...state.project, name }
      localStorage.setItem('logicmap-project', JSON.stringify(project))
      return { project }
    })
  },

  addNodeType: (nodeType) => {
    set((state) => {
      const nodeTypes = [...state.project.nodeTypes, nodeType]
      const project = { ...state.project, nodeTypes }
      localStorage.setItem('logicmap-project', JSON.stringify(project))
      return { project }
    })
  },

  removeNodeType: (key) => {
    set((state) => {
      const nodeTypes = state.project.nodeTypes.filter((t) => t.key !== key)
      const project = { ...state.project, nodeTypes }
      localStorage.setItem('logicmap-project', JSON.stringify(project))
      return { project }
    })
  },

  addEdgeType: (edgeType) => {
    set((state) => {
      const edgeTypes = [...state.project.edgeTypes, edgeType]
      const project = { ...state.project, edgeTypes }
      localStorage.setItem('logicmap-project', JSON.stringify(project))
      return { project }
    })
  },

  removeEdgeType: (key) => {
    set((state) => {
      const edgeTypes = state.project.edgeTypes.filter((t) => t.key !== key)
      const project = { ...state.project, edgeTypes }
      localStorage.setItem('logicmap-project', JSON.stringify(project))
      return { project }
    })
  },

  updateNodeType: (key, updates) => {
    set((state) => {
      const nodeTypes = state.project.nodeTypes.map((t) =>
        t.key === key ? { ...t, ...updates } : t
      )
      const project = { ...state.project, nodeTypes }
      localStorage.setItem('logicmap-project', JSON.stringify(project))
      return { project }
    })
  },

  updateEdgeType: (key, updates) => {
    set((state) => {
      const edgeTypes = state.project.edgeTypes.map((t) =>
        t.key === key ? { ...t, ...updates } : t
      )
      const project = { ...state.project, edgeTypes }
      localStorage.setItem('logicmap-project', JSON.stringify(project))
      return { project }
    })
  },
}))

export default useProjectStore
