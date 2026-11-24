#!/usr/bin/env bash
# ==============================================================================
# 檔案名稱: start-e2e-backend.sh
# 責任描述: 端到端 (E2E) 測試專用後端環境：
#   1. 啟動全新的 MySQL 8 容器 (13306) 與 Mailpit 收信容器 (SMTP 11025 / API 18025)
#   2. 等待 MySQL 可由 TCP 連線
#   3. 打包後端 JAR，以 18080 埠啟動，啟動時執行 schema.sql + seed/01~05 載入種子資料
# 執行時機: 由 playwright.config.ts 之 webServer 自動呼叫 (pnpm e2e)；也可手動執行以除錯
# 覆寫方式: 下方「E2E 專用連接埠」可用同名環境變數覆寫；需要與 e2e/support/env.ts 一致
# 邊界: 只使用 Docker 容器與隨機產生之密碼 / 金鑰，不會連線本機或雲端資料庫，也不會寄出真實信件
# ==============================================================================
set -euo pipefail

FRONTEND_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND_DIR="$(cd "${FRONTEND_DIR}/../backend" && pwd)"

# ------------------------------------------------------------------------------
# 【1. E2E 專用連接埠與容器名稱】
# 刻意避開日常開發的 3306 / 8080 / 4200，E2E 可與本機開發環境同時執行。
#   E2E_DB_PORT        MySQL 容器對外埠
#   E2E_SMTP_PORT      Mailpit 收信 SMTP 埠 (後端寄信目標)
#   E2E_MAILPIT_PORT   Mailpit HTTP API 埠 (測試讀取驗證碼)
#   E2E_API_PORT       後端 API 埠
#   E2E_WEB_PORT       前端開發伺服器埠 (後端 CORS 白名單與信件連結使用)
# ------------------------------------------------------------------------------
E2E_DB_PORT="${E2E_DB_PORT:-13306}"
E2E_SMTP_PORT="${E2E_SMTP_PORT:-11025}"
E2E_MAILPIT_PORT="${E2E_MAILPIT_PORT:-18025}"
E2E_API_PORT="${E2E_API_PORT:-18080}"
E2E_WEB_PORT="${E2E_WEB_PORT:-4300}"
DB_CONTAINER="surveyflow-e2e-mysql"
MAIL_CONTAINER="surveyflow-e2e-mailpit"

# [Why]: 密碼與 JWT 金鑰每次隨機產生且只存在於本次程序，檔案中不寫死任何機敏值
DB_PASSWORD="$(openssl rand -hex 16)"
JWT_SECRET_VALUE="$(openssl rand -base64 48)"
AUDIT_HMAC_SECRET_VALUE="$(openssl rand -base64 48)"

# ------------------------------------------------------------------------------
# 【2. 啟動容器】先移除上一次殘留的容器，確保每次都是乾淨的種子資料
# ------------------------------------------------------------------------------
docker rm -f "${DB_CONTAINER}" "${MAIL_CONTAINER}" >/dev/null 2>&1 || true
docker run -d --rm --name "${DB_CONTAINER}" -p "${E2E_DB_PORT}:3306" \
  -e MYSQL_ROOT_PASSWORD="${DB_PASSWORD}" -e MYSQL_DATABASE=survey_flow \
  mysql:8.0.36 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null
docker run -d --rm --name "${MAIL_CONTAINER}" \
  -p "${E2E_SMTP_PORT}:1025" -p "${E2E_MAILPIT_PORT}:8025" axllent/mailpit:v1.27 >/dev/null

# [Why]: MySQL 映像首次啟動會先開一個「不接受 TCP」的暫時伺服器做初始化，
#        因此用 TCP (-h127.0.0.1) 查詢成功才代表正式伺服器已就緒
echo "[e2e] 等待 MySQL 就緒..."
for _ in $(seq 1 90); do
  if docker exec "${DB_CONTAINER}" mysql -h127.0.0.1 -uroot -p"${DB_PASSWORD}" -e "SELECT 1" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "${DB_CONTAINER}" mysql -h127.0.0.1 -uroot -p"${DB_PASSWORD}" -e "SELECT 1" >/dev/null 2>&1 \
  || { echo "[e2e] MySQL 90 秒內未就緒" >&2; exit 1; }

# ------------------------------------------------------------------------------
# 【3. 打包並啟動後端】
#   SPRING_SQL_INIT_MODE=always    啟動時執行 schema.sql + seed/01~05 (正式環境為 never)
#   JWT_ACCESS_EXPIRATION_MS=20000 Access Token 20 秒過期，讓 E2E 能實際驗證自動換發
#   REFRESH_COOKIE_*               本機 HTTP 測試：Cookie 不加 Secure、SameSite=Lax
#   MAIL_PROVIDER=SMTP + -D 參數   改寄到 Mailpit；Mailpit 不需要帳密與 TLS
#   RATE_LIMIT_*=100000            E2E 由同一 IP 大量註冊登入，調高速率限制避免誤擋
#   TRUSTED_PROXY_COUNT=0          本機直連沒有反向代理
#   AUDIT_HMAC_SECRET              稽核雜湊鏈金鑰，每次隨機產生
# ------------------------------------------------------------------------------
echo "[e2e] 打包後端..."
(cd "${BACKEND_DIR}" && ./mvnw -q -DskipTests package)
JAR="$(ls "${BACKEND_DIR}"/target/survey-flow-backend-*.jar | grep -v '\.original$' | head -n 1)"

echo "[e2e] 啟動後端 (http://localhost:${E2E_API_PORT})..."
export SERVER_PORT="${E2E_API_PORT}"
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:${E2E_DB_PORT}/survey_flow?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=Asia/Taipei"
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}"
export SPRING_SQL_INIT_MODE=always
export SPRING_SQL_INIT_ENCODING=UTF-8
export JWT_SECRET="${JWT_SECRET_VALUE}"
export AUDIT_HMAC_SECRET="${AUDIT_HMAC_SECRET_VALUE}"
export RATE_LIMIT_AUTH_MAX_REQUESTS=100000
export RATE_LIMIT_SUBMISSION_MAX_REQUESTS=100000
export RATE_LIMIT_GENERAL_MAX_REQUESTS=100000
export TRUSTED_PROXY_COUNT=0
export JWT_ACCESS_EXPIRATION_MS=20000
export REFRESH_COOKIE_SECURE=false
export REFRESH_COOKIE_SAME_SITE=Lax
export FRONTEND_BASE_URL="http://localhost:${E2E_WEB_PORT}"
export MAIL_PROVIDER=SMTP
export MAIL_HOST=127.0.0.1
export MAIL_PORT="${E2E_SMTP_PORT}"
exec java \
  -Dspring.mail.properties.mail.smtp.auth=false \
  -Dspring.mail.properties.mail.smtp.starttls.enable=false \
  -Dspring.mail.properties.mail.smtp.starttls.required=false \
  -jar "${JAR}"
