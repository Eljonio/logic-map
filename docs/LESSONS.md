# Lessons from building the scanner

Design notes written down while building Logic Map with an AI coding agent. They explain why the scanner is
regex-based, why filters are learned instead of hard-coded, and why links are built only from route bodies.

The scanner was tuned against a few private Express codebases. The examples below are described in general terms,
and the counts quoted in them were measured on those codebases, so they cannot be reproduced from this repository.

## Principles

### 1. Keep two levels: wide for nodes, narrow for edges

The first version linked loosely: if a routes file imported `helpers.js` and `helpers.js` contained SQL, every route
in that file was linked to every table in that SQL. On one project that gave 76 links where 53 were real.

Extracting the exact handler body with brace matching fixed the links. But removing the global table search altogether
broke another project, a load tester whose SQL lived in helper scripts that no route handler reaches: almost all of
its tables disappeared from the map.

So the scanner keeps both levels: a global pass creates table nodes (wide, may include noise), and a precise pass
creates route → table edges (narrow, only what is in the handler's call chain). The noise is removed afterwards by
verification.

### 2. Don't hard-code filters

Several times the tempting fix was "drop `employee_id`, `your`, `cart`, they are obviously not tables". It is wrong
because:

- on another project `cart` can be a real table;
- a fixed list doesn't grow by itself;
- hidden rules can't be seen or undone by the user.

A filter is either **learned per project** (reject/accept, verification against migrations or a live database) or a
**universal rule of the language** (SQL keywords). Domain knowledge never goes into a parser.

### 3. Filters are per project

A global filter lets noise from one project hide legitimate names in another. `FilterService` keys rejected labels by
the normalised project path.

### 4. Brace matching is more than counting braces

The first matcher counted `{` and `}` and broke on `const x = "}";`. The matcher has to skip strings (`'`, `"`,
`` ` ``), line and block comments, and escapes; otherwise a handler body "ends" inside a string literal.

### 5. Follow the call chain, but only inside the file

Following imports across files made every route in a file reachable from everything in its helpers, which produced
most of the false links above. Following only functions defined in the same file keeps links honest. Recursion depth is
capped at 5.

"Reachable through an import" is not "actually called". The cost is known: a route that really calls a function from
another file loses that link. A missing link sends a person to check by hand; a false link gives false confidence.

### 6. Recall first when collecting candidates

It is better to collect many table candidates and throw most of them away by comparing with migrations than to collect
a "clean" list and silently miss real tables. What the verifier rejects can be accepted back; what was never found
can't be recovered.

### 7. Real projects use wrappers

On one project nobody called `socket.emit('newOrder')` directly; everything went through `sendToStaff`,
`sendToTable`, `broadcast`, `notify`. A parser that only knew `socket.emit` found zero events. The patterns now cover
families of names (`sendTo*`, `broadcast*`, `notify*`, `publish*`). The same is true for databases: `batchInsert()`,
`insertMany()` and similar wrappers are more common than a bare `db.query()`.

## What works and what doesn't

Works well:

- Express routes `router.METHOD('/path', ...)` / `app.METHOD(...)`
- `process.env.X` and `process.env['X']`
- tables in `FROM / JOIN / INTO / UPDATE` inside a route body and its local helpers
- columns in `INSERT INTO t (a, b)`, `UPDATE t SET a = ?`, and `SELECT a, b FROM t` without `*` or aggregates
- auth middleware in the route signature (`authenticateToken`, `requireRole('admin')`, ...)
- transactions (`beginTransaction` / `commit` / `rollback`) and `req.body / req.params / req.query` fields

Works poorly:

| case | why |
|---|---|
| dynamic table names (`'SELECT * FROM ' + table`) | the variable can't be resolved by regex |
| ORM calls without SQL (`Model.findAll()`) | needs a parser per ORM |
| callbacks passed into helpers | control flow through function arguments isn't tracked |
| call chains deeper than 5 | deliberate cap |
| semantic search in Russian | the default embedding model (`nomic-embed-text`) handles it badly |
| `SELECT *` | the edge says "all columns", not which ones |

Doesn't work:

- **No AST.** Parsing is regex plus brace matching, not Babel or the TypeScript compiler. A deliberate trade-off for
  simplicity.
- **Metaprogramming.** Routes generated in a loop (`routes.forEach(r => app[r.method](r.path, ...))`) are invisible.
- **Other languages.** JS/TS only.

## Architecture that held up

### Two-level collection

```
global parser (DatabaseQueryParser)   → table nodes from all code (may include noise)
precise analyser (RouteBodyAnalyzer)  → route → table edges only from handler bodies
verifier (TableVerifier)              → compares nodes with migrations / live DB, auto-rejects noise
```

### Findings → nodes → edges

Parsers emit a flat `RawFinding`. `ScanService` groups findings and builds the graph. New parsers can be added without
touching `ScanService`, and extra data rides in `edge.data.meta` (for example the column list).

### MCP as the feedback loop

The parser doesn't have to be perfect. It has to be good enough for an agent to finish the job through MCP:

```
parser:  most of the map automatically
agent:   the rest through reject / annotate / impact
filter:  remembers the corrections for the next scan
```

## Pitfalls already hit

1. **`[^)]*` for function arguments.** Used to catch `batchInsert(conn, 'table', [...])`, but the arguments contain `]`
   and `)` inside arrays and calls. Fixed with a lazy `.*?`.
2. **Alternation with several groups.** With `a(x)|b(y)|c(z)` the code read only `group(1)` and missed matches of the
   other branches. Fixed by taking the first non-empty group.
3. **Slow boot.** Spring Boot needs several seconds; scripted tests that did `start && sleep && curl` failed randomly.
   Poll `/api/health` instead of sleeping.
4. **Embeddings inside `map.json`.** Each node carries a 768-float vector. On a project with about 330 nodes the file
   grew from about 120 KB to 3.4 MB, and `logic_map_get` exceeded the agent's tool-output limit. Use `logic_map_search`
   or `logic_map_impact` instead of `get`; moving vectors to a separate file is still to do.
5. **Changing a record without backward compatibility.** Adding `embedding` to `NodeData` broke loading of old
   `map.json` files. Fixed with `@JsonInclude(NON_NULL)` and a constructor without the vector.
6. **Cross-file call chains.** See principle 5.
7. **Event name taken from the room argument.** `io.to(roomId).emit('event')` matched `roomId` as the event name.
   Fixed with an explicit pattern for `to(...).emit(...)`.
8. **SQL keyword case.** `Select` / `SELECT` slipped past a lowercase reserved-word set. Fixed with
   `CASE_INSENSITIVE` and lowercasing before comparison.

## Checklist before changing a parser

1. Scan at least two different projects: one typical Express app and one atypical (SQL outside route handlers).
2. Compare node and edge counts before and after; a sudden drop means a regression.
3. Make sure filters learned on one project don't affect another.
4. Run verification against migrations where a project has them.
5. Check that embedding generation still works (`GET /api/search/status`).

## When something goes wrong

- **The scanner finds noise.** Run `verify_migrations` (or `verify_database`), or reject labels through MCP. Don't add
  a hard-coded list to the parser.
- **The scanner misses something real.** Check whether the parser produces a `RawFinding` for it. If it does and the
  map doesn't show it, the bug is in `ScanService`; if it doesn't, extend the parser's pattern.
- **The map is empty after a change.** The `parser → finding → node → edge` chain is broken; check `RawFinding` usage.
- **MCP says the result exceeds the token limit.** Use `logic_map_search`, `logic_map_semantic_search` or
  `logic_map_impact` instead of `logic_map_get`.

## Philosophy

The map is not the source of truth; the code is. The map answers "where should I look?". If it says a column is touched
by a set of endpoints, you open those endpoints and check. It saves time on searching, not on verification, which is
why chasing 100 % precision isn't worth it.
