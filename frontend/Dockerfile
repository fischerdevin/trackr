FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html vite.config.js ./
COPY src src
COPY public public
RUN npm test && npm run build

FROM caddy:2-alpine
RUN addgroup -S -g 10001 trackr \
    && adduser -S -D -H -u 10001 -G trackr trackr \
    && mkdir -p /data/caddy /config/caddy \
    && chown -R trackr:trackr /data /config
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /build/dist /srv
USER 10001:10001
EXPOSE 8080 8443
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8081/healthz || exit 1
