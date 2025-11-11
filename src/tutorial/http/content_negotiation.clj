(ns tutorial.http.content-negotiation
  (:require [clojure.data.json :as json]))

;; Simple content negotiation helper

(defn format-response
  "Format data based on Accept header. Returns a complete response map."
  [request data]
  (let [accept-header (get-in request [:headers "accept"] "application/json")
        ;; Simple parsing - just check what's in the accept header
        content-type (cond
                       (re-find #"application/edn" accept-header) "application/edn"
                       (re-find #"text/plain" accept-header) "text/plain"
                       :else "application/json")
        body (case content-type
               "application/edn" (pr-str data)
               "text/plain" (str data)
               "application/json" (json/write-str data))]
    {:status 200
     :headers {"Content-Type" content-type}
     :body body}))
