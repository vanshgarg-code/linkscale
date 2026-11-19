FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-jammy AS backend
WORKDIR /build/backend
COPY backend/ ./
COPY --from=frontend /build/frontend/dist/ ./src/main/resources/static/
RUN sed -i 's/\r$//' mvnw && bash mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
RUN useradd --uid 10001 --user-group --create-home linkscale
WORKDIR /app
COPY --from=backend /build/backend/target/linkscale-backend-*.jar ./app.jar
USER linkscale
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
