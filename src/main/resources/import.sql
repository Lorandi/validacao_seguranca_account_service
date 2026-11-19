-- Usuários de demonstração. Senhas: admin123, customer123 e auditor123.
INSERT INTO app_user (username, passwordHash, role)
VALUES ('admin', '$2a$12$aoRzCNjmAip3tIaNhVS0POadgO7ZCvYvLzgrAfXiz.02R9AcPFm6q', 'ADMIN');

INSERT INTO app_user (username, passwordHash, role)
VALUES ('customer', '$2a$12$u8KTGGKVypljSGxwh8gUj.cPbRQLW.SHTqgKA0KOnmwAf85iRjSBu', 'CUSTOMER');

INSERT INTO app_user (username, passwordHash, role)
VALUES ('auditor', '$2a$12$OP37kC2.EIHFu7mvVbipdOKeFRsuXf2IFHUwUHFBp0UkDeMiDOE0K', 'AUDITOR');

INSERT INTO app_user (username, passwordHash, role)
VALUES ('customer_active', '$2a$12$RncL4x5pCoGM7lsUXbHYsOWZVPy8kzhPYcPiqsXoxZj1n7R23uyOi', 'CUSTOMER');

INSERT INTO app_user (username, passwordHash, role)
VALUES ('customer_blocked', '$2a$12$6w9gRfP.edWezPqMmMt4.Oq9ul6W0NcQG/swSQUiwBGtM/neGpR5S', 'CUSTOMER');

-- Contas bancárias de demonstração, associadas aos usuários acima.

INSERT INTO account (accountId, balance, status, ownerId)
VALUES ( 'ACC1001', 1500.00, 'ACTIVE', 1);

INSERT INTO account (accountId, balance, status, ownerId)
VALUES ('ACC1002', 2750.50, 'ACTIVE', 2);

INSERT INTO account (accountId, balance, status, ownerId)
VALUES ('ACC1003', 0.00, 'BLOCKED', 3);

INSERT INTO account (accountId, balance, status, ownerId)
VALUES ('ACC2001', 12500.75, 'ACTIVE', 99);

INSERT INTO account (accountId, balance, status, ownerId)
VALUES ('ACC1004', 500.00, 'ACTIVE', 4);

INSERT INTO account (accountId, balance, status, ownerId)
VALUES ('ACC1005', 500.00, 'BLOCKED', 5);
