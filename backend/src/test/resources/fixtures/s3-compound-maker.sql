-- TEST ONLY: execute in an owned, disposable MySQL fixture database.
-- This new subject combines existing roles; no existing actor, role or permission
-- row is changed. Do not install this fixture in a shared or production database.
INSERT INTO user_account
  (user_id,username,display_name,email,auth_provider,external_auth_subject,password_hash,is_active,created_at)
VALUES
  ('user_test_compound_maker_s3','test.compound.maker.s3','Isolated S3 Compound Maker',
   'test.compound.maker.s3@example.invalid','DEV_EXTERNAL',
   'dev-external-test-compound-maker-s3',NULL,'Y','2026-10-08 00:00:00');

INSERT INTO user_role (user_id,role_id,assigned_at) VALUES
  ('user_test_compound_maker_s3','role_label_officer','2026-10-08 00:00:00'),
  ('user_test_compound_maker_s3','role_approver','2026-10-08 00:00:00');
