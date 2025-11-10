# Docker Setup for Clojure SQL Lab

This guide explains how to run the Clojure SQL Lab application in a Docker container.

## Prerequisites

- Docker installed on your system
- Docker Compose (optional, but recommended)

## Building the Docker Image

### Option 1: Using Docker directly

Build the image:
```bash
docker build -t cloj-sql-lab .
```

Run the container:
```bash
docker run -p 3000:3000 cloj-sql-lab
```

### Option 2: Using Docker Compose (Recommended)

Start the application:
```bash
docker-compose up
```

Start in detached mode (background):
```bash
docker-compose up -d
```

Stop the application:
```bash
docker-compose down
```

View logs:
```bash
docker-compose logs -f
```

## Dockerfile Optimizations

The Dockerfile includes several optimizations:

1. **Multi-stage build**: Separates build and runtime environments
2. **Layer caching**: Dependencies are cached separately from source code
3. **Minimal copying**: Only necessary files are included in the final image
4. **Port exposure**: Port 3000 is exposed for the application

## Customization

### Changing the Application Port

If your application runs on a different port, update:
- `EXPOSE` directive in the Dockerfile
- Port mapping in docker-compose.yml

### Adding Database Services

The docker-compose.yml includes a commented PostgreSQL service. Uncomment and configure as needed:

```yaml
postgres:
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: cloj_sql_lab
    POSTGRES_USER: developer
    POSTGRES_PASSWORD: devpassword
  ports:
    - "5432:5432"
```

### Environment Variables

Add environment variables in docker-compose.yml:

```yaml
environment:
  - DATABASE_URL=postgresql://localhost:5432/mydb
  - CLOJURE_ENV=production
```

## Development Mode

For development with live code reloading, the docker-compose.yml mounts source directories as volumes. This allows you to edit code on your host machine and see changes reflected in the container.

To disable this for production, comment out the volume mounts in docker-compose.yml.

## Running Tests

To run tests during the build process, uncomment this line in the Dockerfile:

```dockerfile
RUN clojure -M:test
```

Or run tests in a running container:

```bash
docker-compose exec app clojure -M:test
```

## Troubleshooting

### Container exits immediately

Check logs:
```bash
docker-compose logs app
```

### Port already in use

Change the host port in docker-compose.yml:
```yaml
ports:
  - "3001:3000"  # Use port 3001 instead
```

### Database connection issues

Ensure the database service is running and healthy:
```bash
docker-compose ps
```

## Cleaning Up

Remove containers and networks:
```bash
docker-compose down
```

Remove containers, networks, and volumes:
```bash
docker-compose down -v
```

Remove the built image:
```bash
docker rmi cloj-sql-lab
```

