-- 청첩장 저장 기능(ADR-009) 스키마 변경 DDL
-- 작성일: 2026-07-21
-- 대상: PostgreSQL
--
-- 배경: 이 프로젝트는 ddl-auto: validate 이고 Flyway/Liquibase가 없다.
--       따라서 애플리케이션 기동 전에 아래 DDL을 수동으로 적용해야 한다.
--       적용하지 않으면 Hibernate 스키마 검증(validate)이 실패해 앱이 부팅되지 않는다.
--
-- 순서 주의: image_uploads.uploader_id 는 NOT NULL 이라, 기존 행이 있으면
--            바로 NOT NULL 을 걸 수 없다. 아래 3단계 순서를 지킨다.

BEGIN;

-- 1) invitations: 외부 식별자(UUID)와 사용자 선택 옵션(jsonb) 추가
ALTER TABLE invitations
    ADD COLUMN invitation_uid UUID,
    ADD COLUMN selected_options JSONB;

-- 기존 청첩장 행이 있다면 invitation_uid 를 채운다(무작위 UUID).
-- 새 행은 애플리케이션의 @PrePersist 가 시간순 UUID를 채운다.
UPDATE invitations SET invitation_uid = gen_random_uuid() WHERE invitation_uid IS NULL;

ALTER TABLE invitations
    ALTER COLUMN invitation_uid SET NOT NULL;

ALTER TABLE invitations
    ADD CONSTRAINT uk_invitation_uid UNIQUE (invitation_uid);

-- 2) image_uploads.status: ORPHANED 값이 추가되었으나, status 는 varchar(@Enumerated STRING)
--    이므로 별도 제약 변경은 없다. (enum 을 DB 타입으로 쓰지 않는다.)

-- 3) image_uploads: 업로더(FK) 추가
--
--    (a) 기존 image_uploads 행이 없는 경우 — 곧바로 NOT NULL 로 추가 가능:
ALTER TABLE image_uploads
    ADD COLUMN uploader_id BIGINT;

--    (b) 기존 행이 있다면, 여기서 각 행의 소유자를 백필해야 한다.
--        업로드 기록에는 원래 업로더 정보가 없으므로, 연결된 청첩장의 소유자로 유추하거나
--        (invitation_id 가 있는 행) 정리 대상(고아)이면 삭제한다. 데이터 상황에 맞게 택일:
--
--    -- 연결된 청첩장의 소유자로 백필:
--    UPDATE image_uploads iu
--       SET uploader_id = inv.user_id
--      FROM invitations inv
--     WHERE iu.invitation_id = inv.invitation_id
--       AND iu.uploader_id IS NULL;
--
--    -- 소유자를 정할 수 없는 남은 행(PENDING 고아 등)은 정리:
--    DELETE FROM image_uploads WHERE uploader_id IS NULL;

ALTER TABLE image_uploads
    ALTER COLUMN uploader_id SET NOT NULL;

ALTER TABLE image_uploads
    ADD CONSTRAINT fk_image_upload_uploader
        FOREIGN KEY (uploader_id) REFERENCES users (user_id);

COMMIT;

-- 참고: 운영 데이터가 없다면 위 (b) 백필 블록은 건너뛰어도 된다.
--       현재 이 기능은 신규이며 운영 image_uploads 데이터가 없는 것으로 파악되었다.
