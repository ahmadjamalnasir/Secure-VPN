#!/bin/sh
# Apply any pending migrations before serving. `alembic upgrade head` is a
# no-op when the schema is already current, so this is safe on every restart.
set -e

echo "Applying database migrations..."
alembic -c backend/alembic.ini upgrade head

echo "Starting API..."
exec uvicorn backend.main:app --host 0.0.0.0 --port 8000
