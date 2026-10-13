/**
 * ============================================================================
 * 檔案名稱: app.config.ts
 * 所屬模組: 應用程式全域配置模組 (Core Configuration Module)
 * 責任描述: 定義 Angular 19 獨立組件架構下的全域 Providers，包含變更檢測優化、路由滾動行為與 Material 分頁在地化。
 * 架構分層: Configuration Layer
 * 聯動關係:
 *   - 上游依賴: ./app.routes (應用程式路由表), @angular/material/paginator
 *   - 下游消費: main.ts (bootstrapApplication 載入)
 * 邊界處理: 內建分頁器長度與邊界防呆運算，防止頁碼溢出或負數產生
 * ============================================================================
 */

import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { MatPaginatorIntl } from '@angular/material/paginator';

import { routes } from './app.routes';

/**
 * 繁體中文在地化 MatPaginator 實例建構工廠
 * 內聯提供分頁器在地化翻譯，不增加額外獨立 service 檔案
 * @returns MatPaginatorIntl 臺灣繁體中文在地化分頁器國際化物件
 */
export function getTaiwanPaginatorIntl(): MatPaginatorIntl {
  const intl = new MatPaginatorIntl();
  intl.itemsPerPageLabel = '每頁筆數：';
  intl.nextPageLabel = '下一頁';
  intl.previousPageLabel = '上一頁';
  intl.firstPageLabel = '第一頁';
  intl.lastPageLabel = '最後一頁';

  intl.getRangeLabel = (
    pageIndex: number,
    pageSize: number,
    totalLength: number,
  ): string => {
    if (totalLength === 0 || pageSize === 0) {
      return '第 0 筆，共 0 筆';
    }
    const safeLength = Math.max(totalLength, 0);
    const startIndex = pageIndex * pageSize;
    const endIndex =
      startIndex < safeLength
        ? Math.min(startIndex + pageSize, safeLength)
        : startIndex + pageSize;

    return `第 ${startIndex + 1} – ${endIndex} 筆，共 ${safeLength} 筆`;
  };

  return intl;
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withInMemoryScrolling({
        scrollPositionRestoration: 'top',
        anchorScrolling: 'enabled',
      })
    ),
    {
      provide: MatPaginatorIntl,
      useFactory: getTaiwanPaginatorIntl,
    },
  ],
};
