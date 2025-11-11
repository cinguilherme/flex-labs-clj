(ns tutorial.db
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [malli.core :as m]
            [malli.error :as me]
            [tutorial.logger :as log]))

;; Malli Schemas

(def User
  "Schema for a User entity"
  [:map
   [:id {:optional true} :int]
   [:name [:string {:min 1 :max 100}]]
   [:email [:re {:error/message "must be a valid email"}
            #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"]]
   [:created_at {:optional true} :any]])

(def UserInput
  "Schema for user input (without id and created_at)"
  [:map
   [:name [:string {:min 1 :max 100}]]
   [:email [:re {:error/message "must be a valid email"}
            #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"]]])

(def UserUpdate
  "Schema for updating a user"
  [:map
   [:id :int]
   [:name [:string {:min 1 :max 100}]]
   [:email [:re {:error/message "must be a valid email"}
            #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"]]])

;; Validation helpers

(defn validate
  "Validate data against a schema. Returns data if valid, throws ex-info if invalid."
  ([schema data]
   (validate schema data nil))
  ([schema data logger]
   (if (m/validate schema data)
     data
     (let [errors (me/humanize (m/explain schema data))]
       (when logger
         (log/info logger :validation/failed {:schema (pr-str schema)
                                               :data data
                                               :errors errors}))
       (throw (ex-info "Validation failed"
                       {:type ::validation-error
                        :errors errors}))))))

(defn valid?
  "Check if data is valid according to schema"
  [schema data]
  (m/validate schema data))

;; Database operations using next.jdbc

(defn create-users-table!
  "Create users table if it doesn't exist"
  [ds]
  (jdbc/execute! ds
                 ["CREATE TABLE IF NOT EXISTS users (
                     id INTEGER PRIMARY KEY AUTO_INCREMENT,
                     name VARCHAR(100) NOT NULL,
                     email VARCHAR(100) NOT NULL UNIQUE,
                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                   )"]))

(defn insert-user!
  "Insert a new user. Validates input before inserting."
  ([ds user-data]
   (insert-user! ds user-data nil))
  ([ds user-data logger]
   (let [validated (validate UserInput user-data logger)
         result (sql/insert! ds :users validated)]
     (when logger
       (log/info logger :user/inserted {:email (:email validated)}))
     result)))

(defn get-user-by-id
  "Get a user by id"
  [ds id]
  (sql/get-by-id ds :users id))

(defn get-all-users
  "Get all users"
  [ds]
  (jdbc/execute! ds ["SELECT * FROM users ORDER BY created_at DESC"]))

(defn delete-user!
  "Delete a user by id"
  ([ds id]
   (delete-user! ds id nil))
  ([ds id logger]
   (let [result (sql/delete! ds :users {:id id})]
     (when logger
       (log/info logger :user/deleted {:id id}))
     result)))

(defn update-user!
  "Update a user's name and email. Validates input before updating."
  ([ds user-data]
   (update-user! ds user-data nil))
  ([ds user-data logger]
   (let [validated (validate UserUpdate user-data logger)
         {:keys [id name email]} validated
         result (sql/update! ds :users {:name name :email email} {:id id})]
     (when logger
       (log/info logger :user/updated {:id id :email email}))
     result)))

;; Integrant lifecycle

(defmethod ig/init-key :tutorial.db/database [_ options]
  (let [spec (or (:spec options) 
                 {:dbtype "h2"
                  :dbname "./test.db"})
        ds (jdbc/get-datasource spec)
        logger (:logger options)]
    ;; Initialize the users table on startup
    (create-users-table! ds)
    (when logger
      (log/info logger :database/initialized {:dbtype (:dbtype spec)
                                               :dbname (:dbname spec)}))
    {:datasource ds
     :spec spec
     :logger logger}))

(defmethod ig/halt-key! :tutorial.db/database [_ {:keys [datasource logger]}]
  (when logger
    (log/info logger :database/shutdown {}))
  (when datasource
    ;; Only close if the datasource is actually closeable
    ;; Simple connection specs from get-datasource don't always need explicit closing
    (when (instance? java.io.Closeable datasource)
      (.close datasource))))

