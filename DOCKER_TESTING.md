# 🧪 Docker Testing Guide - TruePay

## Status: 🔴 Docker NOT Installed

Before running tests, you must **install Docker Desktop**.

---

## ⬇️ Step 1: Install Docker Desktop

### Download & Install (5 minutes)
```
https://www.docker.com/products/docker-desktop
```

**Installation Steps:**
1. Download for Windows
2. Run installer
3. Enable WSL 2 when prompted
4. Complete installation
5. **Restart your computer**

### Verify Installation
After restart, open PowerShell:
```powershell
docker --version
# Should show: Docker version XX.X.X, build XXXXX

docker ps
# Should show: Empty container list (no error)

docker run hello-world
# Should show: "Hello from Docker!" message
```

---

## ✅ Step 2: Run Docker Verification Script

Once Docker is installed:

```powershell
cd C:\Users\Administrator\IdeaProjects\11.102-TruePay

# Run the verification script
.\docker-verify.ps1
```

**This will test:**
- ✅ Docker installation
- ✅ Docker daemon status
- ✅ Docker Compose
- ✅ WSL 2 integration
- ✅ Image pulling
- ✅ Container execution
- ✅ Project files
- ✅ System resources

**Expected Output:**
```
[1] Testing: Docker command exists... ✅ PASS
    📌 Docker version 24.0.0, build xxxxx
[2] Testing: Docker daemon is running... ✅ PASS
    📌 Docker daemon is responsive
...
✅ All tests PASSED! Docker is ready.

Next Steps:
  1. Run: .\docker-start.ps1
  2. Choose Option 1: Start TruePay
  3. Open: http://localhost:8082
```

---

## 🚀 Step 3: Test TruePay Docker Deployment

### Full Stack Test
```powershell
# Start the complete stack
.\docker-start.ps1
# Choose: 1 (Start TruePay)

# Wait 30-60 seconds for first startup...
```

### Individual Component Tests

#### Test 1: Check Services Status
```powershell
docker-compose ps

# Expected output:
# NAME              STATUS              PORTS
# truepay-mysql     Up (healthy)        3306/tcp
# truepay-app       Up                  0.0.0.0:8082->8082/tcp
```

#### Test 2: Verify MySQL Database
```powershell
# Check MySQL logs
docker-compose logs mysql

# Should see:
# "mysqld: ready for connections"
# "port: 3306"
```

#### Test 3: Verify Spring Boot App
```powershell
# Check app logs
docker-compose logs app

# Should see:
# "Tomcat started on port..."
# "Started Main in X seconds"
```

#### Test 4: Test Application Endpoint
```powershell
# HTTP test (should return 200 OK)
curl http://localhost:8082

# Or open in browser:
# http://localhost:8082
```

#### Test 5: Database Connection Test
```powershell
# Connect to MySQL from host
# Use your favorite MySQL client or:

docker-compose exec mysql mysql -u truepay -pn3u3da! -D truepay -e "SHOW TABLES;"

# Should list database tables
```

#### Test 6: Container Logs Timeline
```powershell
# View all recent logs
docker-compose logs --tail=50

# Follow live logs
docker-compose logs -f

# View only app logs with timestamps
docker-compose logs --timestamps app

# View MySQL logs
docker-compose logs mysql

# View last 100 lines
docker-compose logs --tail=100
```

#### Test 7: Resource Usage
```powershell
# Check container stats
docker stats --no-stream

# Output:
# CONTAINER    CPU %    MEM USAGE / LIMIT
# truepay-app  0.5%     350MiB / 16GiB
# truepay-mysql 0.3%    200MiB / 16GiB
```

#### Test 8: Network Testing
```powershell
# Check network
docker network ls | findstr truepay

# Containers on same network can access each other
docker-compose exec app ping mysql
# Should succeed (MySQL is reachable)
```

#### Test 9: Volume Testing
```powershell
# Check volumes
docker volume ls | findstr truepay

# Inspect MySQL data volume
docker volume inspect truepay_mysql_data

# Data persists after container restart
docker-compose restart mysql
docker-compose logs mysql
# Database should be intact
```

#### Test 10: Image Inspection
```powershell
# Check built image
docker images | findstr truepay

# Inspect image details
docker inspect truepay:latest

# Check image size, layers, etc.
docker history truepay:latest
```

---

## 🧪 Advanced Testing

### Test Memory/CPU Limits
```powershell
# View resource constraints
docker stats

# To add limits, edit docker-compose.yml:
# services:
#   app:
#     deploy:
#       resources:
#         limits:
#           cpus: '1'
#           memory: 512M
```

### Test Health Checks
```powershell
# View health status
docker ps --format "{{.Names}}\t{{.Status}}\t{{.Health}}"

# truepay-mysql     Up 2 minutes       healthy
# truepay-app       Up 2 minutes       (health check not explicit)
```

### Test Volume Persistence
```powershell
# 1. Start stack
docker-compose up -d

# 2. Create test data in DB
docker-compose exec mysql mysql -u truepay -pn3u3da! -D truepay -e "CREATE TABLE test (id INT);"

# 3. Stop and remove containers
docker-compose down

# 4. Restart
docker-compose up -d

# 5. Check if table still exists
docker-compose exec mysql mysql -u truepay -pn3u3da! -D truepay -e "SHOW TABLES;" | findstr test
# Should find "test" table
```

### Test Network Isolation
```powershell
# App should reach MySQL by service name
docker-compose exec app curl http://mysql:3306
# Should fail (MySQL port test) but proves connectivity

# Or test Spring app can connect to DB
docker-compose logs app | findstr "Connection pool initialized"
```

### Test Image Rebuild
```powershell
# Modify source code
# Then rebuild without cache
docker-compose build --no-cache

# Should recompile from source
```

---

## 📊 Test Results Template

```powershell
# Save test results:

Write-Output "=== Docker Test Results ===" | Tee-Object docker-test-results.txt
Write-Output "Date: $(Get-Date)" | Tee-Object docker-test-results.txt -Append
Write-Output "" | Tee-Object docker-test-results.txt -Append
Write-Output "1. Docker Version:" | Tee-Object docker-test-results.txt -Append
docker --version | Tee-Object docker-test-results.txt -Append
Write-Output "" | Tee-Object docker-test-results.txt -Append
Write-Output "2. Container Status:" | Tee-Object docker-test-results.txt -Append
docker-compose ps | Tee-Object docker-test-results.txt -Append
Write-Output "" | Tee-Object docker-test-results.txt -Append
Write-Output "3. Resource Usage:" | Tee-Object docker-test-results.txt -Append
docker stats --no-stream | Tee-Object docker-test-results.txt -Append
Write-Output "" | Tee-Object docker-test-results.txt -Append
Write-Output "4. Application Test:" | Tee-Object docker-test-results.txt -Append
curl http://localhost:8082 | Tee-Object docker-test-results.txt -Append

# Results saved to docker-test-results.txt
```

---

## ✨ Quick Test Script

```powershell
# Quick validation of TruePay Docker setup
function Test-TruePayDocker {
    Write-Host "🧪 TruePay Docker Test Suite" -ForegroundColor Cyan
    Write-Host ""
    
    # Test 1
    Write-Host "[Test 1] Services running..." -NoNewline
    $running = docker-compose ps --format json | ConvertFrom-Json
    if ($running.Count -ge 2) { Write-Host " ✅" -ForegroundColor Green } else { Write-Host " ❌" -ForegroundColor Red }
    
    # Test 2
    Write-Host "[Test 2] App responding..." -NoNewline
    try {
        $response = curl -s http://localhost:8082
        Write-Host " ✅" -ForegroundColor Green
    } catch {
        Write-Host " ❌" -ForegroundColor Red
    }
    
    # Test 3
    Write-Host "[Test 3] Database reachable..." -NoNewline
    try {
        docker-compose exec -T mysql mysql -u truepay -pn3u3da! -D truepay -e "SELECT 1" > $null 2>&1
        Write-Host " ✅" -ForegroundColor Green
    } catch {
        Write-Host " ❌" -ForegroundColor Red
    }
    
    # Test 4
    Write-Host "[Test 4] Logs clean..." -NoNewline
    $errors = docker-compose logs app | findstr -i "error" | Measure-Object -Line
    if ($errors.Lines -lt 5) { Write-Host " ✅" -ForegroundColor Green } else { Write-Host " ⚠️" -ForegroundColor Yellow }
    
    Write-Host ""
    Write-Host "✅ docker-compose is working correctly!" -ForegroundColor Green
}

Test-TruePayDocker
```

---

## 🔧 Troubleshooting

### If Tests Fail

```powershell
# 1. Check Docker is running
docker ps

# 2. View error logs
docker-compose logs app
docker-compose logs mysql

# 3. Restart services
docker-compose restart

# 4. Full reset
docker-compose down -v
docker-compose up -d

# 5. Check system resources
docker stats
```

---

## 📋 Pre-Testing Checklist

- [ ] Docker Desktop installed
- [ ] WSL 2 enabled
- [ ] Computer restarted after Docker install
- [ ] Port 8082 is available (not in use)
- [ ] Disk space available (>2GB)
- [ ] At least 4GB RAM available
- [ ] docker-compose.yml exists
- [ ] Dockerfile exists
- [ ] init.sql exists

---

## Next Command

Once Docker is installed, run:
```powershell
.\docker-verify.ps1
```

This will automatically run all verification tests and show results.

