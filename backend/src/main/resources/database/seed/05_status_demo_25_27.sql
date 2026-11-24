-- ============================================================================
-- 檔案名稱: 05_status_demo_25_27.sql
-- 責任描述: 示範種子資料 05 / 05：狀態示範問卷 (testuser 擁有)：25 排程中、26 已截止 (2 份回收)、27 僅限連結
-- 執行順序: schema.sql → seed/01 → 02 → 03 → 04 → 05 (完整重置)；
--           02 ~ 05 開頭會先刪除本檔負責之 ID 範圍，中途失敗時只需重跑該檔；本檔依賴 01 (會員)
-- 執行方式: Spring Boot (spring.sql.init.data-locations 依序列出)、整合測試、E2E，或於 TiDB / MySQL 依序貼上執行
-- 邊界:     只用於初始化或重建示範環境；全部為單筆 INSERT，網頁版 SQL Editor 也能完整執行
-- 字元編碼: utf8mb4 / utf8mb4_unicode_ci
-- ============================================================================

USE `survey_flow`;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
-- [Why]: 各檔之間有外鍵參照 (例如 04 的作答參照 03 的選項)，暫停外鍵檢查讓單一檔案可獨立重跑；檔尾恢復
SET FOREIGN_KEY_CHECKS = 0;

-- 先刪除本檔負責之資料範圍 (子表在前)，因此可單獨重跑而不產生重複
DELETE FROM `submission_answers` WHERE `id` BETWEEN 881 AND 886;
DELETE FROM `submissions` WHERE `id` BETWEEN 101 AND 102;
DELETE FROM `survey_options` WHERE `id` BETWEEN 266 AND 273;
DELETE FROM `survey_questions` WHERE `id` BETWEEN 181 AND 189;
DELETE FROM `survey_basic_info_configs` WHERE `survey_id` BETWEEN 25 AND 27;
DELETE FROM `surveys` WHERE `id` BETWEEN 25 AND 27;

-- -------------------------------------------------------------
-- 問卷狀態示範 (testuser 擁有，讓「問卷管理」頁可看到每一種狀態)
--   25. 排程中：開始時間在未來 14 天後，大廳暫不顯示，填答頁顯示尚未開始
--   26. 已截止：結束時間已過，大廳不顯示，仍可在管理頁查看 2 份回收結果
--   27. 僅限連結：開放填答但不公開於大廳，只能透過分享連結進入
-- -------------------------------------------------------------
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `cover_image_url`, `category`, `is_public_to_lobby`, `is_open`, `start_date`, `end_date`, `share_token`, `view_count`, `created_at`, `updated_at`) VALUES (25, 2, '【排程中】年度會員服務滿意度預調查', '【狀態示範】此問卷已設定未來的開始時間，時間未到前不會出現在大廳，直接開啟連結會顯示尚未開始。', NULL, '顧客滿意度', 1, 1, NOW() + INTERVAL 14 DAY, NULL, 'Iextuh-olfMT6goijia9Zw', 0, NOW(), NOW());
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `cover_image_url`, `category`, `is_public_to_lobby`, `is_open`, `start_date`, `end_date`, `share_token`, `view_count`, `created_at`, `updated_at`) VALUES (26, 2, '【已截止】上半年新功能試用回饋', '【狀態示範】此問卷的結束時間已過，不再接受填答，但擁有者仍可查看回收結果與匯出報表。', NULL, '產品體驗研究', 1, 1, '2026-01-01 00:00:00', '2026-06-30 23:59:59', '6rGqk_qYYT6DWRBjZ7E1mw', 42, NOW(), NOW());
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `cover_image_url`, `category`, `is_public_to_lobby`, `is_open`, `start_date`, `end_date`, `share_token`, `view_count`, `created_at`, `updated_at`) VALUES (27, 2, '【僅限連結】團隊讀書會時段調查', '【狀態示範】此問卷開放填答但不公開於大廳，只有拿到分享連結的人可以填寫。', NULL, '社群互動與綜合', 0, 1, NULL, NULL, 'LPOQP2Z38hvCM9OFiMM84Q', 7, NOW(), NOW());

INSERT INTO `survey_basic_info_configs` (`survey_id`, `collect_basic_info`, `require_name`, `require_gender`, `require_age`, `require_birthday`, `require_national_id`, `require_phone`, `require_home_phone`, `require_email`, `require_industry`, `require_job_title`, `require_department`, `require_employee_id`, `require_academic_major`, `require_class_name`, `require_student_id`, `require_height`, `require_weight`, `require_residence`, `require_registered_address`) VALUES (25, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
INSERT INTO `survey_basic_info_configs` (`survey_id`, `collect_basic_info`, `require_name`, `require_gender`, `require_age`, `require_birthday`, `require_national_id`, `require_phone`, `require_home_phone`, `require_email`, `require_industry`, `require_job_title`, `require_department`, `require_employee_id`, `require_academic_major`, `require_class_name`, `require_student_id`, `require_height`, `require_weight`, `require_residence`, `require_registered_address`) VALUES (26, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
INSERT INTO `survey_basic_info_configs` (`survey_id`, `collect_basic_info`, `require_name`, `require_gender`, `require_age`, `require_birthday`, `require_national_id`, `require_phone`, `require_home_phone`, `require_email`, `require_industry`, `require_job_title`, `require_department`, `require_employee_id`, `require_academic_major`, `require_class_name`, `require_student_id`, `require_height`, `require_weight`, `require_residence`, `require_registered_address`) VALUES (27, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`) VALUES (181, 25, 'Q1', 'SINGLE', '您成為本平臺會員的時間有多久？', NULL, 1, 1, NULL, NULL, NOW(), NOW());
INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`) VALUES (182, 25, 'Q2', 'RATING', '整體而言，您對本平臺會員服務的滿意度（1-5分）', NULL, 1, 2, 1, 5, NOW(), NOW());
INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`) VALUES (183, 25, 'Q3', 'TEXT', '希望明年新增哪些會員服務？', NULL, 0, 3, NULL, NULL, NOW(), NOW());
INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`) VALUES (184, 26, 'Q1', 'BOOLEAN', '試用期間您是否實際使用過「條件跳題」功能？', NULL, 1, 1, NULL, NULL, NOW(), NOW());
INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`) VALUES (185, 26, 'Q2', 'RATING', '新功能的整體易用度評分（1-5分）', NULL, 1, 2, 1, 5, NOW(), NOW());
INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`) VALUES (186, 26, 'Q3', 'TEXT', '其他建議', NULL, 0, 3, NULL, NULL, NOW(), NOW());
INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`) VALUES (187, 27, 'Q1', 'MULTI', '您方便參加讀書會的星期（複選）', NULL, 1, 1, NULL, NULL, NOW(), NOW());
INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`) VALUES (188, 27, 'Q2', 'DATE', '您希望第一次聚會的日期', NULL, 1, 2, NULL, NULL, NOW(), NOW());
INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`) VALUES (189, 27, 'Q3', 'TIME', '您偏好的開始時間', NULL, 1, 3, NULL, NULL, NOW(), NOW());

INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`) VALUES (266, 181, '未滿 1 年', 1, NULL);
INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`) VALUES (267, 181, '1 至 3 年', 2, NULL);
INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`) VALUES (268, 181, '3 年以上', 3, NULL);
INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`) VALUES (269, 184, '是', 1, NULL);
INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`) VALUES (270, 184, '否', 2, NULL);
INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`) VALUES (271, 187, '星期二', 1, NULL);
INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`) VALUES (272, 187, '星期四', 2, NULL);
INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`) VALUES (273, 187, '星期六', 3, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`) VALUES (101, 26, '192.168.5.21', 64, '2026-05-12 10:15:00');
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`) VALUES (102, 26, '192.168.5.22', 38, '2026-06-03 21:40:00');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (881, 101, 184, 269, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (882, 101, 185, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (883, 101, 186, NULL, '跳題設定很直覺，希望能在預覽時標示哪些題目會被解鎖', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (884, 102, 184, 270, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (885, 102, 185, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (886, 102, 186, NULL, NULL, NULL);

-- 恢復外鍵檢查並提交
SET FOREIGN_KEY_CHECKS = 1;
COMMIT;
