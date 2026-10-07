ALTER TABLE review_task
  ADD COLUMN target_label_version_id VARCHAR(120),
  ADD COLUMN decision VARCHAR(40),
  ADD COLUMN resolved_by_user_id VARCHAR(80),
  ADD COLUMN resolved_at DATETIME;

UPDATE review_task
SET target_label_version_id = draft_label_version_id
WHERE draft_label_version_id IS NOT NULL;

ALTER TABLE review_task
  ADD CONSTRAINT fk_review_task_target_label_v7
    FOREIGN KEY (target_label_version_id)
    REFERENCES label_version(label_version_id),
  ADD CONSTRAINT fk_review_task_resolver_v7
    FOREIGN KEY (resolved_by_user_id)
    REFERENCES user_account(user_id),
  ADD INDEX idx_review_task_target_status_v7
    (target_label_version_id, status, resolved_at);

-- Publication is owned by the Spring transaction so the ReviewTask guard,
-- lifecycle changes, pointer, record, audit event, and resolution commit together.
DROP PROCEDURE IF EXISTS sp_publish_label;
