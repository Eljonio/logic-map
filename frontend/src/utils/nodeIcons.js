import {
  Route, Database, Zap, HardDrive, Globe, FileText,
  Settings, Shield, Box, Layers,
} from 'lucide-react'

const ICON_MAP = {
  route: Route,
  database: Database,
  event: Zap,
  cache: HardDrive,
  http: Globe,
  file: FileText,
  env: Settings,
  middleware: Shield,
}

// Unicode chars for Canvas rendering (lucide can't render on Canvas)
export const NODE_ICON_CHARS = {
  route:      '⟶',
  database:   '⛁',
  event:      '⚡',
  cache:      '⧉',
  http:       '⊕',
  file:       '📄',
  env:        '⚙',
  middleware:  '🛡',
}

/**
 * Get lucide icon component for a node type (for DOM rendering).
 */
export function getNodeIcon(nodeType) {
  return ICON_MAP[nodeType] || Box
}

export function getAllNodeIcons() {
  return { ...ICON_MAP, default: Box, group: Layers }
}
