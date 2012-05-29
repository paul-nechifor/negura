-- Store configuration settings as key-value pairs.
DROP TABLE IF EXISTS settings CASCADE;
CREATE TABLE settings (
    key TEXT PRIMARY KEY,
    value TEXT
);

DROP TABLE IF EXISTS operations CASCADE;
CREATE TABLE operations (
    oid INTEGER PRIMARY KEY,
    path TEXT,
    newpath TEXT,
    signature TEXT,
    dat INTEGER,
    size BIGINT,
    hash TEXT,
    type VARCHAR(6),
    firstbid INTEGER,       -- The ID of the first block.
    lastbid INTEGER         -- The ID of the last block.
);
DROP SEQUENCE IF EXISTS operation_seq;
CREATE SEQUENCE operation_seq START 1;


-- The current blocks of the file system. 
DROP TABLE IF EXISTS blocks CASCADE;
CREATE TABLE blocks (
    bid INTEGER PRIMARY KEY,
    hash TEXT DEFAULT NULL
);
-- The sequence used for new block IDs.
DROP SEQUENCE IF EXISTS block_seq;
CREATE SEQUENCE block_seq START 1;


-- The registered users.
DROP TABLE IF EXISTS users CASCADE;
CREATE TABLE users (
    uid INTEGER PRIMARY KEY,
    ip TEXT,
    port INTEGER,
    nblocks INTEGER,        -- The number of blocks the user will store.
    pkey TEXT,              -- The public key in base64.
    newindex INTEGER        -- The new index to be used in the allocation list.
);
-- The sequence used for new user IDs.
DROP SEQUENCE IF EXISTS user_seq;
CREATE SEQUENCE user_seq START 1;


-- The allocated blocks for each user.
DROP TABLE IF EXISTS allocated CASCADE;
CREATE TABLE allocated (
    uid INTEGER REFERENCES users(uid) ON DELETE CASCADE,
    bid INTEGER REFERENCES blocks(bid) ON DELETE CASCADE
);


-- The completed blocks for each user.
DROP TABLE IF EXISTS completed CASCADE;
CREATE TABLE completed (
    uid INTEGER REFERENCES users(uid) ON DELETE CASCADE,
    bid INTEGER REFERENCES blocks(bid) ON DELETE CASCADE
);


-- The allocation list.
DROP TABLE IF EXISTS alist CASCADE;
CREATE TABLE alist (
    uid INTEGER                  -- The ID of the user.
        REFERENCES users(uid)
        ON DELETE CASCADE,
    bid INTEGER,                -- If pozitive, the ID of the block beeing
                                -- added. If negative, the negative of the
                                -- block being removed.
    orderb INTEGER              -- The order or the elements in the list. Starts
                                -- at 1 for each user.
);

DROP TABLE IF EXISTS tempblocks CASCADE;
CREATE TABLE tempblocks (
    uid INTEGER REFERENCES users(uid) ON DELETE CASCADE,
    bid INTEGER REFERENCES blocks(bid) ON DELETE CASCADE,
    remove INTEGER              -- Date in UNIX timestamp when to remove this.
);
