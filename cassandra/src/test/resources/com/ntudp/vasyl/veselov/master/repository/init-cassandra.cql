CREATE KEYSPACE mykeyspace WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TYPE IF NOT EXISTS mykeyspace.friend_info (
                                          id text,
                                          username text,
                                          email text,
                                          phone text,
                                          address text,
                                          salary decimal,
                                          birth_date timestamp
                                      );