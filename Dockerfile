# ---------- Build stage ----------
FROM maven:3-amazoncorretto-21 AS build
WORKDIR /workspace

# GitHub Packages credentials (injected via build args/env during CI/CD)
ARG GH_ACTOR
ARG GH_PACKAGES_TOKEN
ARG MAVEN_ACTIVE_PROFILES=prod
ENV MAVEN_SETTINGS_PATH=/tmp/maven-settings.xml
ENV SPRING_PROFILES_ACTIVE=${MAVEN_ACTIVE_PROFILES}

# Configure Maven to authenticate against the private GitHub repositories
RUN printf '%s\n' \
    '<settings>' \
    '  <servers>' \
    '    <server>' \
    '      <id>github-nimbly-notification</id>' \
    "      <username>${GH_ACTOR}</username>" \
    "      <password>${GH_PACKAGES_TOKEN}</password>" \
    '    </server>' \
    '    <server>' \
    '      <id>github-nimbly-commons</id>' \
    "      <username>${GH_ACTOR}</username>" \
    "      <password>${GH_PACKAGES_TOKEN}</password>" \
    '    </server>' \
    '    <server>' \
    '      <id>github-nimbly-useraccounts</id>' \
    "      <username>${GH_ACTOR}</username>" \
    "      <password>${GH_PACKAGES_TOKEN}</password>" \
    '    </server>' \
    '  </servers>' \
    '</settings>' \
    > ${MAVEN_SETTINGS_PATH}

# Cache dependencies first
COPY pom.xml .
COPY ph-shoes-alerts-service-core/pom.xml ph-shoes-alerts-service-core/pom.xml
COPY ph-shoes-alerts-service-web/pom.xml ph-shoes-alerts-service-web/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -s ${MAVEN_SETTINGS_PATH} -q -e -U -P${MAVEN_ACTIVE_PROFILES} -DskipTests dependency:go-offline

# Build
COPY ph-shoes-alerts-service-core ./ph-shoes-alerts-service-core
COPY ph-shoes-alerts-service-web ./ph-shoes-alerts-service-web
COPY docs ./docs
RUN --mount=type=cache,target=/root/.m2 mvn -s ${MAVEN_SETTINGS_PATH} -q -P${MAVEN_ACTIVE_PROFILES} -DskipTests package

# ---------- Runtime stage ----------
FROM amazoncorretto:21-alpine AS runtime
ENV APP_HOME=/app \
    JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -XX:MaxRAMPercentage=75" \
    SPRING_PROFILES_ACTIVE=prod \
    PORT=8084

RUN addgroup -S spring && adduser -S spring -G spring
RUN apk add --no-cache curl

USER spring:spring
WORKDIR ${APP_HOME}

COPY --from=build /workspace/ph-shoes-alerts-service-web/target/*.jar app.jar

EXPOSE ${PORT}

ENTRYPOINT ["java","-jar","/app/app.jar"]
