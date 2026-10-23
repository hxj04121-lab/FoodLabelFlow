-- S4 exposes the existing Change Manager and Data Admin demo identities and
-- grants both roles read-only access to the label audit history view.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM `role` r
JOIN permission p ON p.permission_code = 'AUDIT.READ'
WHERE r.role_code IN ('CHANGE_MANAGER', 'ADMIN_DATA_MAINTENANCE');
