pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
    }

    parameters {
        string(name: 'REPO_URL', defaultValue: 'https://github.com/Neueda-Learning/11.102-TruePay.git', description: 'Git repository URL to checkout (uses default when left unchanged).')
        string(name: 'GIT_BRANCH', defaultValue: 'main', description: 'Branch to checkout from the configured repository URL.')
        string(name: 'GIT_CREDENTIALS_ID', defaultValue: '', description: 'Optional Jenkins credentials ID for private repository access.')
    }

    environment {
        DEFAULT_GIT_URL = 'https://github.com/Neueda-Learning/11.102-TruePay.git'
        APP_PORT = '8082'
        DOCKER_ENV_FILE = '.env.docker'
        DOCKER_IMAGE = "truepay:${env.BUILD_NUMBER}"
    }

    stages {
        stage('Verify Jenkins Agent') {
            steps {
                script {
                    if (!isUnix()) {
                        error('This pipeline requires a Linux/Unix Jenkins agent because it uses sh/bash commands, docker compose, awk, curl, and source.')
                    }
                }
            }
        }

        stage('Checkout') {
            steps {
                script {
                    String repoUrl = params.REPO_URL?.trim() ? params.REPO_URL.trim() : env.DEFAULT_GIT_URL
                    echo "[INFO] Checking out from repository URL: ${repoUrl}"
                    if (params.GIT_CREDENTIALS_ID?.trim()) {
                        git branch: params.GIT_BRANCH, url: repoUrl, credentialsId: params.GIT_CREDENTIALS_ID
                    } else {
                        git branch: params.GIT_BRANCH, url: repoUrl
                    }
                }
            }
        }

        stage('Verify Java 21') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
if ! command -v java >/dev/null 2>&1; then
  echo "[ERROR] Java is not installed or not available on PATH"
  exit 1
fi
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

        stage('Verify Maven') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
if ! command -v mvn >/dev/null 2>&1; then
  echo "[ERROR] Maven is not installed or not available on PATH"
  exit 1
fi
echo "[INFO] Verifying Maven runtime"
mvn -version
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

        stage('Verify Docker Prerequisites') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] Docker CLI is not installed on this Jenkins agent"
  exit 1
fi

echo "[INFO] Docker CLI version"
docker --version

if ! docker compose version >/dev/null 2>&1; then
  echo "[ERROR] Docker Compose plugin is not available (expected: docker compose ...)"
  exit 1
fi

echo "[INFO] Docker Compose version"
docker compose version

if ! docker info >/dev/null 2>&1; then
  echo "[ERROR] Docker daemon is not reachable for this Jenkins user"
  exit 1
fi

echo "[INFO] Docker daemon is reachable"
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

        stage('Validate Docker Env File') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
if [[ ! -f "${DOCKER_ENV_FILE}" ]]; then
  echo "[ERROR] ${DOCKER_ENV_FILE} is missing"
  exit 1
fi

set -a
source "${DOCKER_ENV_FILE}"
set +a

required_vars=(MYSQL_DATABASE MYSQL_ROOT_PASSWORD MYSQL_USER MYSQL_PASSWORD DB_URL DB_USER DB_PASSWORD SERVER_PORT)
for key in "${required_vars[@]}"; do
  if [[ -z "${!key:-}" ]]; then
    echo "[ERROR] Missing required env var in ${DOCKER_ENV_FILE}: ${key}"
    exit 1
  fi
done

if [[ "${DB_URL}" != *"/${MYSQL_DATABASE}"* ]]; then
  echo "[ERROR] DB_URL (${DB_URL}) does not target MYSQL_DATABASE (${MYSQL_DATABASE})"
  exit 1
fi

echo "[INFO] Env validated: MYSQL_DATABASE=${MYSQL_DATABASE}, DB_USER=${DB_USER}, SERVER_PORT=${SERVER_PORT}"
'''
            }
        }

        stage('Start with Docker Compose') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
set -a
source "${DOCKER_ENV_FILE}"
set +a
echo "[INFO] Starting services with docker compose on port ${SERVER_PORT}"
docker compose --env-file "${DOCKER_ENV_FILE}" up -d --build
docker compose --env-file "${DOCKER_ENV_FILE}" ps
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
docker compose --env-file "${DOCKER_ENV_FILE}" logs mysql || true
exit 1
'''
            }
        }

        stage('Verify MySQL Schema') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
set -a
source "${DOCKER_ENV_FILE}"
set +a

ACTIVE_DB="$(docker compose --env-file "${DOCKER_ENV_FILE}" exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -Nse "SELECT DATABASE();"' | tr -d '\r')"
SQL_USER="$(docker compose --env-file "${DOCKER_ENV_FILE}" exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -Nse "SELECT CURRENT_USER();"' | tr -d '\r')"
echo "[INFO] MySQL SQL session user: ${SQL_USER:-<unknown>}"
echo "[INFO] MySQL active database before USE: ${ACTIVE_DB:-<none>}"

DB_FOUND="$(docker compose --env-file "${DOCKER_ENV_FILE}" exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -Nse "SHOW DATABASES LIKE \\\"$MYSQL_DATABASE\\\";"' | tr -d '\r')"
if [[ "${DB_FOUND}" != "${MYSQL_DATABASE}" ]]; then
  echo "[ERROR] Expected database ${MYSQL_DATABASE} not found. Found: ${DB_FOUND:-<none>}"
  docker compose --env-file "${DOCKER_ENV_FILE}" logs mysql || true
  exit 1
fi

TABLE_FOUND="$(docker compose --env-file "${DOCKER_ENV_FILE}" exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -Nse "USE $MYSQL_DATABASE; SHOW TABLES LIKE \\\"user_profiles\\\";"' | tr -d '\r')"
if [[ "${TABLE_FOUND}" != "user_profiles" ]]; then
  echo "[ERROR] Expected table user_profiles not found in database ${MYSQL_DATABASE}"
  docker compose --env-file "${DOCKER_ENV_FILE}" logs mysql || true
  exit 1
fi

echo "[INFO] MySQL schema verified in database ${MYSQL_DATABASE}"
'''
            }
        }

        stage('Verify App') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
set -a
source "${DOCKER_ENV_FILE}"
set +a
APP_PORT="${SERVER_PORT:-8082}"
echo "[INFO] Verifying Spring Boot app on port ${APP_PORT}"
for i in $(seq 1 60); do
  if curl -fsS "http://localhost:${APP_PORT}/" > /dev/null; then
    echo "[INFO] Application is reachable on port ${APP_PORT}"
    exit 0
  fi
  sleep 5
done
echo "[ERROR] Application failed to start on port ${APP_PORT}"
docker compose --env-file "${DOCKER_ENV_FILE}" logs app || true
exit 1
'''
            }
        }
    }

    post {
        always {
            sh '''#!/usr/bin/env bash
set +e
echo "[INFO] Docker compose service status"
docker compose --env-file "${DOCKER_ENV_FILE}" ps
echo "[INFO] Recent compose logs"
docker compose --env-file "${DOCKER_ENV_FILE}" logs --tail=150
echo "[INFO] Cleaning up docker compose resources"
docker compose --env-file "${DOCKER_ENV_FILE}" down -v --remove-orphans
'''
        }
    }
}

