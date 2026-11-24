-- ============================================================================
-- 檔案名稱: 01_reset_and_users.sql
-- 責任描述: 示範種子資料 01 / 05：清空全部業務資料表、重置稽核雜湊鏈起點，寫入 14 位會員 (管理員 1、正常 11、待開通 1、已凍結 1)
-- 執行順序: schema.sql → seed/01 → 02 → 03 → 04 → 05 (完整重置)；
--           本檔以 TRUNCATE 清空全部資料表 (可重複執行)；執行本檔後必須接著依序執行 02 ~ 05
-- 執行方式: Spring Boot (spring.sql.init.data-locations 依序列出)、整合測試、E2E，或於 TiDB / MySQL 依序貼上執行
-- 邊界:     只用於初始化或重建示範環境；全部為單筆 INSERT，網頁版 SQL Editor 也能完整執行
-- 字元編碼: utf8mb4 / utf8mb4_unicode_ci
-- ============================================================================

USE `survey_flow`;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- [Why]: 先暫停外鍵檢查才能依任意順序 TRUNCATE；檔尾恢復
SET FOREIGN_KEY_CHECKS = 0;
-- 稽核鏈隨稽核紀錄一起歸零，重建後第一筆紀錄的 prev_hash 為 64 個 0
UPDATE `audit_chain_head` SET `last_log_id` = 0, `last_hash` = REPEAT('0', 64), `updated_at` = NOW(3) WHERE `id` = 1;
TRUNCATE TABLE `submission_answers`;
TRUNCATE TABLE `submission_basic_infos`;
TRUNCATE TABLE `submissions`;
TRUNCATE TABLE `survey_options`;
TRUNCATE TABLE `survey_questions`;
TRUNCATE TABLE `survey_basic_info_configs`;
TRUNCATE TABLE `surveys`;
TRUNCATE TABLE `audit_logs`;
TRUNCATE TABLE `revoked_tokens`;
TRUNCATE TABLE `users`;

-- 重設系統預設使用者帳號與密碼 (全體會員預設密碼清單詳見下方註解)
-- 【管理員】admin           -> 密碼: Admin123!  (ROLE_ADMIN)
-- 【一般會員】testuser        -> 密碼: User1234!   (ROLE_USER)
-- 【一般會員】academic_chen   -> 密碼: User1234!   (ROLE_USER)
-- 【一般會員】product_lin     -> 密碼: User1234!   (ROLE_USER)
-- 【一般會員】hr_huang        -> 密碼: User1234!   (ROLE_USER)
-- 【一般會員】market_wu       -> 密碼: User1234!   (ROLE_USER)
-- 【一般會員】event_chang     -> 密碼: User1234!   (ROLE_USER)
-- 【一般會員】support_li      -> 密碼: User1234!   (ROLE_USER)
-- 【一般會員】edu_wang        -> 密碼: User1234!   (ROLE_USER)
-- 【一般會員】health_tsai     -> 密碼: User1234!   (ROLE_USER)
-- 【一般會員】policy_yang     -> 密碼: User1234!   (ROLE_USER)
-- 【一般會員】social_liao     -> 密碼: User1234!   (ROLE_USER)
-- 【待開通】  pending_chiu    -> 密碼: User1234!   (PENDING_VERIFICATION，登入時被導向開通頁)
-- 【已凍結】  suspended_kuo   -> 密碼: User1234!   (SUSPENDED，登入被拒；管理員可於會員管理恢復)
-- 密碼: Admin123!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (1, 'admin', '$argon2id$v=19$m=4096,t=3,p=1$dxlqSLPglwBvQ/uW8I4u4Q$XFRrN5aM8WzQSfMGTQCyLYi880buSWKmRgRpc0FwPgI', '系統最高管理員', 'admin@surveyflow.com', '0912345678', 'A123456789', 'ROLE_ADMIN', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (2, 'testuser', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '測試會員小明', 'testuser@surveyflow.com', '0987654321', 'B234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (3, 'academic_chen', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '陳思翰副教授', 'sh.chen@surveyflow.com', '0911223344', 'C123456789', 'ROLE_USER', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (4, 'product_lin', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '林品妤產品長', 'py.lin@surveyflow.com', '0922334455', 'D234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (5, 'hr_huang', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '黃彥碩人資主管', 'ys.huang@surveyflow.com', '0933445566', 'E123456789', 'ROLE_USER', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (6, 'market_wu', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '吳芷晴行銷總監', 'cq.wu@surveyflow.com', '0944556677', 'F234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (7, 'event_chang', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '張銘宇活動策劃', 'my.chang@surveyflow.com', '0955667788', 'G123456789', 'ROLE_USER', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (8, 'support_li', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '李冠廷客服副總', 'kt.li@surveyflow.com', '0966778899', 'H234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (9, 'edu_wang', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '王靜雯培訓講師', 'cw.wang@surveyflow.com', '0977889900', 'I123456789', 'ROLE_USER', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (10, 'health_tsai', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '蔡承翰健康顧問', 'ch.tsai@surveyflow.com', '0988990011', 'J234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (11, 'policy_yang', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '楊心慈政策研究員', 'ct.yang@surveyflow.com', '0999001122', 'K123456789', 'ROLE_USER', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234!
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (12, 'social_liao', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '廖俊翔社群經理', 'jx.liao@surveyflow.com', '0900112233', 'L234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW());
-- 密碼: User1234! (尚未完成信箱驗證)
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (13, 'pending_chiu', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '邱怡君', 'yj.chiu@surveyflow.com', '0911002200', 'M123456789', 'ROLE_USER', 'PENDING_VERIFICATION', NOW(), NOW());
-- 密碼: User1234! (已被管理員凍結)
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`) VALUES (14, 'suspended_kuo', '$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM', '郭建志', 'jc.kuo@surveyflow.com', '0911003300', 'N234567890', 'ROLE_USER', 'SUSPENDED', NOW(), NOW());

-- 恢復外鍵檢查並提交
SET FOREIGN_KEY_CHECKS = 1;
COMMIT;
