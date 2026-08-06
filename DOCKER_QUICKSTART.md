# 🐳 Docker Quick Start Guide

## 1️⃣ Install Docker

### Windows
1. Download **Docker Desktop for Windows**: https://www.docker.com/products/docker-desktop
2. Run the installer and follow setup wizard
3. Enable **WSL 2** when prompted (recommended)
4. Restart your computer
5. Verify:
```powershell
docker --version
docker run hello-world
```

### macOS
```bash
# via Homebrew
brew install --cask docker

# Or download from: https://www.docker.com/products/docker-desktop
```

### Linux
```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
```

---

## 2️⃣ Deploy TruePay

### Option A: Windows (PowerShell) — Easiest ⭐

```powershell
# Run the interactive script
.\docker-start.ps1
```

Follow the menu to:
- **Option 1**: Start TruePay
- **Option 2**: View logs
- **Option 3**: Stop
- **Option 6**: Check status

### Option B: Manual Commands (Windows)

```powershell
# Start
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f app

# Stop
docker-compose down
```

### Option C: Linux/macOS

```bash
# Start
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f app

# Stop
docker-compose down
```

---

## 3️⃣ Access TruePay

Once running, open your browser:

```
http://localhost:8082
```

**Database direct access** (if needed):
```
Host: localhost
Port: 3306
Username: truepay
Password: n3u3da!
```

---

## 4️⃣ Common Commands

```powershell
# View running services
docker-compose ps

# View all running containers
docker ps

# Follow app logs in real-time
docker-compose logs -f app

# Follow MySQL logs
docker-compose logs -f mysql

# Restart app
docker-compose restart app

# Stop all services
docker-compose stop

# Stop and remove everything
docker-compose down

# Stop and remove including volumes (full cleanup)
docker-compose down -v

# Rebuild image after code changes
docker-compose build --no-cache
docker-compose up -d
```

---

## 5️⃣ Verify Everything Works

```powershell
# 1. Check Docker is running
docker ps

# 2. Check services are healthy
docker-compose ps
# Should show:
#   truepay-mysql      running
#   truepay-app        running

# 3. Test the app (should return 200 OK)
curl http://localhost:8082

# 4. View logs
docker-compose logs -f
```

---

## 6️⃣ Troubleshooting

### ❌ Port 8082 Already in Use
Find what's using it:
```powershell
netstat -ano | findstr :8082
```

**Solution A:** Stop the conflicting service  
**Solution B:** Change port in `docker-compose.yml`
```yaml
app:
  ports:
    - "8083:8082"  # Use 8083 instead
```

### ❌ Container Keeps Restarting
Check logs:
```powershell
docker-compose logs app
```
Common causes:
- Database not ready → MySQL took too long to start
- Credentials wrong → check `docker-compose.yml`
- Port conflict → see above

### ❌ MySQL Won't Start
```powershell
docker-compose logs mysql
docker-compose restart mysql
```

### ❌ Need Fresh Start
```powershell
# Remove everything
docker-compose down -v
docker rmi truepay:latest

# Start over
docker-compose up -d
```

---

## 7️⃣ File Structure

```
11.102-TruePay/
├── Dockerfile              ← Build Spring Boot app
├── docker-compose.yml      ← Orchestrate MySQL + App
├── docker-start.ps1        ← Windows startup script
├── docker-start.sh         ← Linux/macOS startup script
├── .env.example            ← Environment template
├── init.sql                ← Database schema
├── DOCKER_DEPLOYMENT.md    ← Detailed guide
├── pom.xml                 ← Maven config
├── src/
│   ├── main/
│   │   ├── java/          ← Spring Boot code
│   │   └── resources/
│   │       └── static/    ← Frontend (HTML/CSS/JS)
│   └── test/
└── target/                ← Build output
```

---

## 8️⃣ Customization

### Change Database Password
Edit `docker-compose.yml`:
```yaml
environment:
  MYSQL_PASSWORD: your-new-password
  DB_PASSWORD: your-new-password
```

### Change Application Port
Edit `docker-compose.yml`:
```yaml
env:
  SERVER_PORT: 9000
ports:
  - "9000:9000"
```

### Use .env File (Best Practice)
```bash
# 1. Copy the template
cp .env.example .env

# 2. Edit .env with your values
# DB_PASSWORD=your-secure-password

# 3. docker-compose will automatically load it
docker-compose up -d
```

---

## 9️⃣ Production Notes

⚠️ **Current setup is for development only**

For production:
- ✅ Change all passwords in `.env`
- ✅ Enable SSL/TLS
- ✅ Use managed database service (AWS RDS, Azure Database, etc.)
- ✅ Add monitoring and logging
- ✅ Use secrets management (Docker Secrets, HashiCorp Vault)
- ✅ Deploy on Kubernetes or Docker Swarm
- ✅ Set resource limits
- ✅ Enable health checks

---

## 📞 Need Help?

1. Check `DOCKER_DEPLOYMENT.md` for detailed guide
2. View logs: `docker-compose logs -f`
3. Check Docker status: `docker ps -a`
4. Test connectivity: `docker exec -it truepay-app curl http://localhost:8082`

---

**Ready to go? Run:**
```powershell
.\docker-start.ps1
```

