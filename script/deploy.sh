#!/bin/bash
set -e

echo "🚀 Banking API - Deploying to production..."

# Pull code mới nhất
git pull origin dev

# Build image
docker compose --env-file .env.prod build app

# Restart app không downtime
docker compose --env-file .env.prod up -d --no-deps app

# Xóa image cũ
docker image prune -f

echo "✅ Deployment done!"
docker compose --env-file .env.prod ps