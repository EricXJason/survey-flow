# 08. 容器建置與維運指標 (DevOps & Build Pipeline)

> **專案作者**: 許哲誠 (HSU, CHE-CHENG)  
> **運行環境**: Node.js 24.18.0, Angular CLI 19.0.5, Java 21.0.12, Spring Boot 4.1.1, MySQL 8.0.36  
> **更新日期**: 2026-10-15  

---

## 1. 本地開發與構建管線

### 1.1 前置需求
- **Node.js**: `24.18.0`
- **Java JDK**: `21.0.12`
- **MySQL**: `8.0.36`
- **套件管理器**: `pnpm` 或 `npm 10.8.2`

### 1.2 核心指令集
```bash
# 安裝相依套件
npm install

# 啟動本地開發伺服器 (預設監聽 http://localhost:4200)
npm run start
# 或
ng serve

# 正式生產環境編譯打包 (驗證 0 錯誤構建)
npm run build

# 執行單元測試
npm run test
```

### 1.3 產物拓撲與打包指標
執行 `npm run build` 後產物輸出至 `dist/survey-flow/`：
- `main-[hash].js`: 包含所有 Standalone 組件與 Angular 核心核心代碼（約 204 kB 壓縮傳輸量）。
- `polyfills-[hash].js`: 現代瀏覽器墊片相容層（約 11 kB）。
- `styles-[hash].css`: 編譯後之全域 SCSS 樣式包（約 5 kB）。
- 總初始傳輸量 (Initial Bundle Size) 小於 `230 kB`，達到首屏秒開之極致性能標準。

---

## 2. 生產環境部署與 Nginx 靜態伺服器配置

```nginx
server {
    listen 80;
    server_name surveyflow.local;

    root /usr/share/nginx/html/survey-flow/browser;
    index index.html;

    # Gzip 壓縮支援
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript;

    # SPA 路由歷史回退 (HTML5 History API)
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 靜態資源長效快取
    location ~* \.(?:ico|css|js|gif|jpe?g|png|woff2?|eot|otf|ttf|svg)$ {
        expires 6M;
        access_log off;
        add_header Cache-Control "public, max-age=15552000, immutable";
    }
}
```
