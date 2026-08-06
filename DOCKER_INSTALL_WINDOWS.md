# 🐳 Docker Installation & Test Guide for Windows

## Current Status: ❌ Docker NOT Installed

---

## Step 1: Install Docker Desktop for Windows

### What You Need:
- Windows 10/11 (Home, Pro, Enterprise, or Education)
- At least 4GB RAM (8GB+ recommended)
- WSL 2 (Windows Subsystem for Linux 2) support
- Admin privileges

### Installation Steps:

1. **Download Docker Desktop**
   - Visit: https://www.docker.com/products/docker-desktop
   - Click "Download for Windows"
   - Choose: **Docker Desktop for Windows (x64 installer)**

2. **Run the Installer**
   - Double-click `Docker Desktop Installer.exe`
   - Click through the setup wizard
   - When prompted, **ENABLE WSL 2** (recommended)
   - Click "Install"

3. **Complete Installation**
   - Wait for installation to finish
   - **Restart your computer** (important!)
   - Docker Desktop will start automatically after restart

4. **Verify Installation**
   - Open PowerShell
   - Run: `docker --version`
   - Should display: `Docker version XX.X.X, build XXXXX`

---

## Step 2: Enable WSL 2 (One-Time Setup)

If Docker installer didn't handle this automatically:

```powershell
# Run as Administrator:

# 1. Enable Windows Subsystem for Linux
wsl --install

# 2. Set WSL 2 as default
wsl --set-default-version 2

# 3. Restart computer
Restart-Computer
```

---

## Step 3: Test Docker Installation

After installation and restart, run these commands:

```powershell
# Test 1: Check Docker version
docker --version

# Test 2: Check Docker daemon
docker ps

# Test 3: Run hello-world
docker run hello-world

# Test 4: Check Docker system status
docker system info
```

**Expected output from `docker run hello-world`:**
```
Hello from Docker!
This message shows that your installation appears to be working correctly.
...
```

---

## Step 4: Test TruePay Docker Setup

Once Docker is working:

```powershell
# 1. Navigate to project
cd C:\Users\Administrator\IdeaProjects\11.102-TruePay

# 2. Verify Docker Compose
docker-compose --version

# 3. Check Dockerfile is valid
docker build -t truepay:test .

# 4. Start the full stack
docker-compose up -d

# 5. Check services
docker-compose ps

# 6. Test application
curl http://localhost:8082

# 7. View logs
docker-compose logs app
```

---

## Troubleshooting

### Problem: "Docker daemon is not running"
**Solution:** Start Docker Desktop from Windows Start Menu

### Problem: WSL 2 kernel update needed
```powershell
# Download and install WSL 2 kernel
# https://docs.microsoft.com/en-us/windows/wsl/install-manual#step-4---download-the-linux-kernel-update-package

# Or run:
wsl --update
```

### Problem: Insufficient resources
**Solution:** Increase Docker Desktop resources:
- Docker Desktop → Settings → Resources
- Increase CPUs and Memory allocation
- Restart Docker

### Problem: Port already in use
```powershell
# Find process using port 8082
netstat -ano | findstr :8082

# Kill process (replace PID)
taskkill /PID <PID> /F
```

---

## Installation Checklist

- [ ] Downloaded Docker Desktop installer
- [ ] Ran installer with admin privileges
- [ ] Enabled WSL 2 during installation
- [ ] Restarted computer
- [ ] Docker Desktop is running (check system tray)
- [ ] `docker --version` returns version number
- [ ] `docker run hello-world` succeeds
- [ ] Ready to test TruePay!

---

## Next Steps

After successful Docker installation:

1. Run: `.\docker-start.ps1` in PowerShell
2. Choose Option 1 (Start TruePay)
3. Wait for services to be ready (30-60 seconds first time)
4. Open browser: http://localhost:8082
5. Check logs: `docker-compose logs -f`

---

## Quick Links

- Docker Desktop: https://www.docker.com/products/docker-desktop
- Installation Guide: https://docs.docker.com/docker-for-windows/install/
- WSL 2 Setup: https://docs.microsoft.com/en-us/windows/wsl/
- Troubleshooting: https://docs.docker.com/docker-for-windows/troubleshoot/

---

## Support

If you encounter issues during installation:
1. Check Docker system logs
2. Restart Docker Desktop
3. Restart your computer
4. Check official Docker Windows documentation

