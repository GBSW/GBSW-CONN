FROM node:26.7.0-bookworm-slim AS dependencies

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm install --global npm@11.6.2 \
    && npm ci

FROM dependencies AS build

ARG BACKEND_INTERNAL_URL=http://backend:8080
ENV BACKEND_INTERNAL_URL=${BACKEND_INTERNAL_URL}
COPY . .
RUN npm run build \
    && npm prune --omit=dev

FROM node:26.7.0-bookworm-slim AS runtime

WORKDIR /app
ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

COPY --from=build --chown=node:node /app/package.json /app/package.json
COPY --from=build --chown=node:node /app/next.config.ts /app/next.config.ts
COPY --from=build --chown=node:node /app/.next /app/.next
COPY --from=build --chown=node:node /app/node_modules /app/node_modules

EXPOSE 3000
USER node

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:3000/').then(r => process.exit(r.status < 500 ? 0 : 1)).catch(() => process.exit(1))"

CMD ["sh", "-ec", "test -n \"${BACKEND_INTERNAL_URL:-}\"; exec node node_modules/next/dist/bin/next start --hostname 0.0.0.0 --port 3000"]
