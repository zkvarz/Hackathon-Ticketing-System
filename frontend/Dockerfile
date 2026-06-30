# Multi-stage build: compile the Vite SPA, then serve the static bundle with nginx.
# nginx.conf adds SPA history-API fallback and proxies /api → backend (architecture.md §4).

# ---- Stage 1: build ----
FROM node:22-alpine AS build
WORKDIR /app

# Install deps against the lockfile for reproducible builds.
COPY package.json package-lock.json* ./
RUN npm ci

# Build the static bundle.
COPY . .
RUN npm run build

# ---- Stage 2: serve ----
FROM nginx:alpine AS runtime
# SPA fallback + /api proxy.
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Static build output.
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD wget -q --spider http://localhost/ || exit 1
