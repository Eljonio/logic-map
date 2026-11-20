# Logic Map

[![tests](https://github.com/Eljonio/logic-map/actions/workflows/test.yml/badge.svg)](https://github.com/Eljonio/logic-map/actions/workflows/test.yml)

A code map for Express (JS/TS) backends that an AI coding agent can query and correct through MCP. A Spring Boot
service scans a project with regex parsers and builds a graph: routes, the tables and columns they touch, socket
events, cache keys, env variables, external URLs. An MCP server exposes that graph to Claude Code as 18 tools
(scan, impact analysis, search, edit, learned filters, verification against migrations). A React page draws the same
graph.

The question it is built for: "if I change this table or column, which endpoints are affected?" Grep answers with
every textual match, including other tables' columns of the same name and HTTP status codes. The map answers with the
routes whose handler (or a local helper it calls) contains SQL for that table, with the operation and columns on each
edge. It is a pointer for where to read, not a replacement for reading the code.

## Where to look first

| to check | open | command |
|---|---|---|
| the MCP server and its 18 tools: `logic_map_get`, `_add_node`, `_add_edge`, `_update_node`, `_update_edge`, `_delete_node`, `_delete_edge`, `_search`, `_scan`, `_impact`, `_annotate`, `_reject`, `_accept`, `_semantic_search`, `_find_similar`, `_verify_migrations`, `_verify_database`, `_clear` | [`mcp-server/src/index.js`](mcp-server/src/index.js) | `cd mcp-server && npm ci && npm start` (stdio; see [MCP setup](#connect-to-claude-code)) |
| route body extraction: brace matching that skips strings and comments, local call chain up to depth 5, tables, columns, auth, transactions, request fields | [`RouteBodyAnalyzer.java`](backend/src/main/java/com/logicmap/service/parser/RouteBodyAnalyzer.java) | scan [`examples/express-demo`](examples/express-demo), see [Try it](#try-it-on-the-demo-app) |
| how findings become nodes and edges, and where learned filters and embeddings are applied | [`ScanService.java`](backend/src/main/java/com/logicmap/service/ScanService.java) | same |
| learned per-project filters and verification against `CREATE TABLE` in migrations | [`FilterService.java`](backend/src/main/java/com/logicmap/service/FilterService.java), [`TableVerifier.java`](backend/src/main/java/com/logicmap/service/TableVerifier.java), [`VerifyController.java`](backend/src/main/java/com/logicmap/controller/VerifyController.java) | `POST /api/verify/migrations` |
| impact analysis (BFS over the graph, done in the MCP server) | `logic_map_impact` in [`mcp-server/src/index.js`](mcp-server/src/index.js) | MCP tool call |
| optional semantic search through a local Ollama embedding model | [`EmbeddingService.java`](backend/src/main/java/com/logicmap/service/EmbeddingService.java), [`SemanticSearchService.java`](backend/src/main/java/com/logicmap/service/SemanticSearchService.java) | `GET /api/search/status` |
| why it is built this way (two-level collection, no hard-coded filters, pitfalls) | [`docs/LESSONS.md`](docs/LESSONS.md) | |
| what is tested | [`backend/src/test`](backend/src/test) — **1 test** (Spring context loads) | `cd backend && ./mvnw test` |

## How it works

```
                    Claude Code
                        │ stdio (MCP)
                 mcp-server/src/index.js      React page (frontend/, Vite, :5173)
                 18 tools, thin HTTP client      │ polls /api/map every 3 s
                        │                        │
                        └──────────┬─────────────┘
                                   │ HTTP JSON
                     backend/ (Spring Boot 3.5, Java 21, :8080)
       ScanController → ScanService → 11 regex parsers → RawFinding → graph
       MapController (CRUD), FilterController, VerifyController, SearchController
                                   │
                ~/.logic-map/map.json, ~/.logic-map/filters.json
                optional: Ollama (localhost:11434) for embeddings
```

### Scanning

`POST /api/scan {projectPath, fileExtensions}` walks the project (default extensions `.js`, `.ts`; `node_modules`,
`.git` and `dist` are skipped), runs every parser on every file, and replaces the current map with the result.

| parser | finds | node type |
|---|---|---|
| `ExpressRouteParser` | `router.get/post/put/delete/patch('/path')`, `app.*`, `router.use('/path')` | `route`, `middleware` |
| `RouteBodyAnalyzer` | per route: tables, columns, operation, emits, cache keys, auth middleware, transactions, `req.body/params/query` fields | edges + route description |
| `DatabaseQueryParser` | SQL anywhere in the file (`FROM/JOIN/INTO/UPDATE/TABLE`), plus any function call with a table-like string argument | `database` |
| `WebSocketParser` | `socket/io/ws.emit('name')`, `io.to(room).emit('name')` (`.on(...)` is parsed but not drawn) | `event` |
| `CacheParser` | `redis/cache/client.get/set/del('key')` and variants, `cacheGet/cacheSet/cacheInvalidate('key')` | `cache` |
| `HttpClientParser` | `fetch('literal')`, `axios.get/post/...('literal')`, `http(s)://` string literals | `http` |
| `FileOpsParser` | `fs.readFile/writeFile/unlink/readdir/...` with a literal path | `file` |
| `EnvVarParser` | `process.env.X`, `process.env['X']` | `env` |
| `MiddlewareParser` | `app.use('/path', router)`, `app.use(name)` | `middleware` |
| `ImportParser`, `FunctionCallParser` | `require/import`, function → function calls | not used for edges by the current `ScanService` |

Nodes are created wide: `DatabaseQueryParser` deliberately over-collects table candidates (it will pick up
`require('express')` as a "table"). Edges are created narrow: a route → table edge exists only if
`RouteBodyAnalyzer` found the SQL inside that route's handler body or inside a function defined in the same file and
reachable from it (depth ≤ 5). Edge labels carry the SQL operation and up to four column names
(`SELECT id,total,status`, `INSERT (7 cols)`, `QUERY price`). Only three edge kinds are produced: route → table,
route → event, route → cache. Env, HTTP, file and middleware nodes stay unlinked.

### Learned filters and verification

There are no hard-coded "this is not a table" lists. Instead:

- `logic_map_reject` / `logic_map_accept` (or `POST /api/filters/reject|accept`) store rejected labels per project
  path in `~/.logic-map/filters.json`; the next scan drops those nodes and their edges.
- `logic_map_verify_migrations` reads `CREATE TABLE` statements from `.sql` files under a folder and auto-rejects every
  `database` node that is not among them.
- `logic_map_verify_database` does the same against a live database through JDBC metadata (see Limitations).

### Impact analysis

`logic_map_impact` is computed in the MCP server: it finds nodes whose label or description contains the query, runs
an undirected BFS over all edges, and returns the impacted nodes grouped by type plus the direct edges with their
labels. Because the BFS is transitive, two routes that share a table are reported as impacting each other.

### Semantic search (optional)

If Ollama answers on `http://localhost:11434`, each scan also embeds every node (`nomic-embed-text` by default) and
stores the vector in the node. `logic_map_semantic_search` and `logic_map_find_similar` rank nodes by cosine
similarity. Without Ollama they return an empty list and everything else keeps working.

### UI

`frontend/` is React 19 + Vite. The canvas is a force-directed graph (`react-force-graph-2d`) with a Dagre layout
button, a command palette (`Ctrl+P`, `cmdk`), filters by node type, text and semantic search, focus on a node's
neighbours, undo/redo (30 steps), JSON export/import, a scan dialog with project discovery and history, and per-project
node/edge types kept in `localStorage`. Nodes without edges are hidden by default (toggle in the filter panel). The page
polls the backend every 3 seconds, so changes made through MCP appear without a reload.

## Install and run

Requirements: Java 21, Node.js 20.19+ or 22.12+ (Vite 8), `lsof` if you use `start.sh`. Maven is not needed; the
wrapper downloads it. Optional: [Ollama](https://ollama.com) with `ollama pull nomic-embed-text` for semantic search.

```bash
git clone <this repo> logic-map && cd logic-map

# backend (http://localhost:8080)
cd backend && ./mvnw spring-boot:run

# frontend (http://localhost:5173), in another terminal
cd frontend && npm ci && npm run dev

# MCP server dependencies
cd mcp-server && npm ci
```

`./start.sh` does the backend and frontend in one go, but note that it first kills whatever listens on ports 5173 and
8080, and starts Vite with `--host` (reachable from your network). It was not run during the export.

### Connect to Claude Code

```bash
claude mcp add logic-map -e LOGIC_MAP_URL=http://localhost:8080 -- node /absolute/path/to/logic-map/mcp-server/src/index.js
```

or in a project's `.mcp.json`:

```json
{
  "mcpServers": {
    "logic-map": {
      "command": "node",
      "args": ["/absolute/path/to/logic-map/mcp-server/src/index.js"],
      "env": { "LOGIC_MAP_URL": "http://localhost:8080" }
    }
  }
}
```

The backend has to be running; the MCP server is only a client of its HTTP API.

### Try it on the demo app

[`examples/express-demo`](examples/express-demo) is a small fictional Express app (two routers, one migration). With the
backend running:

```bash
DEMO="$PWD/examples/express-demo"
curl -s -X POST localhost:8080/api/scan -H 'Content-Type: application/json' -d "{\"projectPath\":\"$DEMO\"}"
curl -s -X POST localhost:8080/api/verify/migrations -H 'Content-Type: application/json' \
     -d "{\"projectPath\":\"$DEMO\",\"migrationsPath\":\"$DEMO/migrations\"}"
curl -s -X POST localhost:8080/api/scan -H 'Content-Type: application/json' -d "{\"projectPath\":\"$DEMO\"}"
```

What this gave during the export (Ollama not running): the first scan produced 19 nodes and 9 edges, including two
false "tables", `express` (from `require('express')`) and `admin` (from `requireRole('admin')`). Verification against
the migration rejected exactly those two; the second scan produced 17 nodes and 9 edges. Examples of what the scan
recorded:

```
GET /:id  -[SELECT id,total,status]->  orders
GET /:id  -[SELECT product_id,qty]->   order_items     (SQL is in a local helper the handler calls)
POST /    -[INSERT customer_id,total,status]-> orders
POST /    -[emits]-> WS: order:created
GET /     -[cache]-> Cache: products:all

POST / => routes/orders.js:21 | 🔒 auth: authenticateToken | 💾 transaction | 📥 body: customerId, total
```

Note that route labels don't include the router's mount prefix (`/api/orders`).

## Configuration

| setting | default | where |
|---|---|---|
| `server.port` | `8080` | backend, Spring property (`--server.port=...`) |
| `ollama.url` | `http://localhost:11434` | backend, Spring property |
| `ollama.embedding.model` | `nomic-embed-text` | backend, Spring property |
| `LOGIC_MAP_URL` | `http://localhost:8080` | MCP server, environment variable |
| data folder | `~/.logic-map/` (`map.json`, `filters.json`) | backend, derived from `user.home` |

The frontend has `http://localhost:8080` written into `App.jsx`, `store/useMapStore.js`, `components/ScanPanel.jsx` and
`components/SmartSearch.jsx`; changing the backend port means editing those.

## API

`GET /api/health` ·
`GET|PUT|DELETE /api/map` · `POST /api/map/nodes` · `PUT|DELETE /api/map/nodes/{id}` · `POST /api/map/edges` ·
`PUT|DELETE /api/map/edges/{id}` · `GET /api/map/search?q=` ·
`POST /api/scan {projectPath, fileExtensions}` ·
`GET /api/projects/discover[?root=]` · `GET|POST /api/projects/history` ·
`GET|DELETE /api/filters?projectPath=` · `POST /api/filters/reject|accept {projectPath, label}` ·
`POST /api/verify/migrations {projectPath, migrationsPath}` · `POST /api/verify/database {projectPath, jdbcUrl, user, password}` ·
`POST /api/search/semantic {query, topK}` · `POST /api/search/similar {nodeId, topK}` · `GET /api/search/status`

## Tests and checks

What was run for this export, on Ubuntu 24.04 with OpenJDK 21 and Node 24:

| check | command | result |
|---|---|---|
| backend build and tests | `cd backend && ./mvnw test` | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` |
| frontend build | `cd frontend && npm ci && npm run build` | builds (one chunk of 647 kB, Vite warns about the size) |
| frontend lint | `cd frontend && npm run lint` | **fails: 7 errors, 3 warnings** (unused variables, React hooks rules) |
| MCP server | `initialize` + `tools/list` over stdio | starts, reports `logic-map 1.0.0`, lists 18 tools |
| end to end | each of the 18 tools called against the backend with the demo app | all answered; results as in [Try it](#try-it-on-the-demo-app) |
| access from other sites | request with `Origin: https://evil.example` / `Origin: http://localhost:5173` | 403 without CORS headers / allowed; the port listens on 127.0.0.1 only |
| failed database check | `POST /api/verify/database` with an unreachable JDBC URL | returns an error, rejects nothing |
| dependencies | `npm audit` in `frontend/` and `mcp-server/` after `npm audit fix` | 0 advisories in both (2026-11-20); the MCP server still lists 18 tools |
| UI | built page opened in headless Chrome against the running backend | loads and shows "17 nodes · 9 edges" with the backend online; interaction was not checked by hand |

The only automated test is the generated Spring Boot `contextLoads`. Parsers, the scanner, the filters and the MCP
server have no automated tests.

## Limitations

- **Express-only, regex-based.** JS/TS with Express-style `router.METHOD('/path')` routes. No AST, no other frameworks
  or languages, no ORM support without raw SQL, no dynamic table names or generated routes.
- **Call chains stop at the file boundary.** A route that calls a helper from another module gets no edge for the SQL
  inside that helper. A project whose SQL lives in shared modules gets table nodes but few or no edges.
- **Route identity is method + path, without the mount prefix.** `GET /:id` in two different routers becomes one node,
  and `logic_map_search` for the full public URL won't find it.
- **Noise by design.** `DatabaseQueryParser` turns any function call with a table-like string argument into a table
  candidate. Without migrations to verify against, the noise has to be rejected by hand or by the agent.
  `ExpressRouteParser` and `MiddlewareParser` both report `app.use('/path', ...)`, so mounts show up twice.
- **`verify_database` cannot connect to anything as shipped.** The `pom.xml` contains no JDBC drivers, so any JDBC URL
  fails with "No suitable driver". The call then returns an error and rejects nothing (an empty table list used to
  reject every table node of the project; fixed for this export). Add a driver to use it, or use `verify_migrations`.
- **No authentication.** The backend listens on 127.0.0.1 and accepts cross-origin calls only from pages served from
  `localhost` / `127.0.0.1`, so other machines and other sites open in the browser cannot call it. Any local process
  or local page still can: read the map, list directories through `/api/projects/discover` and trigger scans of any
  path the backend user can read.
- **Project discovery assumes WSL.** `/api/projects/discover` searches `/mnt/c/Users` when it exists, otherwise the
  home folder (4 levels deep).
- **Single shared map.** There is one map for the whole backend. Every scan replaces it; scan history is kept in memory
  only and lost on restart.
- **Embeddings live inside `map.json`.** With Ollama on, each node carries a 768-float vector, which makes
  `map.json` and the output of `logic_map_get` large. Prefer `logic_map_search` / `logic_map_impact` on big projects.
- **Dependencies were only patched.** `npm audit fix` (non-breaking) was applied to `frontend/` and `mcp-server/`
  for the export; nothing else was upgraded.
- **Leftovers.** `@xyflow/react` is used only for change helpers in the store (the canvas itself was replaced).
  In `ScanService`, the import map and call graph are built but not used.

## License

MIT, see [LICENSE](LICENSE).

**Provenance.** Exported on 2026-11-20 from a private working copy. Personal design notes, a usage log from private
projects, names of those projects, local paths and Vite/Spring template files were removed, so the public history
starts with the export.
