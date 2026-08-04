-- ============================================================================
-- Triplog 지난 메이트 조회 테스트용 시드 (H2)
--
-- 전제:
--   - TestInitData가 이미 실행되어 admin 계정(dev@example.com)이 있는 상태
--
-- 실행 방법:
--   1) H2 콘솔(http://localhost:8080/h2-console)에서 이 파일 전체를 붙여넣어 Run
--   2) 또는 H2 콘솔에서: RUNSCRIPT FROM '<file-path>';
--
-- 특징:
--   - id는 모두 자동 생성(identity)에 맡김 → 재실행/기존 데이터와 PK 충돌 없음
--   - 참조는 이메일(members) / join_code(trip_groups)로 서브쿼리 조회
--
-- 결과:
--   - admin의 지난 메이트 = 20명
--   - 여행 11건 추가 (일부는 같은 날짜 → 3단 정렬(date, name, id) 테스트용)
--   - 김수민만 여행 2회 → travelCount 다중 케이스 확인
--
-- password 모두 '1234' (BCrypt)
-- ============================================================================

-- ============ 0. admin(dev@example.com) 보장 ============
-- 배경:
--   spring.sql.init은 (defer-datasource-initialization=true여도) Hibernate DDL 직후에 실행되고,
--   admin을 만드는 DevAccountInitData(ApplicationRunner)는 그 이후에 돈다.
--   → 첫 앱 기동 시 이 시점엔 admin이 없어서 아래 trip_groups의
--     (SELECT id FROM members WHERE email='dev@example.com') 서브쿼리가 NULL을 반환,
--     member_id NOT NULL/FK 위반으로 전량 실패 → 결과적으로 지난 메이트 0명이 된다.
-- 대응:
--   여기서 admin을 idempotent하게 미리 삽입한다.
--   - 이미 있으면(2회차 이후 실행 등) WHERE NOT EXISTS로 스킵.
--   - 비밀번호 해시는 다른 시드 계정들과 동일한 BCrypt('1234').
--   - DevAccountInitData는 existsByEmail 체크가 있어 우리가 먼저 넣어도 안전하게 스킵됨.
INSERT INTO members (email, password, name, created_at, updated_at)
SELECT 'dev@example.com',
       '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq',
       '개발자1',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM members WHERE email = 'dev@example.com');

-- ============ 1. 친구 회원 20명 ============
-- failed_login_count: 로그인 실패 카운터 컬럼 (NOT NULL) — seed는 항상 0으로 시작
-- locked_until: nullable이라 생략 (기본 NULL = 락 안 걸린 상태)
INSERT INTO members (email, password, name, failed_login_count, created_at, updated_at) VALUES
('kim.sumin@test.com',    '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '김수민', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('lee.jinho@test.com',    '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '이진호', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('park.seoyeon@test.com', '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '박서연', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('choi.youngsu@test.com', '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '최영수', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('jung.haneul@test.com',  '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '정하늘', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('kang.minjun@test.com',  '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '강민준', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('cho.yujin@test.com',    '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '조유진', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('yoon.dohyun@test.com',  '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '윤도현', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('lim.chaewon@test.com',  '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '임채원', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('han.jihoon@test.com',   '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '한지훈', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('oh.sea@test.com',       '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '오세아', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('seo.jimin@test.com',    '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '서지민', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('hong.junseo@test.com',  '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '홍준서', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('shin.daeun@test.com',   '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '신다은', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bae.hyunwoo@test.com',  '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '배현우', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('cho.minji@test.com',    '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '조민지', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('yu.garam@test.com',     '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '유가람', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('moon.jiho@test.com',    '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '문지호', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('na.yerin@test.com',     '$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '나예린', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('baek.seungwoo@test.com','$2a$10$DWK7CC1RmNMw177o0KWD0OIER1Sr5BSw0uZVcndxNa4eEPn3rD2bq', '백승우', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============ 2. 여행 11건 (모두 admin이 owner) ============
-- 날짜 배치: 최근순 정렬 + 같은 날짜 tie 케이스 포함
INSERT INTO trip_groups (member_id, name, region, nights, join_code, start_date, end_date, is_vote, created_at, updated_at) VALUES
((SELECT id FROM members WHERE email = 'dev@example.com'), '제주 힐링',      '제주', 3, 'JEJU2606', '2026-06-01', '2026-06-04', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'dev@example.com'), '서울 미식투어',   '서울', 2, 'SEOUL051', '2026-05-15', '2026-05-17', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'dev@example.com'), '부산 바다',      '부산', 2, 'BUSAN051', '2026-05-15', '2026-05-17', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'dev@example.com'), '강릉 커피여행',   '강릉', 2, 'GN2604',   '2026-04-10', '2026-04-12', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'dev@example.com'), '여수 야경',      '여수', 2, 'YS2603',   '2026-03-20', '2026-03-22', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'dev@example.com'), '전주 한옥마을',   '전주', 1, 'JJ2602',   '2026-02-15', '2026-02-16', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'dev@example.com'), '통영 굴 먹방',   '통영', 2, 'TY2601',   '2026-01-05', '2026-01-07', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'dev@example.com'), '대구 근대여행',   '대구', 1, 'DG2512',   '2025-12-10', '2025-12-11', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'dev@example.com'), '인천 차이나타운', '인천', 1, 'IC2511',   '2025-11-01', '2025-11-02', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'dev@example.com'), '대전 성심당',    '대전', 1, 'DJ2510',   '2025-10-15', '2025-10-16', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'dev@example.com'), '광주 무등산',    '광주', 2, 'GJ2509',   '2025-09-01', '2025-09-03', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============ 3. Trip Members ============
-- admin 11건 + 친구들 24건 = 35건
INSERT INTO trip_members (member_id, trip_group_id, is_admin, created_at, updated_at) VALUES
-- 제주 (2026-06-01) : admin + 김수민
((SELECT id FROM members WHERE email = 'dev@example.com'),      (SELECT id FROM trip_groups WHERE join_code = 'JEJU2606'), true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'kim.sumin@test.com'),   (SELECT id FROM trip_groups WHERE join_code = 'JEJU2606'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 서울 (2026-05-15) : admin + 이진호, 박서연, 최영수
((SELECT id FROM members WHERE email = 'dev@example.com'),      (SELECT id FROM trip_groups WHERE join_code = 'SEOUL051'), true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'lee.jinho@test.com'),   (SELECT id FROM trip_groups WHERE join_code = 'SEOUL051'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'park.seoyeon@test.com'),(SELECT id FROM trip_groups WHERE join_code = 'SEOUL051'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'choi.youngsu@test.com'),(SELECT id FROM trip_groups WHERE join_code = 'SEOUL051'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 부산 (2026-05-15) : admin + 정하늘 (서울과 같은 날짜 → tie 테스트)
((SELECT id FROM members WHERE email = 'dev@example.com'),      (SELECT id FROM trip_groups WHERE join_code = 'BUSAN051'), true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'jung.haneul@test.com'), (SELECT id FROM trip_groups WHERE join_code = 'BUSAN051'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 강릉 (2026-04-10) : admin + 강민준, 조유진, 윤도현
((SELECT id FROM members WHERE email = 'dev@example.com'),      (SELECT id FROM trip_groups WHERE join_code = 'GN2604'), true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'kang.minjun@test.com'), (SELECT id FROM trip_groups WHERE join_code = 'GN2604'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'cho.yujin@test.com'),   (SELECT id FROM trip_groups WHERE join_code = 'GN2604'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'yoon.dohyun@test.com'), (SELECT id FROM trip_groups WHERE join_code = 'GN2604'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 여수 (2026-03-20) : admin + 임채원, 한지훈
((SELECT id FROM members WHERE email = 'dev@example.com'),      (SELECT id FROM trip_groups WHERE join_code = 'YS2603'), true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'lim.chaewon@test.com'), (SELECT id FROM trip_groups WHERE join_code = 'YS2603'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'han.jihoon@test.com'),  (SELECT id FROM trip_groups WHERE join_code = 'YS2603'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 전주 (2026-02-15) : admin + 오세아
((SELECT id FROM members WHERE email = 'dev@example.com'),      (SELECT id FROM trip_groups WHERE join_code = 'JJ2602'), true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'oh.sea@test.com'),      (SELECT id FROM trip_groups WHERE join_code = 'JJ2602'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 통영 (2026-01-05) : admin + 서지민, 홍준서, 신다은
((SELECT id FROM members WHERE email = 'dev@example.com'),      (SELECT id FROM trip_groups WHERE join_code = 'TY2601'), true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'seo.jimin@test.com'),   (SELECT id FROM trip_groups WHERE join_code = 'TY2601'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'hong.junseo@test.com'), (SELECT id FROM trip_groups WHERE join_code = 'TY2601'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'shin.daeun@test.com'),  (SELECT id FROM trip_groups WHERE join_code = 'TY2601'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 대구 (2025-12-10) : admin + 배현우, 조민지
((SELECT id FROM members WHERE email = 'dev@example.com'),      (SELECT id FROM trip_groups WHERE join_code = 'DG2512'), true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'bae.hyunwoo@test.com'), (SELECT id FROM trip_groups WHERE join_code = 'DG2512'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'cho.minji@test.com'),   (SELECT id FROM trip_groups WHERE join_code = 'DG2512'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 인천 (2025-11-01) : admin + 유가람, 문지호
((SELECT id FROM members WHERE email = 'dev@example.com'),      (SELECT id FROM trip_groups WHERE join_code = 'IC2511'), true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'yu.garam@test.com'),    (SELECT id FROM trip_groups WHERE join_code = 'IC2511'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'moon.jiho@test.com'),   (SELECT id FROM trip_groups WHERE join_code = 'IC2511'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 대전 (2025-10-15) : admin + 나예린
((SELECT id FROM members WHERE email = 'dev@example.com'),      (SELECT id FROM trip_groups WHERE join_code = 'DJ2510'), true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'na.yerin@test.com'),    (SELECT id FROM trip_groups WHERE join_code = 'DJ2510'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- 광주 (2025-09-01) : admin + 백승우, 김수민(2번째 참여 → travelCount=2 테스트)
((SELECT id FROM members WHERE email = 'dev@example.com'),       (SELECT id FROM trip_groups WHERE join_code = 'GJ2509'), true,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'baek.seungwoo@test.com'),(SELECT id FROM trip_groups WHERE join_code = 'GJ2509'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM members WHERE email = 'kim.sumin@test.com'),    (SELECT id FROM trip_groups WHERE join_code = 'GJ2509'), false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================================
-- 검증 쿼리 (참고용)
-- ============================================================================
--
-- admin의 지난 메이트 개수 확인 (20이어야 함):
--   SELECT COUNT(DISTINCT other.member_id)
--   FROM trip_members me
--   JOIN trip_members other ON other.trip_group_id = me.trip_group_id
--   WHERE me.member_id = (SELECT id FROM members WHERE email = 'dev@example.com')
--     AND other.member_id <> me.member_id;
--
-- admin의 지난 메이트 (최근순, 이름순, id순):
--   SELECT other.member_id, m.name,
--          COUNT(DISTINCT other.trip_group_id) AS travel_count,
--          MAX(tg.start_date) AS latest
--   FROM trip_members me
--   JOIN trip_members other ON other.trip_group_id = me.trip_group_id
--   JOIN trip_groups tg ON other.trip_group_id = tg.id
--   JOIN members m ON other.member_id = m.id
--   WHERE me.member_id = (SELECT id FROM members WHERE email = 'dev@example.com')
--     AND other.member_id <> me.member_id
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
