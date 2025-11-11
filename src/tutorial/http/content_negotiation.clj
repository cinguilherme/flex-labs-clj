(ns tutorial.http.content-negotiation
  (:require [io.pedestal.http.content-negotiation :as cn]
            [clojure.data.json :as json]
            [tutorial.logger :as log]))

;; Supported content types
(def supported-types ["application/json" "application/edn" "text/plain"])

;; Coercion functions

(defn- coerce-to-json [body]
  (json/write-str body))

(defn- coerce-to-edn [body]
  (pr-str body))

(defn- coerce-to-text [body]
  (str body))

(defn- coerce-body-by-type
  "Coerce body to the appropriate format based on accepted content type"
  [body accepted-type]
  (cond
    (= accepted-type "application/json") (coerce-to-json body)
    (= accepted-type "application/edn") (coerce-to-edn body)
    (= accepted-type "text/plain") (coerce-to-text body)
    :else (coerce-to-json body))) ;; Default to JSON

;; Interceptors

(def negotiate-content
  "Pedestal content negotiation interceptor"
  (cn/negotiate-content supported-types))

(def coerce-body
  "Interceptor to coerce response body based on accepted content type"
  {:name ::coerce-body
   :enter (fn [context]
            (when-let [logger (:logger context)]
              (log/info logger :coerce-body/enter {:uri (get-in context [:request :uri])}))
            context)
   :leave (fn [context]
            (let [logger (:logger context)]
              (try
                (when logger
                  (log/info logger :coerce-body/leave-start {}))
                
                (let [response (:response context)
                      body (:body response)]
                  
                  (when logger
                    (log/info logger :coerce-body/inspect {:has-response (some? response)
                                                            :has-body (some? body)
                                                            :body-type (type body)}))
                  
                  (cond
                    ;; No response or no body - pass through
                    (or (nil? response) (nil? body))
                    (do
                      (when logger
                        (log/info logger :coerce-body/pass-through {:reason "no-response-or-body"}))
                      context)
                    
                    ;; Body is already a string - pass through
                    (string? body)
                    (do
                      (when logger
                        (log/info logger :coerce-body/pass-through {:reason "body-is-string" :length (count body)}))
                      context)
                    
                    ;; Body needs coercion
                    :else
                    (let [accepted (get-in context [:request :accept :field] "application/json")
                          _ (when logger
                              (log/info logger :coerce-body/coercing {:accept accepted}))
                          ;; Realize lazy sequences from JDBC
                          realized-body (cond
                                          (seq? body) (vec body)
                                          (instance? clojure.lang.ISeq body) (vec body)
                                          :else body)
                          coerced-body (coerce-body-by-type realized-body accepted)
                          result (-> context
                                     (assoc-in [:response :body] coerced-body)
                                     (assoc-in [:response :headers "Content-Type"] accepted))]
                      (when logger
                        (log/info logger :coerce-body/complete {:coerced-length (count coerced-body)}))
                      result)))
                (catch Exception e
                  (when logger
                    (log/info logger :coerce-body/error {:message (.getMessage e)}))
                  (println "ERROR in coerce-body:" (.getMessage e))
                  (.printStackTrace e)
                  ;; Return context unchanged on error
                  context))))})
