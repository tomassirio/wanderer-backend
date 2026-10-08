#!/usr/bin/env bash
# Helm-deploys one backend service. Run by one parallel matrix job per service in
# .github/workflows/helm-deploy.yml, so each service deploys (and fails) on its own.
#
# Usage: deploy-service.sh SERVICE
# Env: ENVIRONMENT, IMAGE_TAG, NAMESPACE, ENV_SUFFIX, PUBLIC_URL, JWT_SECRET, DB_PASSWORD,
#      plus the optional per-service values read below.
set -euo pipefail

SERVICE=$1
case "$SERVICE" in
  wanderer-auth) PORT=8083 DB=wanderer_auth_db ;;
  wanderer-command) PORT=8081 DB=wanderer_db ;;
  wanderer-query) PORT=8082 DB=wanderer_db ;;
  *) echo "::error::Unknown service: $SERVICE"; exit 1 ;;
esac
EMAIL_SMTP_PWD=${EMAIL_SMTP_PWD:-}
DB_URL="jdbc:postgresql://postgres${ENV_SUFFIX}:5432/$DB"

# Chart.yaml version
if [ "$ENVIRONMENT" == "dev" ] || [ "$IMAGE_TAG" == "latest" ]; then
  VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
  VERSION="${VERSION%-SNAPSHOT}"
else
  VERSION="${IMAGE_TAG#v}"
fi
CHART_FILE="./$SERVICE/src/main/chart/Chart.yaml"
if [ -f "$CHART_FILE" ]; then
  sed -i "s/^version: .*/version: $VERSION/" "$CHART_FILE"
  sed -i "s/^appVersion: .*/appVersion: \"$VERSION\"/" "$CHART_FILE"
fi

# Environment-specific CORS configuration
if [ "$ENVIRONMENT" == "dev" ]; then
  # Dev: Allow localhost, local domain, and public URL if set
  CORS_ORIGINS="http://localhost:51538,http://localhost:3000,https://wanderer.localwanderer-dev.com,http://wanderer.localwanderer-dev.com"
  if [ -n "${PUBLIC_URL:-}" ]; then
    CORS_ORIGINS="$PUBLIC_URL,$CORS_ORIGINS"
  fi
else
  # Prod: Only public URL
  CORS_ORIGINS="${PUBLIC_URL:-}"
fi

# Environment-specific configuration
if [ "$ENVIRONMENT" == "dev" ]; then
  REPLICA_COUNT="1" CPU_LIMIT="500m" MEMORY_LIMIT="512Mi" CPU_REQUEST="250m" MEMORY_REQUEST="256Mi"
  LOG_LEVEL_APP="DEBUG" LOG_LEVEL_ROOT="INFO" PULL_POLICY="Always"
else
  REPLICA_COUNT="3" CPU_LIMIT="1000m" MEMORY_LIMIT="1Gi" CPU_REQUEST="500m" MEMORY_REQUEST="512Mi"
  LOG_LEVEL_APP="INFO" LOG_LEVEL_ROOT="WARN" PULL_POLICY="IfNotPresent"
fi

HELM_ARGS=(
  "upgrade" "--install" "$SERVICE" "./$SERVICE/src/main/chart"
  "--namespace" "$NAMESPACE" "--create-namespace"
  "--set" "environment.name=$ENVIRONMENT"
  "--set" "environment.suffix=$ENV_SUFFIX"
  "--set" "replicaCount=$REPLICA_COUNT"
  "--set" "service.port=$PORT"
  "--set" "image.tag=$IMAGE_TAG"
  "--set" "image.pullPolicy=$PULL_POLICY"
  "--set" "resources.limits.cpu=$CPU_LIMIT"
  "--set" "resources.limits.memory=$MEMORY_LIMIT"
  "--set" "resources.requests.cpu=$CPU_REQUEST"
  "--set" "resources.requests.memory=$MEMORY_REQUEST"
  "--set" "application.logging.level.root=$LOG_LEVEL_ROOT"
  "--set" "application.logging.level.${SERVICE#wanderer-}=$LOG_LEVEL_APP"
  "--set" "application.jwt.secret=$JWT_SECRET"
  "--set" "application.database.password=$DB_PASSWORD"
  "--set" "application.database.url=$DB_URL"
  # Escape commas for Helm --set
  "--set" "application.cors.allowedOrigins=${CORS_ORIGINS//,/\\,}"
  "--set" "ingress.enabled=false"
)

case "$SERVICE" in
  wanderer-auth)
    HELM_ARGS+=(
      "--set" "application.wandererCommandUrl=http://wanderer-command${ENV_SUFFIX}:8081"
      "--set" "application.wandererQueryUrl=http://wanderer-query${ENV_SUFFIX}:8082"
    )
    # Bootstrap admin configuration from GitHub environment variables
    if [ -n "${BOOTSTRAP_ADMIN_USERNAME:-}" ]; then
      HELM_ARGS+=("--set" "application.bootstrap.adminUsername=$BOOTSTRAP_ADMIN_USERNAME")
    fi
    if [ -n "${BOOTSTRAP_ADMIN_ENABLED:-}" ]; then
      HELM_ARGS+=("--set" "application.bootstrap.adminEnabled=$BOOTSTRAP_ADMIN_ENABLED")
    fi
    # Email configuration from GitHub environment variables and secrets
    if [ -n "${EMAIL_ENABLED:-}" ]; then
      HELM_ARGS+=("--set" "application.email.enabled=$EMAIL_ENABLED")
    fi
    # Logs the length only, never the value
    echo "EMAIL_PASSWORD length: ${#EMAIL_SMTP_PWD}"
    if [ -z "${EMAIL_SMTP_PWD:-}" ]; then
      echo "::warning::EMAIL_PASSWORD secret is empty — email authentication will fail"
    fi
    HELM_ARGS+=("--set-string" "application.email.password=${EMAIL_SMTP_PWD:-}")
    if [ -n "${EMAIL_BASE_URL:-}" ]; then
      HELM_ARGS+=("--set" "application.email.baseUrl=$EMAIL_BASE_URL")
    fi
    # SSO (Google) from GitHub environment variables and secrets
    HELM_ARGS+=(
      "--set-string" "application.sso.google.clientId=${GOOGLE_CLIENT_ID:-}"
      "--set-string" "application.sso.google.clientSecret=${GOOGLE_CLIENT_SECRET:-}"
    )
    if [ -n "${SSO_ALLOWED_RETURN_URIS:-}" ]; then
      HELM_ARGS+=("--set-string" "application.sso.allowedReturnUris=${SSO_ALLOWED_RETURN_URIS//,/\\,}")
    fi
    ;;
  wanderer-command)
    if [ -n "${RELEASE_CI_TOKEN:-}" ]; then
      HELM_ARGS+=("--set-string" "application.release.ciToken=$RELEASE_CI_TOKEN")
    fi
    if [ -n "${GOOGLE_MAPS_API_KEY:-}" ]; then
      HELM_ARGS+=("--set" "application.googleMaps.apiKey=$GOOGLE_MAPS_API_KEY")
      # Thumbnails are served from PUBLIC_URL (set per environment in GitHub)
      if [ "$ENVIRONMENT" == "dev" ]; then
        THUMBNAIL_STORAGE_SIZE="5Gi" THUMBNAIL_NGINX_REPLICAS="1"
      else
        THUMBNAIL_STORAGE_SIZE="20Gi" THUMBNAIL_NGINX_REPLICAS="2"
      fi
      HELM_ARGS+=(
        "--set" "application.thumbnail.baseUrl=${PUBLIC_URL:-}/thumbnails"
        "--set" "application.thumbnail.enabled=true"
        "--set" "thumbnail.enabled=true"
        "--set" "thumbnail.storage.useExistingClaim=true"
        "--set" "thumbnail.storage.size=$THUMBNAIL_STORAGE_SIZE"
        "--set" "thumbnail.nginx.replicas=$THUMBNAIL_NGINX_REPLICAS"
      )
    fi
    ;;
esac

if [ "$SERVICE" == "wanderer-command" ] || [ "$SERVICE" == "wanderer-query" ]; then
  HELM_ARGS+=("--set" "application.wanderer.authUrl=http://wanderer-auth${ENV_SUFFIX}:8083")
fi

if ! helm "${HELM_ARGS[@]}" --wait --timeout 5m; then
  echo "::error::$SERVICE failed to deploy"
  kubectl logs -l "app=$SERVICE$ENV_SUFFIX" -n "$NAMESPACE" --tail=50 || true
  exit 1
fi
echo "✓ $SERVICE deployed successfully"

kubectl wait --for=condition=ready pod -l "app=$SERVICE$ENV_SUFFIX" -n "$NAMESPACE" --timeout=120s || true
