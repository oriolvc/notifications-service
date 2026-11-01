-- Tenant 101: an active and an inactive EMAIL list (with members, below) for REGULATORY_DOCUMENT_MATCHED.
-- Tenant 102: one active EMAIL list.
-- Explicit ids far above the sequences, so lists created by the test never collide.
INSERT INTO distribution_lists (id, customer_id, name, notification_type_id, channel, active, version, created_at, created_by, updated_at, updated_by)
SELECT v.id, v.customer_id, v.name, t.id, 'EMAIL', v.active, 0, now(), 'fixture', now(), 'fixture'
FROM notification_types t,
     (VALUES (1001, 101, 'Compliance team', true),
             (1002, 101, 'Old compliance team', false),
             (2001, 102, 'Quality alerts', true)) AS v (id, customer_id, name, active)
WHERE t.code = 'REGULATORY_DOCUMENT_MATCHED';

-- List 1001: two active members and a removed one. List 1002 (inactive): one active member. List 2001: one member.
INSERT INTO distribution_list_members (id, distribution_list_id, address, active, version, created_at, created_by, updated_at, updated_by)
VALUES (1101, 1001, 'alice@customer101.example', true, 0, now(), 'fixture', now(), 'fixture'),
       (1102, 1001, 'bob@customer101.example', true, 0, now(), 'fixture', now(), 'fixture'),
       (1103, 1001, 'carol@customer101.example', false, 0, now(), 'fixture', now(), 'fixture'),
       (1201, 1002, 'old@customer101.example', true, 0, now(), 'fixture', now(), 'fixture'),
       (2101, 2001, 'dave@customer102.example', true, 0, now(), 'fixture', now(), 'fixture');
