FROM python:3.11-slim

# Fail fast, no .pyc clutter, unbuffered logs so container output is live.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# Install dependencies first so the layer caches across source changes.
COPY requirements.txt /app/backend/requirements.txt
RUN pip install --no-cache-dir -r /app/backend/requirements.txt

# The application uses absolute `from backend import ...` imports, so the source
# must live in a package directory named `backend` on the Python path.
COPY . /app/backend/

# Drop privileges: nothing in this image needs to run as root.
RUN useradd --create-home --uid 10001 appuser && chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

CMD ["uvicorn", "backend.main:app", "--host", "0.0.0.0", "--port", "8000"]
