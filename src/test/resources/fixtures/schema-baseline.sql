-- One active EMAIL list with one member for customer 101, and one notification with a delivery.
-- Explicit ids far above the identity sequences, so rows inserted by the test never collide.
INSERT INTO distribution_lists (id, customer_id, name, notification_type_id, channel, active, created_at, created_by, updated_at, updated_by)
SELECT 1001, 101, 'Compliance team', id, 'EMAIL', true, now(), 'test', now(), 'test'
FROM notification_types WHERE code = 'REGULATORY_DOCUMENT_MATCHED';

INSERT INTO distribution_list_members (id, distribution_list_id, address, active, created_at, created_by, updated_at, updated_by)
VALUES (2001, 1001, 'Quality@Customer101.example', true, now(), 'test', now(), 'test');

INSERT INTO notifications (id, notification_type_id, event_key, customer_id, parameters, outcome, created_at, created_by)
SELECT 3001, id, repeat('a', 64), 101, '{}'::jsonb, 'ACCEPTED', now(), 'detection-process'
FROM notification_types WHERE code = 'REGULATORY_DOCUMENT_MATCHED';

INSERT INTO notification_deliveries (id, notification_id, distribution_list_id, distribution_list_member_id, channel, address, template_id, subject, body, status, next_attempt_at, created_at, updated_at)
SELECT 4001, 3001, 1001, 2001, 'EMAIL', 'Quality@Customer101.example', t.id, 'subject', 'body', 'PENDING', now(), now(), now()
FROM notification_templates t WHERE t.channel = 'EMAIL';
