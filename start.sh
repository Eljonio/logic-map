#!/bin/bash

# Logic Map — start backend + frontend and open the browser
DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Starting Logic Map..."
echo ""

# Stop whatever is listening on the two ports (old Logic Map processes)
kill $(lsof -t -i:5173 -i:8080) 2>/dev/null
sleep 1

# Compile the backend
echo "Compiling backend..."
cd "$DIR/backend"
./mvnw compile -q 2>&1

# Start the backend
echo "Starting backend on :8080..."
./mvnw spring-boot:run -q &
BACKEND_PID=$!

# Start the frontend
echo "Starting frontend on :5173..."
cd "$DIR/frontend"
npx vite --port 5173 --host &
FRONTEND_PID=$!

# Wait a bit
sleep 5

# Open the browser
if command -v cmd.exe &> /dev/null; then
    cmd.exe /c start http://localhost:5173
elif command -v xdg-open &> /dev/null; then
    xdg-open http://localhost:5173
elif command -v open &> /dev/null; then
    open http://localhost:5173
fi

echo ""
echo "Logic Map is running!"
echo "  Frontend: http://localhost:5173"
echo "  Backend:  http://localhost:8080"
echo "  MCP:      register mcp-server/src/index.js in Claude Code (see README)"
echo ""
echo "Press Ctrl+C to stop"

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" INT TERM
wait
