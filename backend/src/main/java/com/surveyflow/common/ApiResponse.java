/**
 * ============================================================================
 * 檔案名稱: ApiResponse.java
 * 所屬模組: Common 核心通用模組
 * 責任描述: 全系統統一 RESTful API 封裝回傳物件，確保前後端資料契約一元化
 * 架構分層: Presentation / Common Layer
 * 聯動關係:
 *   - 上游依賴: 各領域 Controller 業務回傳結果或 GlobalExceptionHandler 異常
 *   - 下游消費: 前端 Angular 各 Service 訂閱之 HTTP Response Body
 * 邊界處理: 泛型支援任何型別之資料負載 (Payload)，若無資料則為 null
 * ============================================================================
 */

package com.surveyflow.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 狀態碼 (例如: 200, 400, 401, 403, 404, 500) */
    private int status;

    /** 業務處理是否成功 */
    private boolean success;

    /** 伺服器提示訊息 (純臺灣繁體中文) */
    private String message;

    /** 資料負載主體 */
    private T data;

    /** 伺服器回傳時間戳記 */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 快速建立成功回應 (含資料)
     * @param data 回傳資料內容
     * @param message 提示訊息
     * @param <T> 資料泛型型別
     * @return 封裝後之標準 ApiResponse 物件
     */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
                .status(200)
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 快速建立成功回應 (僅訊息)
     * @param message 提示訊息
     * @param <T> 資料泛型型別
     * @return 封裝後之標準 ApiResponse 物件
     */
    public static <T> ApiResponse<T> ok(String message) {
        return ok(null, message);
    }

    /**
     * 快速建立錯誤失敗回應
     * @param status HTTP 狀態碼
     * @param message 錯誤原因描述
     * @param <T> 資料泛型型別
     * @return 封裝後之標準 ApiResponse 物件
     */
    public static <T> ApiResponse<T> error(int status, String message) {
        return ApiResponse.<T>builder()
                .status(status)
                .success(false)
                .message(message)
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
