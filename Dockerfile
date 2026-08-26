# 주식 자동매매 백엔드 Dockerfile (Spring Boot)
# Stock auto-trading backend Dockerfile (Spring Boot)
# 멀티스테이지 빌드: 빌드 산출물만 런타임 이미지에 포함 (Multi-stage build: only the build output ships in the runtime image)

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY backend/ .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# 시크릿(앱키/시크릿키/계좌번호)은 이미지에 절대 포함하지 않고 런타임 환경변수로만 주입
# Secrets (app key/secret/account number) are never baked into the image — injected only via runtime env vars
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
