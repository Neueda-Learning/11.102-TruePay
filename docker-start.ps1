# TruePay Docker Quick Start Script (Windows PowerShell)
# Usage: .\docker-start.ps1

Write-Host "🐳 TruePay Docker Setup" -ForegroundColor Cyan
Write-Host "======================" -ForegroundColor Cyan
Write-Host ""

# Check if Docker is installed
try {
    $dockerVersion = docker --version
    Write-Host "✅ Docker found: $dockerVersion" -ForegroundColor Green
}
catch {
    Write-Host "❌ Docker is not installed. Please install Docker Desktop." -ForegroundColor Red
    Write-Host "   Download: https://www.docker.com/products/docker-desktop" -ForegroundColor Yellow
    exit 1
}

# Check if Docker Compose is installed
try {
    $composeVersion = docker-compose --version
    Write-Host "✅ Docker Compose found: $composeVersion" -ForegroundColor Green
}
catch {
    Write-Host "❌ Docker Compose is not installed." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "What would you like to do?" -ForegroundColor Cyan
Write-Host ""
Write-Host "1) Start TruePay (build + run)" -ForegroundColor White
Write-Host "2) View logs" -ForegroundColor White
Write-Host "3) Stop TruePay" -ForegroundColor White
Write-Host "4) Restart TruePay" -ForegroundColor White
Write-Host "5) Full cleanup (remove all containers & volumes)" -ForegroundColor White
Write-Host "6) View container status" -ForegroundColor White
Write-Host ""

$choice = Read-Host "Choose an option (1-6)"

switch ($choice) {
    "1" {
        Write-Host ""
        Write-Host "🚀 Building and starting TruePay..." -ForegroundColor Cyan
        docker-compose build --no-cache
        docker-compose up -d

        Write-Host ""
        Write-Host "✅ TruePay is starting!" -ForegroundColor Green
        Write-Host "   📱 Web App: http://localhost:8082" -ForegroundColor Yellow
        Write-Host "   🗄️  MySQL:  localhost:3306" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "⏳ Waiting for services to be ready..." -ForegroundColor Cyan
        Start-Sleep -Seconds 5
        docker-compose ps
        Write-Host ""
        Write-Host "📋 View logs: docker-compose logs -f app" -ForegroundColor Yellow
    }
    "2" {
        Write-Host ""
        Write-Host "📋 TruePay Application Logs" -ForegroundColor Cyan
        Write-Host "============================" -ForegroundColor Cyan
        docker-compose logs -f app
    }
    "3" {
        Write-Host ""
        Write-Host "🛑 Stopping TruePay..." -ForegroundColor Yellow
        docker-compose stop
        Write-Host "✅ TruePay stopped" -ForegroundColor Green
        docker-compose ps
    }
    "4" {
        Write-Host ""
        Write-Host "🔄 Restarting TruePay..." -ForegroundColor Cyan
        docker-compose restart
        Write-Host "✅ TruePay restarted" -ForegroundColor Green
        docker-compose ps
    }
    "5" {
        Write-Host ""
        Write-Host "⚠️  This will remove all containers, images, and volumes!" -ForegroundColor Red
        $confirm = Read-Host "Are you sure? (yes/no)"
        if ($confirm -eq "yes") {
            Write-Host "🧹 Cleaning up..." -ForegroundColor Yellow
            docker-compose down -v
            docker rmi truepay:latest 2>$null
            Write-Host "✅ Cleanup complete" -ForegroundColor Green
        }
        else {
            Write-Host "Cancelled." -ForegroundColor Yellow
        }
    }
    "6" {
        Write-Host ""
        Write-Host "📊 Container Status" -ForegroundColor Cyan
        Write-Host "===================" -ForegroundColor Cyan
        docker-compose ps
        Write-Host ""
        docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    }
    default {
        Write-Host "❌ Invalid option" -ForegroundColor Red
        exit 1
    }
}

