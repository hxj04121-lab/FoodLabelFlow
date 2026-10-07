ALTER TABLE review_task
  ADD COLUMN target_label_version_id VARCHAR(120),
  ADD COLUMN decision VARCHAR(40),
  ADD COLUMN resolved_by_user_id VARCHAR(80),
  ADD COLUMN resolved_at DATETIME;

UPDATE review_task
SET target_label_version_id = draft_label_version_id
WHERE draft_label_version_id IS NOT NULL;

-- Restore the last persisted review decision before publication moved into
-- the Spring service. The task's cached decision is derived from its matching
-- review_task + label approval history, not from label lifecycle alone.
UPDATE review_task rt
JOIN approval_record ar
  ON ar.review_task_id = rt.review_task_id
 AND ar.label_version_id = rt.target_label_version_id
LEFT JOIN approval_record newer
  ON newer.review_task_id = ar.review_task_id
 AND newer.label_version_id = ar.label_version_id
 AND (
      newer.decided_at > ar.decided_at
      OR (newer.decided_at = ar.decided_at
          AND newer.approval_record_id > ar.approval_record_id)
 )
SET rt.decision = ar.decision
WHERE newer.approval_record_id IS NULL;

-- A V6 approval closed the task before the separate publish action existed.
-- Reopen only approved, unpublished tasks whose matching final decision was
-- APPROVE. Rejected and already-published tasks stay closed.
UPDATE review_task rt
JOIN label_version lv
  ON lv.label_version_id = rt.target_label_version_id
SET rt.status = 'IN_REVIEW',
    rt.resolved_by_user_id = NULL,
    rt.resolved_at = NULL
WHERE rt.status = 'CLOSED'
  AND rt.decision = 'APPROVE'
  AND lv.lifecycle_status = 'APPROVED'
  AND NOT EXISTS (
      SELECT 1
      FROM publication_record pr
      WHERE pr.label_version_id = lv.label_version_id
  );

-- Preserve resolution details for rejected tasks from the approval history.
UPDATE review_task rt
JOIN approval_record ar
  ON ar.review_task_id = rt.review_task_id
 AND ar.label_version_id = rt.target_label_version_id
 AND ar.decision = 'REJECT'
LEFT JOIN approval_record newer
  ON newer.review_task_id = ar.review_task_id
 AND newer.label_version_id = ar.label_version_id
 AND (
      newer.decided_at > ar.decided_at
      OR (newer.decided_at = ar.decided_at
          AND newer.approval_record_id > ar.approval_record_id)
 )
SET rt.resolved_by_user_id = ar.decided_by_user_id,
    rt.resolved_at = ar.decided_at
WHERE rt.status = 'CLOSED'
  AND rt.decision = 'REJECT'
  AND newer.approval_record_id IS NULL;

-- Existing publications are already resolved even if V6 did not retain the
-- resolver columns. Keep their CLOSED status and restore supported metadata.
UPDATE review_task rt
JOIN publication_record pr
  ON pr.label_version_id = rt.target_label_version_id
JOIN label_version lv
  ON lv.label_version_id = pr.label_version_id
SET rt.resolved_by_user_id = pr.published_by_user_id,
    rt.resolved_at = pr.published_at
WHERE rt.status = 'CLOSED'
  AND lv.lifecycle_status = 'PUBLISHED';

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
