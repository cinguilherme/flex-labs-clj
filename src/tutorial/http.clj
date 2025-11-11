(ns tutorial.http
  (:require [integrant.core :as ig]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.http.body-params :as body-params]
            [tutorial.db :as db]
            [tutorial.logger :as log]
            [tutorial.http.content-negotiation :as cn]))

;; Response helpers

(defn ok [body]
  {:status 200
   :body body})

(defn not-found []
  {:status 404
   :body "Not Found"})

(defn bad-request [message]
  {:status 400
   :body message})

;; Simple file logger for debugging interceptors
(defn log-debug [message]
  (try
    (spit "logs/interceptor-debug.log" 
          (str (java.time.LocalDateTime/now) " " message "\n")
          :append true)
    (catch Exception e
      (println "Failed to log:" (.getMessage e)))))

;; Interceptor to inject database and logger components
(defn component-interceptor [database logger]
  (interceptor/interceptor
   {:name ::components
    :enter (fn [context]
             (log-debug "=== COMPONENT-INTERCEPTOR ENTER ===")
             (log-debug (str "Request URI: " (get-in context [:request :uri])))
             (log-debug (str "Request method: " (get-in context [:request :request-method])))
             (-> context
                 (assoc-in [:request :database] database)
                 (assoc-in [:request :logger] logger)
                 ;; Also make logger available to other interceptors via context
                 (assoc :logger logger)))
    :leave (fn [context]
             (log-debug "=== COMPONENT-INTERCEPTOR LEAVE ===")
             (log-debug (str "Response status: " (get-in context [:response :status])))
             (log-debug (str "Response body type: " (type (get-in context [:response :body]))))
             (log-debug (str "Response body: " (pr-str (get-in context [:response :body]))))
             context)}))

;; Route handlers
(defn home-handler [request]
  (println "HOME-HANDLER CALLED")
  (log-debug "=== HOME-HANDLER CALLED ===")
  (log-debug (str "Request keys: " (keys request)))
  (let [response {:status 200
                  :headers {"Content-Type" "text/plain"}
                  :body "Hello from Pedestal!"}]
    (log-debug (str "Handler returning: " (pr-str response)))
    response))

(defn get-users-handler [request]
  (log-debug "=== GET-USERS-HANDLER CALLED ===")
  (try
    (let [database (:database request)
          logger (:logger request)]
      (log-debug (str "Has database: " (some? database)))
      (log-debug (str "Has logger: " (some? logger)))
      (when logger
        (log/info logger :http/get-users-called {:has-database (some? database)}))
      (if-not database
        {:status 500
         :body "Database not available"}
        (let [users (db/get-all-users (:datasource database))]
          (log-debug (str "Users count: " (count users)))
          (log-debug (str "Users type: " (type users)))
          (when logger
            (log/info logger :http/get-users {:count (count users)}))
          (let [response {:status 200
                         :body users}]
            (log-debug (str "Handler returning response: " (pr-str response)))
            response))))
    (catch Exception e
      (log-debug (str "ERROR in handler: " (.getMessage e)))
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
      {:status 200
       :body user}
      (not-found))))

(defn create-user-handler [request]
  (let [database (:database request)
        logger (:logger request)
        {:keys [name email]} (:json-params request)]
    (try
      (when logger
        (log/info logger :http/create-user {:email email}))
      (db/insert-user! (:datasource database) {:name name :email email} (:logger database))
      {:status 200
       :body {:message "User created"}}
      (catch clojure.lang.ExceptionInfo e
        (if (= (:type (ex-data e)) :tutorial.db/validation-error)
          (do
            (when logger
              (log/info logger :http/validation-error {:errors (:errors (ex-data e))}))
            {:status 400
             :body {:error "Validation failed"
                    :details (:errors (ex-data e))}})
          (throw e))))))


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
                                                                  cn/negotiate-content
                                                                  #_cn/coerce-body])
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

