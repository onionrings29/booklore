# syntax=docker/dockerfile:1.7

########################
# Stage 1: Build UI
########################
FROM node:22 AS ui-build
WORKDIR /workspace

COPY booklore-ui/package*.json ./booklore-ui/
RUN cd booklore-ui && npm ci --force

COPY booklore-ui ./booklore-ui
RUN cd booklore-ui && npm run build --configuration=production

########################
# Stage 2: Build API
########################
FROM eclipse-temurin:25-jdk AS api-build
WORKDIR /workspace/booklore-api

COPY booklore-api/gradlew ./gradlew
COPY booklore-api/gradle ./gradle
COPY booklore-api/settings.gradle ./settings.gradle
COPY booklore-api/build.gradle ./build.gradle
COPY booklore-api/src ./src

RUN chmod +x ./gradlew
RUN ./gradlew clean bootJar -x test --no-daemon


########################
# Stage 3: Runtime
########################
FROM eclipse-temurin:25-jre AS runtime

ARG APP_VERSION
ARG APP_REVISION

# Set OCI labels
LABEL org.opencontainers.image.title="BookLore" \
      org.opencontainers.image.description="BookLore: A self-hosted, multi-user digital library with smart shelves, auto metadata, Kobo & KOReader sync, BookDrop imports, OPDS support, and a built-in reader for EPUB, PDF, and comics." \
      org.opencontainers.image.source="https://github.com/booklore-app/booklore" \
      org.opencontainers.image.url="https://github.com/booklore-app/booklore" \
      org.opencontainers.image.documentation="https://booklore-app.github.io/booklore-docs/docs/getting-started" \
      org.opencontainers.image.version=$APP_VERSION \
      org.opencontainers.image.revision=$APP_REVISION \
      org.opencontainers.image.licenses="GPL-3.0" \
      org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:25-jre"

ENV BOOKLORE_PORT=6060
WORKDIR /opt/booklore

RUN apt-get update \
    && apt-get install -y --no-install-recommends nginx gettext-base \
    && rm -rf /var/lib/apt/lists/*

COPY --from=api-build /workspace/booklore-api/build/libs/booklore-api-*.jar /opt/booklore/app.jar
COPY --from=ui-build /workspace/booklore-ui/dist/booklore/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf.template
COPY docker/entrypoint.sh /entrypoint.sh

RUN chmod +x /entrypoint.sh

EXPOSE ${BOOKLORE_PORT}
ENTRYPOINT ["/entrypoint.sh"]
