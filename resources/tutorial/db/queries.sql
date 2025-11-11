-- :name create-users-table! :!
-- :doc Create users table if it doesn't exist
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- :name insert-user! :! :n
-- :doc Insert a new user
INSERT INTO users (name, email)
VALUES (:name, :email);

-- :name get-user-by-id :? :1
-- :doc Get a user by id
SELECT * FROM users WHERE id = :id;

-- :name get-all-users :? :*
-- :doc Get all users
SELECT * FROM users ORDER BY created_at DESC;

-- :name delete-user! :! :n
-- :doc Delete a user by id
DELETE FROM users WHERE id = :id;

-- :name update-user! :! :n
-- :doc Update a user's name and email
UPDATE users
SET name = :name, email = :email
WHERE id = :id;

