FROM python:3.12-slim

# Run as non-root for security
RUN useradd -m -u 1000 appuser

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy app code
COPY . .
RUN chown -R appuser:appuser /app

USER appuser

# Cloud Run convention — port 8080
ENV PORT=8080
EXPOSE 8080

# Use ADK web server — exposes /run_live WebSocket endpoint for bidi-streaming
CMD ["adk", "web", "--port", "8080", "--host", "0.0.0.0", "."]
