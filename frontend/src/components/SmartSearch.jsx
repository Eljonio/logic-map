import { useState, useEffect, useRef } from 'react'
import { Sparkles, X, Loader } from 'lucide-react'

const API = 'http://localhost:8080/api'

function SmartSearch({ onResults, onClear }) {
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [available, setAvailable] = useState(null)
  const [results, setResults] = useState([])
  const debounceRef = useRef(null)

  // Stable refs for callbacks so useEffect doesn't re-fire on parent re-renders
  const onResultsRef = useRef(onResults)
  const onClearRef = useRef(onClear)
  useEffect(() => { onResultsRef.current = onResults }, [onResults])
  useEffect(() => { onClearRef.current = onClear }, [onClear])

  useEffect(() => {
    fetch(`${API}/search/status`)
      .then((r) => r.json())
      .then((d) => setAvailable(d.ollamaAvailable))
      .catch(() => setAvailable(false))
  }, [])

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (!query.trim()) {
      setResults([])
      onResultsRef.current?.([])
      return
    }
    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      try {
        const res = await fetch(`${API}/search/semantic`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ query, topK: 15 }),
        })
        const data = await res.json()
        setResults(data)
        onResultsRef.current?.(data.map((r) => r.id))
      } catch {
        setResults([])
      } finally {
        setLoading(false)
      }
    }, 400)
    return () => clearTimeout(debounceRef.current)
  }, [query])

  const handleClear = () => {
    setQuery('')
    setResults([])
    onClearRef.current?.()
  }

  if (available === false) return null

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        background: '#0f0f1a',
        border: '1px solid #8b5cf644',
        borderRadius: 6,
        padding: '5px 8px',
      }}>
        {loading ? (
          <Loader size={12} color="#8b5cf6" className="spin" />
        ) : (
          <Sparkles size={12} color="#8b5cf6" />
        )}
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Smart search (by meaning)..."
          style={{
            background: 'none',
            border: 'none',
            outline: 'none',
            color: '#fff',
            fontSize: 12,
            width: '100%',
          }}
        />
        {query && (
          <button
            onClick={handleClear}
            style={{
              background: 'none',
              border: 'none',
              color: '#555',
              cursor: 'pointer',
              padding: 0,
              display: 'flex',
            }}
          >
            <X size={12} />
          </button>
        )}
      </div>

      {results.length > 0 && (
        <div style={{
          fontSize: 10,
          color: '#666',
          padding: '0 4px',
        }}>
          {results.length} matches by meaning
        </div>
      )}
    </div>
  )
}

export default SmartSearch
