(ns tutorial.http
  (:require [integrant.core :as ig]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.content-negotiation :as content-negotiation]
            [clojure.data.json :as json]
            [tutorial.db :as db]
            [tutorial.logger :as log]))

(defn ok [body]
  {:status 200
   :body body})

(defn json-response [data]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/write-str data)})

(defn not-found []
  {:status 404
   :body "Not Found"})

(defn bad-request [message]
  {:status 400
   :body message})

;; Interceptor to inject database and logger components
(defn component-interceptor [database logger]
  (interceptor/interceptor
   {:name ::components
    :enter (fn [context]
             (-> context
                 (assoc-in [:request :database] database)
                 (assoc-in [:request :logger] logger)))}))

;; Route handlers
(defn home-handler [_request]
  (ok "Hello from Pedestal!"))

(defn get-users-handler [request]
  (try
    (let [database (:database request)
          logger (:logger request)]
      (when logger
        (log/info logger :http/get-users-called {:has-database (some? database)}))
      (if-not database
        {:status 500
         :body "Database not available"}
        (let [users (db/get-all-users (:datasource database))]
          (when logger
            (log/info logger :http/get-users {:count (count users)}))
          (json-response users))))
    (catch Exception e
      (println "ERROR in get-users-handler:" (.getMessage e))
      (.printStackTrace e)
      {:status 500
       :body (str "Error: " (.getMessage e))})))

(defn get-user-handler [request]
  (let [database (:database request)
        logger (:logger request)
        id (Long/parseLong (get-in request [:path-params :id]))
        user (db/get-user-by-id (:datasource database) id)]
    (when logger
      (log/info logger :http/get-user {:id id :found (some? user)}))
    (if user
      (json-response user)
      (not-found))))

(defn create-user-handler [request]
  (let [database (:database request)
        logger (:logger request)
        {:keys [name email]} (:json-params request)]
    (try
      (when logger
        (log/info logger :http/create-user {:email email}))
      (db/insert-user! (:datasource database) {:name name :email email} (:logger database))
      (json-response {:message "User created"})
      (catch clojure.lang.ExceptionInfo e
        (if (= (:type (ex-data e)) :tutorial.db/validation-error)
          (do
            (when logger
              (log/info logger :http/validation-error {:errors (:errors (ex-data e))}))
            {:status 400
             :headers {"Content-Type" "application/json"}
             :body (json/write-str {:error "Validation failed"
                                    :details (:errors (ex-data e))})})
          (throw e))))))

;; Content negotiation interceptor
(def content-type-interceptor
  (content-negotiation/negotiate-content ["application/json" "text/html"]))

;; JSON body params interceptor
(def json-body-interceptor
  (body-params/body-params))

;; Routes
(defn routes []
  (route/expand-routes
   #{["/" :get home-handler :route-name :home]
     ["/users" :get get-users-handler :route-name :get-users]
     ["/users/:id" :get get-user-handler :route-name :get-user]
     ["/users" :post [json-body-interceptor create-user-handler] :route-name :create-user]}))

(defmethod ig/init-key :tutorial.http/server [_ {:keys [database logger port]}]
  (let [actual-port (or port 8080)
        server-config {::http/routes (routes)
                       ::http/type :jetty
                       ::http/port actual-port
                       ::http/join? false}
        server-instance (-> server-config
                            http/default-interceptors
                            (update ::http/interceptors concat [(component-interceptor database logger)
                                                                  content-type-interceptor])
                            http/create-server)]
    (when logger
      (log/info logger :http/server-starting {:port actual-port}))
    (let [started-server (http/start server-instance)]
      (when logger
        (log/info logger :http/server-started {:port actual-port}))
      started-server)))

(defmethod ig/halt-key! :tutorial.http/server [_ server]
  ;; Note: logger not available in halt, could be passed through server metadata if needed
  (http/stop server))

