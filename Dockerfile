# ==============================================================================
# SurveyFlow 雲端生產部署 Dockerfile (Render / Cloud Container Root Entry)
# 責任描述: 支援以專案最外層根目錄作為 Docker Build Context 的雲端容器建置流程
# ==============================================================================

# ---------- Stage 1: Build Backend JAR ----------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# 複製 backend 目錄下的 Maven 包裝器與設定檔
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./

# 下載依賴快取
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B || true

# 複製 backend 原始碼並編譯打包
COPY backend/src src
RUN ./mvnw clean package -DskipTests

# ---------- Stage 2: Runtime Image ----------
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# 建立非 root 安全使用者
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 複製產出的 JAR
COPY --from=builder /build/target/survey-flow-backend-*.jar /app/app.jar

RUN chown -R appuser:appgroup /app

USER appuser

ENV PORT=8080
ENV TZ=Asia/Taipei
EXPOSE ${PORT}

ENTRYPOINT ["sh", "-c", "exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT} -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]
