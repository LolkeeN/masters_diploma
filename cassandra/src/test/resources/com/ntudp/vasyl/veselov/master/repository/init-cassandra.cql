CREATE KEYSPACE IF NOT EXISTS test
    WITH replication = {
        'class': 'SimpleStrategy',
        'replication_factor': 1
        };

USE test;

CREATE TYPE IF NOT EXISTS friend_info (
                                          id text,
                                          username text,
                                          email text,
                                          phone text,
                                          address text,
                                          salary decimal,
                                          birth_date date
                                      );

CREATE TABLE IF NOT EXISTS user (
                                    id text PRIMARY KEY,
                                    username text,
                                    email text,
                                    phone text,
                                    address text,
                                    salary decimal,
                                    birth_date date,
                                    friends set<frozen<friend_info>>
);