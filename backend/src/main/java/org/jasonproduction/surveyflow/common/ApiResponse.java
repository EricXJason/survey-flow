/**
 * ============================================================================
 * 檔案名稱: ApiResponse.java
 * 所屬模組: Common 核心通用模組
 * 責任描述: 全系統成功回應 (2xx) 之統一封裝物件；錯誤回應不使用本類別，一律為 RFC 9457 Problem Details (見 GlobalExceptionHandler)
 * 架構分層: Presentation / Common Layer
 * 聯動關係:
 *   - 上游依賴: 各領域 Controller 業務回傳結果
 *   - 下游消費: 前端 Angular 各 Service 訂閱之 HTTP Response Body
 * 邊界處理: 泛型支援任何型別之資料負載 (Payload)，若無資料則為 null
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@ToString
public class ApiResponse<T> {

    /** HTTP 狀態碼 (成功回應恆為 200) */
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
}
