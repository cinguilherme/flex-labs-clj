(ns playground-clj-sql
  (:require [clojure.java.jdbc :as jdbc]))

(def db-spec {:dbtype "sqlite" :dbname "test.db"})

(def db (jdbc/get-connection db-spec))
