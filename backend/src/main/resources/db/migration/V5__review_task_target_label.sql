ALTER TABLE review_task
    ADD COLUMN target_label_version_id VARCHAR(120) NULL
    AFTER draft_label_version_id;

ALTER TABLE review_task
    ADD CONSTRAINT fk_review_task_target_label_version
    FOREIGN KEY (target_label_version_id)
    REFERENCES label_version(label_version_id);

CREATE INDEX idx_review_task_target_label_version
    ON review_task(target_label_version_id);