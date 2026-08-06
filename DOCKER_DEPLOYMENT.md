# TruePay Docker Deployment Guide

This guide explains how to containerize and run the TruePay project using Docker.

## Prerequisites

### 1. Install Docker Desktop (Windows)

Download from: https://www.docker.com/products/docker-desktop

**Steps:**
1. Download **Docker Desktop for Windows**
2. Run the installer and follow the setup wizard
3. Enable **WSL 2 (Windows Subsystem for Linux 2)** during installation
4. Restart your computer
5. Verify installation:
```powershell
docker --version
docker run hello-world
```

### 2. Verify Docker Compose is installed

Docker Desktop includes Docker Compose. Verify:
```powershell
docker-compose --version
```

---

## Project Setup

Your project already has the necessary Docker files:

- **`Dockerfile`** — Multi-stage build for the Spring Boot app
- **`docker-compose.yml`** — Orchestrates MySQL + Spring Boot app
- **`init.sql`** — Database schema initialization

---

## How to Run TruePay with Docker

### Option 1: Using Docker Compose (Recommended)

This runs the entire stack: MySQL database + Spring Boot application.

```powershell
# 1. Navigate to project root
cd C:\Users\Administrator\IdeaProjects\11.102-TruePay

# 2. Build and start all services
docker-compose up -d

# 3. Check if services are running
docker-compose ps

# 4. View logs
docker-compose logs -f app

# 5. Access the application
# Open browser: http://localhost:8082

# 6. Stop all services
docker-compose down

# 7. Stop and remove volumes (full cleanup)
docker-compose down -v
```

**What happens:**
- MySQL starts on `localhost:3306`
- Spring Boot app starts on `localhost:8082`
- Database schema is auto-initialized from `init.sql`
- App waits for MySQL to be healthy before starting (health check)

---

### Option 2: Build Image Manually

```powershell
# 1. Build the Docker image
docker build -t truepay:latest .

# 2. Run MySQL container first
docker run -d `
  --name truepay-mysql `
  -e MYSQL_DATABASE=truepay `
  -e MYSQL_ROOT_PASSWORD=n3u3da! `
  -e MYSQL_USER=truepay `
  -e MYSQL_PASSWORD=n3u3da! `
  -p 3306:3306 `
  -v mysql_data:/var/lib/mysql `
  mysql:8.0

# 3. Run the app container (wait 15 seconds for MySQL to be ready)
Start-Sleep -Seconds 15
docker run -d `
  --name truepay-app `
  -e SERVER_PORT=8082 `
  -e DB_URL=jdbc:mysql://truepay-mysql:3306/truepay `
  -e DB_USER=truepay `
  -e DB_PASSWORD=n3u3da! `
  -p 8082:8082 `
  --link truepay-mysql:mysql `
  truepay:latest

# 4. Check logs
docker logs -f truepay-app
```

---

## Configuration Reference

### Environment Variables

Edit `docker-compose.yml` to customize:

| Variable | Default | Purpose |
|----------|---------|---------|
| `SERVER_PORT` | 8082 | Application port |
| `DB_URL` | `jdbc:mysql://mysql:3306/truepay` | Database connection |
| `DB_USER` | truepay | Database username |
| `DB_PASSWORD` | n3u3da! | Database password |
| `MYSQL_DATABASE` | truepay | Database name |
| `MYSQL_ROOT_PASSWORD` | n3u3da! | MySQL root password |

**⚠️ Security Warning:** These are default credentials for development only. Change passwords for production:

```yaml
# docker-compose.yml
environment:
  DB_PASSWORD: your-secure-password-here
  MYSQL_PASSWORD: your-secure-password-here
  MYSQL_ROOT_PASSWORD: your-secure-root-password-here
```

---

## Access Points

Once running:

| Service | URL |
|---------|-----|
| **Web App** | http://localhost:8082 |
| **MySQL** | localhost:3306 (user: `truepay`, password: `n3u3da!`) |

---

## Docker Commands Reference

```powershell
# View running containers
docker ps

# View all containers (including stopped)
docker ps -a

# View logs
docker logs <container-id-or-name>
docker logs -f truepay-app  # Follow logs in real-time

# Stop services
docker-compose stop

# Restart services
docker-compose restart

# Remove services
docker-compose down

# Remove volumes (database data)
docker-compose down -v

# Rebuild image (after code changes)
docker-compose build --no-cache
docker-compose up -d

# Access app container shell
docker exec -it truepay-app /bin/sh

# Inspect image
docker inspect truepay:latest

# Remove image
docker rmi truepay:latest
```

---

## Dockerfile Explanation

The provided `Dockerfile` uses a **multi-stage build**:

```dockerfile
# Stage 1: Build with Maven
FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline  # Cache deps
COPY src ./src
RUN mvn -B -DskipTests clean package  # Build JAR

# Stage 2: Runtime (lightweight)
FROM eclipse-temurin:21-jre-alpine  # Alpine = smaller image
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring  # Non-root user
COPY --from=builder /workspace/target/*.jar /app/app.jar
RUN chown -R spring:spring /app
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Benefits:**
- ✅ Final image is **small** (~150-200 MB)
- ✅ No Maven/build tools in production image
- ✅ Runs as non-root user (security)
- ✅ Alpine Linux base (minimal surface area)
- ✅ Caches Maven dependencies for faster rebuilds

---

## Troubleshooting

### Port Already in Use
If port 8082 is already in use:
```powershell
# Find process using port 8082
netstat -ano | findstr :8082

# Either stop that service or change port in docker-compose.yml:
# Change "8082:8082" to "8083:8082"
```

### Container Exits Immediately
Check logs:
```powershell
docker-compose logs app
```
Common issues:
- Database not ready → wait longer
- Wrong DB credentials → verify environment variables
- Port conflict → use different port

### MySQL Won't Start
```powershell
# Check MySQL logs
docker-compose logs mysql

# Restart MySQL
docker-compose restart mysql
```

### Reset Everything
```powershell
# Stop and remove all containers and volumes
docker-compose down -v

# Clean up images
docker rmi truepay:latest

# Start fresh
docker-compose up -d
```

---

## Production Deployment Tips

1. **Change credentials** in `docker-compose.yml`
2. **Use environment file** (`.env`) instead of hardcoding values
3. **Add volume mounts** for logs and data persistence
4. **Use Docker Swarm or Kubernetes** for orchestration
5. **Add health checks** to both services
6. **Use private registry** for images
7. **Enable SSL/TLS** for secure communication
8. **Monitor logs** with ELK stack or similar

---

## Next Steps

1. Install Docker Desktop
2. Run: `docker-compose up -d`
3. Open: http://localhost:8082
4. Monitor: `docker-compose logs -f`

