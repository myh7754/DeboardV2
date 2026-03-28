-- DummyDataLoader bypass: inserts 1 row so count > 0 check passes and skips 100k insert
INSERT IGNORE INTO users (email, nickname, password, provider, role)
VALUES ('_test_placeholder@test.com', '_placeholder', 'placeholder', NULL, 'ROLE_MEMBER');
