pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'REPO_URL', defaultValue: 'https://github.com/Neueda-Learning/11.102-TruePay.git', description: 'Git repository URL to checkout (uses default when left unchanged).')
        string(name: 'GIT_BRANCH', defaultValue: 'main', description: 'Branch to checkout from the configured repository URL.')
        string(name: 'GIT_CREDENTIALS_ID', defaultValue: '', description: 'Optional Jenkins credentials ID for private repository access.')
    }

    environment {
        DEFAULT_GIT_URL = 'https://github.com/Neueda-Learning/11.102-TruePay.git'
        APP_PORT = '8082'
        DOCKER_ENV_FILE = '.env.ci'
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
if ! command -v java >/dev/null 2>&1; then
  echo "[ERROR] Java is not installed or not available on PATH"
  exit 1
fi
echo "[INFO] Verifying Java runtime"
java -version
echo "[INFO] JAVA_HOME before Maven stages: ${JAVA_HOME:-<unset>}"
JAVA_VERSION="$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)"
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

JAVA_BIN="$(readlink -f "$(command -v java)")"
JAVA_HOME_DETECTED="$(dirname "$(dirname "${JAVA_BIN}")")"
export JAVA_HOME="${JAVA_HOME_DETECTED}"
export PATH="${JAVA_HOME}/bin:${PATH}"

echo "[INFO] Verifying Maven runtime"
echo "[INFO] JAVA_HOME forced to: ${JAVA_HOME}"
MVN_OUTPUT="$(mvn -version)"
echo "${MVN_OUTPUT}"
MVN_JAVA_VERSION="$(echo "${MVN_OUTPUT}" | awk -F': ' '/Java version/{print $2}' | awk -F',' '{print $1}')"
if [[ "${MVN_JAVA_VERSION}" != 21* ]]; then
  echo "[ERROR] Maven is running on Java ${MVN_JAVA_VERSION}. Java 21 is required."
  exit 1
fi
'''
            }
        }

        stage('Maven Clean') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
JAVA_BIN="$(readlink -f "$(command -v java)")"
export JAVA_HOME="$(dirname "$(dirname "${JAVA_BIN}")")"
export PATH="${JAVA_HOME}/bin:${PATH}"
echo "[INFO] Running mvn clean"
mvn -B clean
'''
            }
        }

        stage('Maven Test') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
JAVA_BIN="$(readlink -f "$(command -v java)")"
export JAVA_HOME="$(dirname "$(dirname "${JAVA_BIN}")")"
export PATH="${JAVA_HOME}/bin:${PATH}"
echo "[INFO] Running mvn test"
mvn -B test
'''
            }
        }

        stage('Maven Package') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
JAVA_BIN="$(readlink -f "$(command -v java)")"
export JAVA_HOME="$(dirname "$(dirname "${JAVA_BIN}")")"
export PATH="${JAVA_HOME}/bin:${PATH}"
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

        stage('Prepare Compose Env') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail
cat > "${DOCKER_ENV_FILE}" <<EOF
SERVER_PORT=8082
DB_URL=${DB_URL}
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
MYSQL_DATABASE=truepay
MYSQL_ROOT_PASSWORD=${DB_PASSWORD}
MYSQL_USER=${DB_USER}
MYSQL_PASSWORD=${DB_PASSWORD}
EOF

echo "[INFO] Generated ${DOCKER_ENV_FILE} for compose"
sed 's/=.*/=***MASKED***/' "${DOCKER_ENV_FILE}" | sed 's/^SERVER_PORT=.*/SERVER_PORT=8082/'
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

$COMPOSE --env-file "${DOCKER_ENV_FILE}" up -d --build
$COMPOSE --env-file "${DOCKER_ENV_FILE}" ps
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
$COMPOSE --env-file "${DOCKER_ENV_FILE}" logs mysql || true
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

if docker compose version > /dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi

ACTIVE_DB="$($COMPOSE --env-file "${DOCKER_ENV_FILE}" exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -Nse "SELECT DATABASE();"' | tr -d '\r')"
SQL_USER="$($COMPOSE --env-file "${DOCKER_ENV_FILE}" exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -Nse "SELECT CURRENT_USER();"' | tr -d '\r')"
echo "[INFO] MySQL SQL session user: ${SQL_USER:-<unknown>}"
echo "[INFO] MySQL active database before USE: ${ACTIVE_DB:-<none>}"

DB_FOUND="$($COMPOSE --env-file "${DOCKER_ENV_FILE}" exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -Nse "SHOW DATABASES LIKE \\\"$MYSQL_DATABASE\\\";"' | tr -d '\r')"
if [[ "${DB_FOUND}" != "${MYSQL_DATABASE}" ]]; then
  echo "[ERROR] Expected database ${MYSQL_DATABASE} not found. Found: ${DB_FOUND:-<none>}"
  $COMPOSE --env-file "${DOCKER_ENV_FILE}" logs mysql || true
  exit 1
fi

TABLE_FOUND="$($COMPOSE --env-file "${DOCKER_ENV_FILE}" exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -Nse "USE $MYSQL_DATABASE; SHOW TABLES LIKE \\\"user_profiles\\\";"' | tr -d '\r')"
if [[ "${TABLE_FOUND}" != "user_profiles" ]]; then
  echo "[ERROR] Expected table user_profiles not found in database ${MYSQL_DATABASE}"
  $COMPOSE --env-file "${DOCKER_ENV_FILE}" logs mysql || true
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
echo "[ERROR] Application failed to start on port 8082"
if docker compose version > /dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi
$COMPOSE --env-file "${DOCKER_ENV_FILE}" logs app || true
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
if [[ -f "${DOCKER_ENV_FILE}" ]]; then
  $COMPOSE --env-file "${DOCKER_ENV_FILE}" ps || true
  echo "[INFO] Recent compose logs"
  $COMPOSE --env-file "${DOCKER_ENV_FILE}" logs --tail=150 || true
  echo "[INFO] Cleaning up docker compose resources"
  $COMPOSE --env-file "${DOCKER_ENV_FILE}" down -v --remove-orphans || true
  rm -f "${DOCKER_ENV_FILE}"
else
  echo "[WARN] ${DOCKER_ENV_FILE} not found; skipping compose diagnostics and cleanup"
fi
'''
        }
    }
}

