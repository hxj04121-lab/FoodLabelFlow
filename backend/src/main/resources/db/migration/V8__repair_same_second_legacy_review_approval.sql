-- V7 stays unchanged for databases that already applied it. V6 decision times
-- have second precision and approval IDs may be random UUIDs, so V7's UUID
-- tie-break can select an earlier REQUEST_CHANGES instead of the final APPROVE.
-- Recover only the supported legacy state: CLOSED + APPROVED + unpublished,
-- a matching APPROVE at the greatest decision time, and the cached earlier
-- REQUEST_CHANGES at that same time. The terminal label state disambiguates
-- this valid history; UUID ordering is not evidence of chronology.
-- Resolved, rejected, published, missing-approval, or later-decision tasks stay
-- unchanged. No approval history, label, publication, or audit row is rewritten.
UPDATE review_task rt
JOIN label_version lv
  ON lv.label_version_id = rt.target_label_version_id
SET rt.decision = 'APPROVE',
    rt.status = 'IN_REVIEW',
    rt.resolved_by_user_id = NULL,
    rt.resolved_at = NULL
WHERE rt.status = 'CLOSED'
  AND rt.decision = 'REQUEST_CHANGES'
  AND rt.resolved_by_user_id IS NULL
  AND rt.resolved_at IS NULL
  AND rt.target_label_version_id = rt.draft_label_version_id
  AND lv.lifecycle_status = 'APPROVED'
  AND lv.is_current_published = 'N'
  AND NOT EXISTS (
      SELECT 1 FROM publication_record pr
      WHERE pr.label_version_id = lv.label_version_id
  )
  AND NOT EXISTS (
      SELECT 1 FROM approval_record rejected
      WHERE rejected.review_task_id = rt.review_task_id
        AND rejected.label_version_id = rt.target_label_version_id
        AND rejected.decision = 'REJECT'
  )
  AND EXISTS (
      SELECT 1
      FROM approval_record approved
      JOIN approval_record requested
        ON requested.review_task_id = approved.review_task_id
       AND requested.label_version_id = approved.label_version_id
       AND requested.decided_at = approved.decided_at
       AND requested.decision = 'REQUEST_CHANGES'
      WHERE approved.review_task_id = rt.review_task_id
        AND approved.label_version_id = rt.target_label_version_id
        AND approved.decision = 'APPROVE'
        AND NOT EXISTS (
            SELECT 1 FROM approval_record later
            WHERE later.review_task_id = approved.review_task_id
              AND later.label_version_id = approved.label_version_id
              AND later.decided_at > approved.decided_at
        )
  );
