#!/bin/bash
# TruePay Docker Quick Start Script (Linux/macOS/WSL)

set -e

echo "🐳 TruePay Docker Setup"
echo "======================="

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker Desktop."
    exit 1
fi

echo "✅ Docker found: $(docker --version)"

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed."
    exit 1
fi

echo "✅ Docker Compose found: $(docker-compose --version)"

# Menu
echo ""
echo "What would you like to do?"
echo ""
echo "1) Start TruePay (build + run)"
echo "2) View logs"
echo "3) Stop TruePay"
echo "4) Restart TruePay"
echo "5) Full cleanup (remove all containers & volumes)"
echo "6) View container status"
echo ""
read -p "Choose an option (1-6): " choice

case $choice in
    1)
        echo ""
        echo "🚀 Building and starting TruePay..."
        docker-compose build --no-cache
        docker-compose up -d
        echo ""
        echo "✅ TruePay is starting!"
        echo "   📱 Web App: http://localhost:8082"
        echo "   🗄️  MySQL:  localhost:3306"
        echo ""
        echo "⏳ Waiting for services to be ready..."
        sleep 5
        docker-compose ps
        echo ""
        echo "📋 View logs: docker-compose logs -f app"
        ;;
    2)
        echo ""
        echo "📋 TruePay Application Logs"
        echo "============================"
        docker-compose logs -f app
        ;;
    3)
        echo ""
        echo "🛑 Stopping TruePay..."
        docker-compose stop
        echo "✅ TruePay stopped"
        docker-compose ps
        ;;
    4)
        echo ""
        echo "🔄 Restarting TruePay..."
        docker-compose restart
        echo "✅ TruePay restarted"
        docker-compose ps
        ;;
    5)
        echo ""
        echo "⚠️  This will remove all containers, images, and volumes!"
        read -p "Are you sure? (yes/no): " confirm
        if [ "$confirm" = "yes" ]; then
            echo "🧹 Cleaning up..."
            docker-compose down -v
            docker rmi truepay:latest 2>/dev/null || true
            echo "✅ Cleanup complete"
        else
            echo "Cancelled."
        fi
        ;;
    6)
        echo ""
        echo "📊 Container Status"
        echo "==================="
        docker-compose ps
        echo ""
        docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "truepay|NAMES"
        ;;
    *)
        echo "❌ Invalid option"
        exit 1
        ;;
esac

