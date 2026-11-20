#!/usr/bin/env node

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import { z } from 'zod'

const BACKEND_URL = process.env.LOGIC_MAP_URL || 'http://localhost:8080'

async function api(path, options = {}) {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  return res.json()
}

const server = new McpServer({
  name: 'logic-map',
  version: '1.0.0',
})

// ─── GET MAP ────────────────────────────────────────────────

server.tool(
  'logic_map_get',
  'Get the current map (all nodes and edges)',
  {},
  async () => {
    const data = await api('/api/map')
    return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
  }
)

// ─── ADD NODE ───────────────────────────────────────────────

server.tool(
  'logic_map_add_node',
  'Add a node to the map',
  {
    label: z.string().describe('Display name of the node'),
    nodeType: z.string().optional().describe('Type key (from project config)'),
    color: z.string().optional().describe('Hex color, e.g. #22c55e'),
    description: z.string().optional().describe('Short description'),
    x: z.number().optional().describe('X position'),
    y: z.number().optional().describe('Y position'),
  },
  async ({ label, nodeType, color, description, x, y }) => {
    const data = await api('/api/map/nodes', {
      method: 'POST',
      body: JSON.stringify({ label, nodeType, color, description, x, y }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data) }] }
  }
)

// ─── ADD EDGE ───────────────────────────────────────────────

server.tool(
  'logic_map_add_edge',
  'Add an edge (connection) between two nodes',
  {
    source: z.string().describe('Source node ID'),
    target: z.string().describe('Target node ID'),
    label: z.string().optional().describe('Edge label'),
    edgeType: z.string().optional().describe('Edge type key (from project config)'),
  },
  async ({ source, target, label, edgeType }) => {
    const data = await api('/api/map/edges', {
      method: 'POST',
      body: JSON.stringify({ source, target, label, edgeType }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data) }] }
  }
)

// ─── DELETE NODE ────────────────────────────────────────────

server.tool(
  'logic_map_delete_node',
  'Delete a node and all its connected edges',
  {
    nodeId: z.string().describe('Node ID to delete'),
  },
  async ({ nodeId }) => {
    const data = await api(`/api/map/nodes/${nodeId}`, { method: 'DELETE' })
    return { content: [{ type: 'text', text: JSON.stringify(data) }] }
  }
)

// ─── DELETE EDGE ────────────────────────────────────────────

server.tool(
  'logic_map_delete_edge',
  'Delete an edge',
  {
    edgeId: z.string().describe('Edge ID to delete'),
  },
  async ({ edgeId }) => {
    const data = await api(`/api/map/edges/${edgeId}`, { method: 'DELETE' })
    return { content: [{ type: 'text', text: JSON.stringify(data) }] }
  }
)

// ─── UPDATE NODE ────────────────────────────────────────────

server.tool(
  'logic_map_update_node',
  'Update a node label, description, or color',
  {
    nodeId: z.string().describe('Node ID'),
    label: z.string().optional().describe('New label'),
    description: z.string().optional().describe('New description'),
    color: z.string().optional().describe('New hex color'),
  },
  async ({ nodeId, label, description, color }) => {
    const data = await api(`/api/map/nodes/${nodeId}`, {
      method: 'PUT',
      body: JSON.stringify({ label, description, color }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data) }] }
  }
)

// ─── UPDATE EDGE ────────────────────────────────────────────

server.tool(
  'logic_map_update_edge',
  'Update an edge label or type',
  {
    edgeId: z.string().describe('Edge ID'),
    label: z.string().optional().describe('New label'),
    edgeType: z.string().optional().describe('New edge type key'),
  },
  async ({ edgeId, label, edgeType }) => {
    const data = await api(`/api/map/edges/${edgeId}`, {
      method: 'PUT',
      body: JSON.stringify({ label, edgeType }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data) }] }
  }
)

// ─── SEARCH ─────────────────────────────────────────────────

server.tool(
  'logic_map_search',
  'Search nodes by label or description',
  {
    query: z.string().describe('Search text'),
  },
  async ({ query }) => {
    const data = await api(`/api/map/search?q=${encodeURIComponent(query)}`)
    return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
  }
)

// ─── SCAN PROJECT ───────────────────────────────────────────

server.tool(
  'logic_map_scan',
  'Scan a project directory and generate map from code',
  {
    projectPath: z.string().describe('Absolute path to project directory'),
    fileExtensions: z.array(z.string()).optional().describe('File extensions to scan, e.g. [".js", ".ts"]'),
  },
  async ({ projectPath, fileExtensions }) => {
    const data = await api('/api/scan', {
      method: 'POST',
      body: JSON.stringify({ projectPath, fileExtensions }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
  }
)

// ─── IMPACT ANALYSIS ────────────────────────────────────────

server.tool(
  'logic_map_impact',
  'Show what will be affected if a node changes. Returns all directly and indirectly connected nodes — useful before modifying a table, endpoint, or service.',
  {
    query: z.string().describe('Node label or partial match (e.g. "orders" or "POST /orders")'),
  },
  async ({ query }) => {
    // Get full map
    const map = await api('/api/map')
    const nodes = map.nodes || []
    const edges = map.edges || []

    // Find matching nodes
    const q = query.toLowerCase()
    const matches = nodes.filter(n =>
      n.data?.label?.toLowerCase().includes(q) ||
      n.data?.description?.toLowerCase().includes(q)
    )

    if (matches.length === 0) {
      return { content: [{ type: 'text', text: `No nodes found matching "${query}"` }] }
    }

    // For each match, find all connected nodes (BFS)
    const allImpacted = new Set()
    const matchIds = new Set(matches.map(m => m.id))

    for (const match of matches) {
      const visited = new Set()
      const queue = [match.id]
      visited.add(match.id)

      while (queue.length > 0) {
        const current = queue.shift()
        for (const edge of edges) {
          let neighbor = null
          if (edge.source === current) neighbor = edge.target
          if (edge.target === current) neighbor = edge.source
          if (neighbor && !visited.has(neighbor)) {
            visited.add(neighbor)
            allImpacted.add(neighbor)
            queue.push(neighbor)
          }
        }
      }
    }

    // Build result
    const nodeMap = Object.fromEntries(nodes.map(n => [n.id, n]))
    const impactedNodes = [...allImpacted]
      .map(id => nodeMap[id])
      .filter(Boolean)

    // Group by type
    const byType = {}
    for (const n of impactedNodes) {
      const type = n.data?.nodeType || 'unknown'
      if (!byType[type]) byType[type] = []
      byType[type].push(n.data?.label)
    }

    // Find direct edges
    const directEdges = edges.filter(e =>
      matchIds.has(e.source) || matchIds.has(e.target)
    ).map(e => ({
      from: nodeMap[e.source]?.data?.label || e.source,
      to: nodeMap[e.target]?.data?.label || e.target,
      label: e.label || '',
    }))

    const result = {
      query,
      matchedNodes: matches.map(m => ({ id: m.id, label: m.data?.label, type: m.data?.nodeType, file: m.data?.description })),
      totalImpacted: impactedNodes.length,
      byType,
      directConnections: directEdges,
    }

    return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) }] }
  }
)

// ─── ANNOTATE NODE ──────────────────────────────────────────

server.tool(
  'logic_map_annotate',
  'Add a note/annotation to a node (stored in description field)',
  {
    nodeId: z.string().describe('Node ID'),
    note: z.string().describe('Note text to add'),
  },
  async ({ nodeId, note }) => {
    // Get current node
    const map = await api('/api/map')
    const node = (map.nodes || []).find(n => n.id === nodeId)
    if (!node) {
      return { content: [{ type: 'text', text: `Node ${nodeId} not found` }] }
    }
    const currentDesc = node.data?.description || ''
    const newDesc = currentDesc ? `${currentDesc} | NOTE: ${note}` : `NOTE: ${note}`

    const data = await api(`/api/map/nodes/${nodeId}`, {
      method: 'PUT',
      body: JSON.stringify({ description: newDesc }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data) }] }
  }
)

// ─── FILTER (LEARN) ─────────────────────────────────────────

server.tool(
  'logic_map_reject',
  'Mark a node label as irrelevant/noise for a project. It will be filtered out on next scan. Use this to teach the scanner what is not a real table, endpoint, etc.',
  {
    projectPath: z.string().describe('Project path that was scanned'),
    label: z.string().describe('Node label to reject (e.g. "employee_id" or "tmp_import")'),
  },
  async ({ projectPath, label }) => {
    const data = await api('/api/filters/reject', {
      method: 'POST',
      body: JSON.stringify({ projectPath, label }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data) }] }
  }
)

server.tool(
  'logic_map_accept',
  'Un-reject a previously rejected label — bring it back on next scan.',
  {
    projectPath: z.string().describe('Project path'),
    label: z.string().describe('Node label to un-reject'),
  },
  async ({ projectPath, label }) => {
    const data = await api('/api/filters/accept', {
      method: 'POST',
      body: JSON.stringify({ projectPath, label }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data) }] }
  }
)

// ─── SEMANTIC SEARCH ────────────────────────────────────────

server.tool(
  'logic_map_semantic_search',
  'Semantic search using vector embeddings — finds nodes by MEANING, not just text match. Use English for best results. E.g. "payment" finds POST /checkout, /pay, etc.',
  {
    query: z.string().describe('Search query in natural language (English works best)'),
    topK: z.number().optional().describe('Max results to return (default 10)'),
  },
  async ({ query, topK }) => {
    const data = await api('/api/search/semantic', {
      method: 'POST',
      body: JSON.stringify({ query, topK: topK || 10 }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
  }
)

server.tool(
  'logic_map_find_similar',
  'Find nodes semantically similar to a given node. Useful for "what else is like this?"',
  {
    nodeId: z.string().describe('Source node ID'),
    topK: z.number().optional().describe('Max results (default 10)'),
  },
  async ({ nodeId, topK }) => {
    const data = await api('/api/search/similar', {
      method: 'POST',
      body: JSON.stringify({ nodeId, topK: topK || 10 }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
  }
)

// ─── VERIFY TABLES ──────────────────────────────────────────

server.tool(
  'logic_map_verify_migrations',
  'Verify found tables against SQL migration files. Auto-rejects false positives.',
  {
    projectPath: z.string().describe('Project path that was scanned'),
    migrationsPath: z.string().describe('Path to SQL migrations directory'),
  },
  async ({ projectPath, migrationsPath }) => {
    const data = await api('/api/verify/migrations', {
      method: 'POST',
      body: JSON.stringify({ projectPath, migrationsPath }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
  }
)

server.tool(
  'logic_map_verify_database',
  'Verify found tables against a live database. Auto-rejects false positives.',
  {
    projectPath: z.string().describe('Project path that was scanned'),
    jdbcUrl: z.string().describe('JDBC URL, e.g. jdbc:mysql://localhost:3306/mydb'),
    user: z.string().optional().describe('DB username'),
    password: z.string().optional().describe('DB password'),
  },
  async ({ projectPath, jdbcUrl, user, password }) => {
    const data = await api('/api/verify/database', {
      method: 'POST',
      body: JSON.stringify({ projectPath, jdbcUrl, user, password }),
    })
    return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
  }
)

// ─── CLEAR MAP ──────────────────────────────────────────────

server.tool(
  'logic_map_clear',
  'Clear the entire map (remove all nodes and edges)',
  {},
  async () => {
    const data = await api('/api/map', { method: 'DELETE' })
    return { content: [{ type: 'text', text: JSON.stringify(data) }] }
  }
)

// ─── START ──────────────────────────────────────────────────

const transport = new StdioServerTransport()
await server.connect(transport)
