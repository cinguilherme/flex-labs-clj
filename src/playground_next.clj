(ns playground-next
  (:require [next.jdbc :as jdbc]))

(def db-spec {:dbtype "sqlite" :dbname "test.db"})

(def db (jdbc/get-connection db-spec))

