FROM eclipse-temurin:25-jdk-jammy AS build

WORKDIR /workspace
COPY . .
RUN chmod +x gradlew \
    && ./gradlew --no-daemon bootJar \
    && artifact="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "${artifact}" \
    && cp "${artifact}" /workspace/application.jar

FROM eclipse-temurin:25-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/application.jar /app/application.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
USER app

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD code="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' http://127.0.0.1:8080/)" && test "${code}" -ge 200 && test "${code}" -lt 500 || exit 1

CMD ["/bin/sh", "-ec", "test \"${SPRING_PROFILES_ACTIVE}\" = prod; test -n \"${DB_URL:-}\"; test -n \"${DB_USERNAME:-}\"; test -n \"${PROPOSAL_IDENTITY_KEY_VERSION:-}\"; test -n \"${PROPOSAL_OWNERSHIP_KEY_VERSION:-}\"; test -n \"${CREDENTIAL_DELIVERY_ENDPOINT:-}\"; test -n \"${TRUSTED_PROXY_CIDRS:-}\"; for secret in db_password throttle_fingerprint_secret proposal_identity_key_base64 proposal_ownership_key_base64 credential_delivery_bearer_token; do test -s \"/run/secrets/${secret}\"; done; export DB_PASSWORD=\"$(cat /run/secrets/db_password)\"; export THROTTLE_FINGERPRINT_SECRET=\"$(cat /run/secrets/throttle_fingerprint_secret)\"; export PROPOSAL_IDENTITY_KEY_BASE64=\"$(cat /run/secrets/proposal_identity_key_base64)\"; export PROPOSAL_OWNERSHIP_KEY_BASE64=\"$(cat /run/secrets/proposal_ownership_key_base64)\"; export CREDENTIAL_DELIVERY_BEARER_TOKEN=\"$(cat /run/secrets/credential_delivery_bearer_token)\"; exec java ${JAVA_OPTS:-} -jar /app/application.jar"]
