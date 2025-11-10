# Multi-stage build for optimized Clojure application
FROM clojure:temurin-17-tools-deps AS builder

WORKDIR /app

# Copy dependency files first for better layer caching
COPY deps.edn .

# Download dependencies (this layer will be cached unless deps.edn changes)
RUN clojure -P -M:duct

# Copy the rest of the application
COPY . .

# Optional: Run tests during build (comment out if not needed)
# RUN clojure -M:test

# Production image
FROM clojure:temurin-17-tools-deps

WORKDIR /app

# Copy deps.edn and download dependencies
COPY deps.edn .
RUN clojure -P -M:duct

# Copy source code and configuration
COPY src/ ./src/
COPY duct.edn .
COPY test.db ./test.db

# Expose the application port (adjust if your app uses a different port)
EXPOSE 3000

# Health check (optional - adjust endpoint if needed)
# HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
#   CMD curl -f http://localhost:3000/health || exit 1

# Run the application
# Using exec to start and keep the Duct system running
CMD ["clojure", "-M:duct", "--main"]