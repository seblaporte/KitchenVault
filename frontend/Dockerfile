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
RUN printf 'server {\n\
    listen 80;\n\
    root /usr/share/nginx/html;\n\
    index index.html;\n\
    location / {\n\
        try_files $uri $uri/ /index.html;\n\
    }\n\
}\n' > /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/frontend/dist/app/browser /usr/share/nginx/html
COPY frontend/scripts/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 80
ENTRYPOINT ["/entrypoint.sh"]
