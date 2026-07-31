-- ============================================================================
-- Triplog 지난 메이트 조회 테스트용 시드 (H2)
--
-- 전제:
--   - TestInitData가 이미 실행되어 members(id 1,2,3), trip_groups(id 1,2,3),
--     trip_members(id 1~5)가 있는 상태
--
-- 실행 방법:
--   1) 앱 재기동 (기존 데이터 초기화가 되도록)
--   2) H2 콘솔(http://localhost:8080/h2-console)에서 이 파일 전체를 붙여넣어 Run
--   3) 또는 H2 콘솔에서: RUNSCRIPT FROM '<file-path>';
--
-- 결과:
--   - admin(id=1)의 지난 메이트 = 20명 (id 4~23)
--   - 여행 11건 추가 (일부는 같은 날짜 → 3단 정렬(date, name, id) 테스트용)
--   - 김수민(id=4)만 여행 2회 → travelCount 다중 케이스 확인
--
-- password 모두 '1234' (BCrypt)
-- ============================================================================

-- ============ 1. 친구 회원 20명 (id 4~23) ============
INSERT INTO members (id, email, password, name, refresh_token, created_at, updated_at) VALUES
(4,  'kim.sumin@test.com',   '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '김수민', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5,  'lee.jinho@test.com',   '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '이진호', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6,  'park.seoyeon@test.com','$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '박서연', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(7,  'choi.youngsu@test.com','$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '최영수', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(8,  'jung.haneul@test.com', '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '정하늘', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(9,  'kang.minjun@test.com', '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '강민준', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10, 'cho.yujin@test.com',   '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '조유진', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(11, 'yoon.dohyun@test.com', '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '윤도현', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(12, 'lim.chaewon@test.com', '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '임채원', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(13, 'han.jihoon@test.com',  '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '한지훈', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(14, 'oh.sea@test.com',      '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '오세아', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(15, 'seo.jimin@test.com',   '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '서지민', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(16, 'hong.junseo@test.com', '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '홍준서', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(17, 'shin.daeun@test.com',  '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '신다은', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(18, 'bae.hyunwoo@test.com', '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '배현우', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(19, 'cho.minji@test.com',   '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '조민지', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(20, 'yu.garam@test.com',    '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '유가람', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(21, 'moon.jiho@test.com',   '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '문지호', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(22, 'na.yerin@test.com',    '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '나예린', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(23, 'baek.seungwoo@test.com','$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq','백승우', RANDOM_UUID(), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============ 2. 여행 11건 (id 4~14, 모두 admin이 owner) ============
-- 날짜 배치: 최근순 정렬 + 같은 날짜 tie 케이스 포함
INSERT INTO trip_groups (id, member_id, name, region, nights, join_code, start_date, end_date, is_vote, created_at, updated_at) VALUES
(4,  1, '제주 힐링',       '제주', 3, 'JEJU2606', '2026-06-01', '2026-06-04', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5,  1, '서울 미식투어',    '서울', 2, 'SEOUL051', '2026-05-15', '2026-05-17', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6,  1, '부산 바다',       '부산', 2, 'BUSAN051', '2026-05-15', '2026-05-17', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(7,  1, '강릉 커피여행',    '강릉', 2, 'GN2604',   '2026-04-10', '2026-04-12', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(8,  1, '여수 야경',       '여수', 2, 'YS2603',   '2026-03-20', '2026-03-22', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(9,  1, '전주 한옥마을',    '전주', 1, 'JJ2602',   '2026-02-15', '2026-02-16', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10, 1, '통영 굴 먹방',    '통영', 2, 'TY2601',   '2026-01-05', '2026-01-07', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(11, 1, '대구 근대여행',    '대구', 1, 'DG2512',   '2025-12-10', '2025-12-11', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(12, 1, '인천 차이나타운',  '인천', 1, 'IC2511',   '2025-11-01', '2025-11-02', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(13, 1, '대전 성심당',     '대전', 1, 'DJ2510',   '2025-10-15', '2025-10-16', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(14, 1, '광주 무등산',     '광주', 2, 'GJ2509',   '2025-09-01', '2025-09-03', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============ 3. Trip Members (id 6~) ============
-- admin(id=1) 11건 + 친구들 24건 = 35건
INSERT INTO trip_members (id, member_id, trip_group_id, is_admin, created_at, updated_at) VALUES
-- Trip 4 (제주, 2026-06-01) : admin + 김수민
(6,  1, 4, true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(7,  4, 4, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Trip 5 (서울, 2026-05-15) : admin + 이진호, 박서연, 최영수
(8,  1, 5, true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(9,  5, 5, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10, 6, 5, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(11, 7, 5, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Trip 6 (부산, 2026-05-15) : admin + 정하늘 (5와 같은 날짜 → tie 테스트)
(12, 1, 6, true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(13, 8, 6, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Trip 7 (강릉, 2026-04-10) : admin + 강민준, 조유진, 윤도현
(14, 1, 7, true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(15, 9, 7, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(16, 10, 7, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(17, 11, 7, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Trip 8 (여수, 2026-03-20) : admin + 임채원, 한지훈
(18, 1, 8, true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(19, 12, 8, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(20, 13, 8, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Trip 9 (전주, 2026-02-15) : admin + 오세아
(21, 1, 9, true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(22, 14, 9, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Trip 10 (통영, 2026-01-05) : admin + 서지민, 홍준서, 신다은
(23, 1, 10, true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(24, 15, 10, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(25, 16, 10, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(26, 17, 10, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Trip 11 (대구, 2025-12-10) : admin + 배현우, 조민지
(27, 1, 11, true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(28, 18, 11, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(29, 19, 11, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Trip 12 (인천, 2025-11-01) : admin + 유가람, 문지호
(30, 1, 12, true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(31, 20, 12, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(32, 21, 12, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Trip 13 (대전, 2025-10-15) : admin + 나예린
(33, 1, 13, true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(34, 22, 13, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Trip 14 (광주, 2025-09-01) : admin + 백승우, 김수민(2번째 참여 → travelCount=2 테스트)
(35, 1, 14, true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(36, 23, 14, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(37, 4, 14, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============ 4. Auto-increment 시퀀스 재설정 ============
-- 이후 자동 생성될 ID가 위 수동 삽입 값과 충돌하지 않도록
ALTER TABLE members ALTER COLUMN id RESTART WITH 24;
ALTER TABLE trip_groups ALTER COLUMN id RESTART WITH 15;
ALTER TABLE trip_members ALTER COLUMN id RESTART WITH 38;

-- ============================================================================
-- 검증 쿼리 (참고용)
-- ============================================================================
--
-- admin(id=1)의 지난 메이트 개수 확인 (20이어야 함):
--   SELECT COUNT(DISTINCT other.member_id)
--   FROM trip_members me
--   JOIN trip_members other ON other.trip_group_id = me.trip_group_id
--   WHERE me.member_id = 1 AND other.member_id <> 1;
--
-- admin(id=1)의 지난 메이트 (최근순, 이름순, id순):
--   SELECT other.member_id, m.name,
--          COUNT(DISTINCT other.trip_group_id) AS travel_count,
--          MAX(tg.start_date) AS latest
--   FROM trip_members me
--   JOIN trip_members other ON other.trip_group_id = me.trip_group_id
--   JOIN trip_groups tg ON other.trip_group_id = tg.id
--   JOIN members m ON other.member_id = m.id
--   WHERE me.member_id = 1 AND other.member_id <> 1
--   GROUP BY other.member_id, m.name
--   ORDER BY latest DESC, m.name ASC, other.member_id ASC;
--
-- 기대 순서:
--   1  김수민   travel_count=2  2026-06-01
--   2  박서연   1              2026-05-15
--   3  이진호   1              2026-05-15
--   4  정하늘   1              2026-05-15
--   5  최영수   1              2026-05-15
--   6  강민준   1              2026-04-10
--   7  윤도현   1              2026-04-10
--   8  조유진   1              2026-04-10
--   9  임채원   1              2026-03-20
--   10 한지훈   1              2026-03-20
--   11 오세아   1              2026-02-15
--   12 서지민   1              2026-01-05
--   13 신다은   1              2026-01-05
--   14 홍준서   1              2026-01-05
--   15 배현우   1              2025-12-10
--   16 조민지   1              2025-12-10
--   17 문지호   1              2025-11-01
--   18 유가람   1              2025-11-01
--   19 나예린   1              2025-10-15
--   20 백승우   1              2025-09-01
