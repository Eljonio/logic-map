import dagre from '@dagrejs/dagre'

const NODE_WIDTH = 200
const NODE_HEIGHT = 50

export function applyDagreLayout(nodes, edges, direction = 'LR') {
  if (nodes.length === 0) return nodes

  const g = new dagre.graphlib.Graph()
  g.setDefaultEdgeLabel(() => ({}))
  g.setGraph({
    rankdir: direction,
    nodesep: 40,
    ranksep: 250,
    edgesep: 20,
    marginx: 50,
    marginy: 50,
  })

  // Assign rank based on node type for better grouping
  const typeRank = {
    middleware: 0,
    route: 1,
    http: 2,
    event: 2,
    cache: 3,
    database: 4,
    file: 4,
    env: 5,
  }

  nodes.forEach((node) => {
    const rank = typeRank[node.data?.nodeType] ?? 2
    g.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT, rank })
  })

  edges.forEach((edge) => {
    if (g.hasNode(edge.source) && g.hasNode(edge.target)) {
      g.setEdge(edge.source, edge.target)
    }
  })

  dagre.layout(g)

  return nodes.map((node) => {
    const pos = g.node(node.id)
    if (!pos) return node
    return {
      ...node,
      position: {
        x: pos.x - NODE_WIDTH / 2,
        y: pos.y - NODE_HEIGHT / 2,
      },
    }
  })
}

/**
 * Get all node IDs connected to a given node (direct neighbors)
 */
export function getConnectedIds(nodeId, edges) {
  const connected = new Set()
  connected.add(nodeId)
  edges.forEach((edge) => {
    if (edge.source === nodeId) connected.add(edge.target)
    if (edge.target === nodeId) connected.add(edge.source)
  })
  return connected
}

/**
 * Extract module name from route label
 */
export function getModuleName(label) {
  const match = label?.match(/(?:GET|POST|PUT|DELETE|PATCH)\s+\/([^/]+)/)
  return match ? match[1] : null
}
