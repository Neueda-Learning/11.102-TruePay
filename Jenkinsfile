pipeline {
    agent any

    tools {
        jdk 'jdk21'
        maven 'maven3'
    }

    options {
        skipDefaultCheckout(true)
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'REPO_URL', defaultValue: 'https://github.com/Neueda-Learning/11.102-TruePay.git', description: 'Optional Git repository URL. Leave empty to use Jenkins job SCM.')
        string(name: 'GIT_BRANCH', defaultValue: 'main', description: 'Branch to checkout when REPO_URL is provided.')
        string(name: 'GIT_CREDENTIALS_ID', defaultValue: '', description: 'Optional Jenkins credentials ID for private repository access.')
    }

    environment {
        DB_URL = 'jdbc:mysql://mysql:3306/truepay'
        DB_USER = 'truepay'
        DB_PASSWORD = 'n3u3da!'
        DOCKER_IMAGE = "truepay:${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    if (params.REPO_URL?.trim()) {
                        echo "[INFO] Checking out from explicit repository URL: ${params.REPO_URL}"
                        if (params.GIT_CREDENTIALS_ID?.trim()) {
                            git branch: params.GIT_BRANCH, url: params.REPO_URL, credentialsId: params.GIT_CREDENTIALS_ID
                        } else {
                            git branch: params.GIT_BRANCH, url: params.REPO_URL
                        }
                    } else {
                        echo "[INFO] REPO_URL not provided. Falling back to Jenkins job SCM configuration."
                        checkout scm
                    }
                }
            }
        }

        stage('Docker Preflight') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
echo "[INFO] Verifying Docker CLI and daemon"
docker --version
docker info > /dev/null

if docker compose version > /dev/null 2>&1; then
  echo "[INFO] Using docker compose plugin"
  docker compose version
elif command -v docker-compose > /dev/null 2>&1; then
  echo "[INFO] Using legacy docker-compose binary"
  docker-compose --version
else
  echo "[ERROR] Neither 'docker compose' nor 'docker-compose' is available"
  exit 1
fi
'''
            }
        }

        stage('Verify Java 21') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
echo "[INFO] Verifying Java runtime"
java -version
JAVA_VERSION="$(java -version 2>&1 | awk -F\" '/version/ {print $2}')"
if [[ "${JAVA_VERSION}" != 21* ]]; then
  echo "[ERROR] Java 21 is required, found: ${JAVA_VERSION}"
  exit 1
fi
echo "[INFO] Java version OK: ${JAVA_VERSION}"
'''
            }
        }

        stage('Maven Clean') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
echo "[INFO] Running mvn clean"
mvn -B clean
'''
            }
        }

        stage('Maven Test') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
echo "[INFO] Running mvn test"
mvn -B test
'''
            }
        }

        stage('Maven Package') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
echo "[INFO] Running mvn package -DskipTests"
mvn -B package -DskipTests
'''
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
echo "[INFO] Building Docker image ${DOCKER_IMAGE}"
docker build -t "${DOCKER_IMAGE}" .
'''
            }
        }

        stage('Prepare Compose Env') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
cat > .env.ci <<EOF
SERVER_PORT=8082
DB_URL=${DB_URL}
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
MYSQL_DATABASE=truepay
MYSQL_ROOT_PASSWORD=${DB_PASSWORD}
MYSQL_USER=${DB_USER}
MYSQL_PASSWORD=${DB_PASSWORD}
EOF

echo "[INFO] Generated .env.ci for compose"
sed 's/=.*/=***MASKED***/' .env.ci | sed 's/^SERVER_PORT=.*/SERVER_PORT=8082/'
'''
            }
        }

        stage('Start with Docker Compose') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
echo "[INFO] Starting services with docker compose"
if docker compose version > /dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi

$COMPOSE --env-file .env.ci up -d --build
$COMPOSE --env-file .env.ci ps
'''
            }
        }

        stage('Wait for MySQL Health') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
echo "[INFO] Waiting for MySQL health status"
for i in $(seq 1 60); do
  STATUS="$(docker inspect --format='{{.State.Health.Status}}' truepay-mysql 2>/dev/null || echo 'missing')"
  echo "[INFO] mysql health: ${STATUS}"
  if [[ "${STATUS}" == "healthy" ]]; then
    exit 0
  fi
  sleep 5
done
echo "[ERROR] MySQL did not become healthy in time"
if docker compose version > /dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi
$COMPOSE --env-file .env.ci logs mysql || true
exit 1
'''
            }
        }

        stage('Verify App on 8082') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
echo "[INFO] Verifying Spring Boot app on port 8082"
for i in $(seq 1 60); do
  if curl -fsS "http://localhost:8082/" > /dev/null; then
    echo "[INFO] Application is reachable on port 8082"
    exit 0
  fi
  sleep 5
done
echo "[ERROR] Application failed to start on port 8082"
if docker compose version > /dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi
$COMPOSE --env-file .env.ci logs app || true
exit 1
'''
            }
        }
    }

    post {
        always {
            sh '''#!/usr/bin/env bash
set +e
if docker compose version > /dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi

echo "[INFO] Docker compose service status"
$COMPOSE --env-file .env.ci ps
echo "[INFO] Recent compose logs"
$COMPOSE --env-file .env.ci logs --tail=150
echo "[INFO] Cleaning up docker compose resources"
$COMPOSE --env-file .env.ci down -v --remove-orphans
rm -f .env.ci
'''
        }
    }
}

