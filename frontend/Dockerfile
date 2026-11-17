# Matriz frontend image (Worksheet 05): the React build served by nginx, which also forwards /api to the
# backend, so the browser sees one origin (session and CSRF cookies, as with the Vite proxy in development).

# --- Build stage: Node LTS ---
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# VITE_API_URL is read at build time; the default /api goes through nginx (nginx.conf)
RUN npm run build

# --- Runtime stage: nginx without root (listens on 8080) ---
FROM nginxinc/nginx-unprivileged:alpine
# Security fixes of the Alpine packages newer than the base image (Trivy, Worksheet 05, Ex. 4)
USER root
RUN apk upgrade --no-cache
USER nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.inc /etc/nginx/conf.d/security-headers.inc
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
