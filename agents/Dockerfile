# ===================================================================
# Dockerfile for DPIP Python CrewAI Agent Service
# ===================================================================

FROM python:3.11-slim
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy agent application code
COPY . .

# Expose agent runner port
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8081/health || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8081"]
