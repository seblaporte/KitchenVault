# Stage 1: build Angular app
FROM node:22-alpine AS builder
RUN apk add --no-cache openjdk21-jre-headless
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY contracts/src/main/resources/openapi/api.yaml /workspace/contracts/src/main/resources/openapi/api.yaml
COPY frontend/ ./
RUN npm run generate:api
RUN npm run build

# Stage 2: serve with nginx
FROM nginx:alpine
COPY frontend/nginx.conf.template /etc/nginx/conf.d/default.conf.template
COPY --from=builder /workspace/frontend/dist/app/browser /usr/share/nginx/html
COPY frontend/scripts/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 80
ENTRYPOINT ["/entrypoint.sh"]
