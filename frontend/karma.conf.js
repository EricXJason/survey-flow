// ==============================================================================
// 檔案名稱: karma.conf.js
// 責任描述: 前端單元測試執行設定 (Karma + Jasmine，於 Chrome 執行 *.spec.ts) 與覆蓋率門檻
// 執行方式:
//   本機一次性執行：pnpm exec ng test --watch=false --browsers=ChromeHeadless --code-coverage
//   CI 執行：同上 (GitHub Actions ubuntu-latest 內建 Chrome)
// 覆寫方式: 調整下方 check.global 的百分比即可；低於門檻時 ng test 以非 0 結束碼失敗
// ==============================================================================

module.exports = function (config) {
  config.set({
    basePath: '',
    // ----------------------------------------------------------------------------
    // 【1. 測試框架與外掛】
    //   jasmine                     測試語法 (describe / it / expect)
    //   @angular-devkit/build-angular Angular CLI 負責編譯 TypeScript 與範本
    //   karma-coverage              以 Istanbul 計算覆蓋率並檢查門檻
    // ----------------------------------------------------------------------------
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      jasmine: {
        // [Why]: 固定測試順序，失敗時容易重現；各測試仍各自在 beforeEach 重建狀態
        random: false,
      },
      clearContext: false,
    },
    jasmineHtmlReporter: {
      suppressAll: true,
    },
    // ----------------------------------------------------------------------------
    // 【2. 覆蓋率報告與門檻】
    //   dir            報告輸出位置 (coverage/survey-flow/index.html 可用瀏覽器開啟)
    //   reporters      html：逐行檢視；text-summary：終端機摘要
    //   check.global   全專案最低覆蓋率 (%)；分支較低是因為範本綁定與防禦性 ?? 預設值
    // ----------------------------------------------------------------------------
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/survey-flow'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }],
      check: {
        global: {
          statements: 95,
          branches: 85,
          functions: 95,
          lines: 95,
        },
      },
    },
    // ----------------------------------------------------------------------------
    // 【3. 執行環境】
    //   browsers  預設 Chrome；CI 與命令列以 --browsers=ChromeHeadless 覆寫為無視窗模式
    //   restartOnFileChange  監看模式下檔案變更時重新執行
    // ----------------------------------------------------------------------------
    reporters: ['progress', 'kjhtml'],
    browsers: ['Chrome'],
    restartOnFileChange: true,
  });
};
