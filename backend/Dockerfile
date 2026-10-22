# ==============================================================================
# SurveyFlow 後端容器化多階段構建腳本 (Multi-stage Dockerfile)
# 階段 1: 建置環境 (Maven + Eclipse Temurin 21 JDK)
# 階段 2: 生產運行環境 (輕量非 root Eclipse Temurin 21 JRE Alpine)
# ==============================================================================

# ---------- Stage 1: Build JAR ----------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# 複製 Maven 封裝腳本與依賴設定
COPY .mvn .mvn
COPY mvnw pom.xml ./

# 預熱下載相依套件 (利用 Docker Layer Cache 提升後續建置效率)
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B || true

# 複製原始碼並編譯封裝成獨立執行檔 JAR
COPY src src
RUN ./mvnw clean package -DskipTests

# ---------- Stage 2: Runtime Image ----------
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# 建立非 root 安全使用者執行系統，遵守資安最佳實踐
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 自建置階段複製打包產出之 JAR 檔案
COPY --from=builder /build/target/survey-flow-backend-*.jar /app/app.jar

# 變更檔案擁有者權限為非 root 帳號
RUN chown -R appuser:appgroup /app

USER appuser

# Render 預設使用動態環境變數 PORT (若無則預設 8080)
ENV PORT=8080
EXPOSE ${PORT}

# 容器化 JVM 記憶體參數自動適配 (容器感測、MaxRAM 限制 75%)
ENTRYPOINT ["sh", "-c", "exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT} -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]
