(ns playground-hugs
  (:require [hugsql.core :as hugsql]))

(hugsql/def-db-fns "sql/queries.sql")

(def db-spec {:dbtype "sqlite" :dbname "test.db"})

(def db (jdbc/get-connection db-spec))

(def db (jdbc/get-connection db-spec))