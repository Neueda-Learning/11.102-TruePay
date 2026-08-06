# Docker Installation & System Diagnostics Script (Windows PowerShell)
# Usage: .\docker-verify.ps1
# This script checks if Docker is properly installed and configured

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║         🐳 Docker Installation & System Diagnostics        ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Counter for tests
$testsRun = 0
$testsPassed = 0

function Test-Item {
    param(
        [string]$Name,
        [scriptblock]$Test,
        [string]$FailMessage = "Failed"
    )

    $script:testsRun++
    Write-Host "[$testsRun] Testing: $Name..." -ForegroundColor Yellow -NoNewline

    try {
        $result = & $Test
        $script:testsPassed++
        Write-Host " ✅ PASS" -ForegroundColor Green
        if ($result) {
            Write-Host "    📌 $result" -ForegroundColor Cyan
        }
        return $true
    }
    catch {
        Write-Host " ❌ FAIL" -ForegroundColor Red
        Write-Host "    ❌ Error: $FailMessage" -ForegroundColor Red
        Write-Host "    💡 Details: $($_.Exception.Message)" -ForegroundColor Yellow
        return $false
    }
}

# Test Suite
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White
Write-Host "PART 1: Docker Installation" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White

Test-Item "Docker command exists" {
    $version = docker --version
    $version
}

Test-Item "Docker daemon is running" {
    docker ps > $null
    "Docker daemon is responsive"
}

Test-Item "Docker version" {
    docker version --format 'Client: {{.Client.Version}}, Server: {{.Server.Version}}'
}

Test-Item "Docker Compose installed" {
    docker-compose --version
}

Test-Item "WSL 2 status" {
    $wslVersion = wsl --version
    $wslVersion
} "WSL 2 may not be installed"

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White
Write-Host "PART 2: Docker Functionality" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White

Test-Item "Docker info retrieval" {
    $info = docker info --format '{{.Containers}} containers, {{.Images}} images'
    $info
}

Test-Item "Pull alpine image" {
    docker pull alpine:latest 2>&1 | Select-Object -Last 1
} "Could not download image"

Test-Item "Run hello-world container" {
    docker run --rm hello-world > $null
    "Container ran successfully"
}

Test-Item "Image list retrieval" {
    $images = docker images --format "table" | Measure-Object -Line
    "$(($images.Lines - 1)) images available"
}

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White
Write-Host "PART 3: TruePay Project Detection" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White

Test-Item "Project directory exists" {
    $projPath = "C:\Users\Administrator\IdeaProjects\11.102-TruePay"
    if (Test-Path $projPath) {
        "Found at $projPath"
    } else {
        throw "Directory not found"
    }
}

Test-Item "Dockerfile present" {
    if (Test-Path "Dockerfile") {
        "Dockerfile found"
    } else {
        throw "Dockerfile not found in current directory"
    }
}

Test-Item "docker-compose.yml present" {
    if (Test-Path "docker-compose.yml") {
        "docker-compose.yml found"
    } else {
        throw "docker-compose.yml not found"
    }
}

Test-Item "src/main/resources/static exists" {
    if (Test-Path "src/main/resources/static") {
        $files = Get-ChildItem "src/main/resources/static" | Measure-Object
        "$($files.Count) files in static directory"
    } else {
        throw "Static directory not found"
    }
}

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White
Write-Host "PART 4: System Resources" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White

# CPU info
$cpuCount = (Get-WmiObject Win32_ComputerSystem).NumberOfLogicalProcessors
Write-Host "[*] CPU Cores: $cpuCount" -ForegroundColor Cyan

# RAM info
$totalRAM = [math]::Round((Get-WmiObject Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 2)
Write-Host "[*] Total RAM: ${totalRAM}GB" -ForegroundColor Cyan

# Disk space
$diskFree = [math]::Round((Get-Volume).SizeRemaining / 1GB, 2)
Write-Host "[*] Disk Free: ~$diskFree GB" -ForegroundColor Cyan

# OS info
$osInfo = Get-WmiObject Win32_OperatingSystem
$osVersion = $osInfo.Caption
Write-Host "[*] OS: $osVersion" -ForegroundColor Cyan

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White
Write-Host "SUMMARY" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White

$percentage = if ($testsRun -gt 0) { [math]::Round(($testsPassed / $testsRun) * 100) } else { 0 }

Write-Host ""
Write-Host "Tests Passed: $testsPassed / $testsRun ($percentage%)" -ForegroundColor $(if ($testsPassed -eq $testsRun) { "Green" } else { "Yellow" })

if ($testsPassed -eq $testsRun) {
    Write-Host ""
    Write-Host "✅ All tests PASSED! Docker is ready." -ForegroundColor Green
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Green
    Write-Host "  1. Run: .\docker-start.ps1" -ForegroundColor Yellow
    Write-Host "  2. Choose Option 1: Start TruePay" -ForegroundColor Yellow
    Write-Host "  3. Open: http://localhost:8082" -ForegroundColor Yellow
    Write-Host ""
    exit 0
}
elseif ($testsPassed -ge ($testsRun * 0.75)) {
    Write-Host ""
    Write-Host "⚠️  Most tests passed, but some issues detected." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Troubleshooting:" -ForegroundColor Yellow
    Write-Host "  1. Ensure Docker Desktop is running" -ForegroundColor Gray
    Write-Host "  2. Check Windows system updates" -ForegroundColor Gray
    Write-Host "  3. Restart Docker Desktop" -ForegroundColor Gray
    Write-Host "  4. See DOCKER_INSTALL_WINDOWS.md for detailed help" -ForegroundColor Gray
    Write-Host ""
    exit 1
}
else {
    Write-Host ""
    Write-Host "❌ Docker is NOT properly installed or configured." -ForegroundColor Red
    Write-Host ""
    Write-Host "Installation Required:" -ForegroundColor Red
    Write-Host "  1. Download: https://www.docker.com/products/docker-desktop" -ForegroundColor Yellow
    Write-Host "  2. Run installer (enable WSL 2)" -ForegroundColor Yellow
    Write-Host "  3. Restart your computer" -ForegroundColor Yellow
    Write-Host "  4. Run this script again" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "See DOCKER_INSTALL_WINDOWS.md for detailed instructions" -ForegroundColor Cyan
    Write-Host ""
    exit 1
}

