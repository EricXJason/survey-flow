/**
 * ============================================================================
 * 檔案名稱: SurveyFlowApplication.java
 * 所屬模組: SurveyFlow 核心啟動模組
 * 責任描述: Spring Boot 4.1.1 系統主啟動入口類別，負責初始化 Spring 上下文與啟動內嵌 Web 容器
 * 架構分層: Application Layer
 * 聯動關係:
 *   - 上游依賴: application.yml (系統設定檔)
 *   - 下游消費: 掃描 com.surveyflow 套件下所有 Controller, Service, Repository 與 Entity
 * 邊界處理: 啟用 JPA 審計支援 (@EnableJpaAuditing) 以自動管理建立與修改時間戳
 * ============================================================================
 */

package com.surveyflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SurveyFlowApplication {

    /**
     * 應用程式主執行進入點
     * @param args 外部命令行參數
     */
    public static void main(String[] args) {
        SpringApplication.run(SurveyFlowApplication.class, args);
    }
}
