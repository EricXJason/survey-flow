# ==============================================================================
# SurveyFlow 雲端生產部署 Dockerfile (Render / Cloud Container Root Entry)
# 責任描述: 支援以專案最外層根目錄作為 Docker Build Context 的雲端容器建置流程
# ==============================================================================

# ---------- Stage 1: Build Backend JAR ----------
# [Why]: 使用官方 Maven 映像 (Maven 3.9.9，與 mvnw 鎖定版本相同)，建置時不必再下載 Maven Wrapper；
#        Render 共用出口 IP 偶爾被 Maven Central 限流 (HTTP 429)，少一次下載就少一個失敗點
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# [Why]: Render 共用建置 IP 會被 Maven Central 持續限流 (HTTP 429)，改由 Google 維護之 Maven Central 鏡像下載
#        (內容與 Maven Central 相同；Render 建置機位於 Google Cloud)。只影響容器建置，本機與 CI 仍直接使用 Maven Central
RUN mkdir -p /root/.m2 && printf '%s\n' \
      '<settings>' \
      '  <mirrors>' \
      '    <mirror>' \
      '      <id>google-maven-central</id>' \
      '      <name>Google Maven Central mirror</name>' \
      '      <url>https://maven-central.storage-download.googleapis.com/maven2/</url>' \
      '      <mirrorOf>central</mirrorOf>' \
      '    </mirror>' \
      '  </mirrors>' \
      '</settings>' > /root/.m2/settings.xml

# 先只複製 pom.xml 下載相依套件，原始碼變動時可沿用此層快取
COPY backend/pom.xml ./

# [Why]: 不加「|| true」：下載失敗必須讓建置失敗，否則不完整的結果會被快取，之後每次建置都重複失敗；
#        遇到 429 等暫時性錯誤時自動重試 (每次間隔加長)
RUN for attempt in 1 2 3 4 5; do \
      mvn -B -q dependency:go-offline \
        -Daether.connector.http.retryHandler.count=5 \
        -Daether.connector.http.retryHandler.interval=5000 && exit 0; \
      echo "相依套件下載失敗，第 ${attempt} 次，$((attempt * 20)) 秒後重試"; \
      sleep $((attempt * 20)); \
    done; exit 1

# 複製後端原始碼並打包 (測試已由 CI 執行，此處略過)
COPY backend/src src
RUN mvn -B -q package -DskipTests \
      -Daether.connector.http.retryHandler.count=5 \
      -Daether.connector.http.retryHandler.interval=5000

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
