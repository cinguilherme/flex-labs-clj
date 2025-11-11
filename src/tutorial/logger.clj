(ns tutorial.logger
  (:require [integrant.core :as ig]
            [clojure.java.io :as io])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

;; Protocol definition

(defprotocol Logger
  "Protocol for logging operations"
  (info [logger event-name data]
    "Log an informational event with a name and associated data"))

;; Helper to format log messages

(defn- format-log-message [event-name data]
  (let [timestamp (.format (LocalDateTime/now)
                           (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))
        formatted-data (if (empty? data)
                        ""
                        (str " " (pr-str data)))]
    (format "[%s] [INFO] %s%s"
            timestamp
            (name event-name)
            formatted-data)))

;; Console Logger implementation

(defrecord ConsoleLogger []
  Logger
  (info [_logger event-name data]
    (println (format-log-message event-name data))))

;; File Logger implementation

(defrecord FileLogger [log-file]
  Logger
  (info [_logger event-name data]
    (let [message (str (format-log-message event-name data) "\n")]
      (try
        (spit log-file message :append true)
        (catch Exception e
          (println "Error writing to log file:" (.getMessage e)))))))

;; Integrant lifecycle

(defmethod ig/init-key :tutorial.logger/logger [_ options]
  (let [log-type (get options :type :console)
        logger (case log-type
                 :file (let [log-file (get options :log-file "./logs/logs.txt")]
                         ;; Ensure logs directory exists
                         (io/make-parents log-file)
                         (->FileLogger log-file))
                 :console (->ConsoleLogger)
                 (->ConsoleLogger))]
    (info logger :logger/started {:component :tutorial.logger/logger :type log-type})
    logger))

(defmethod ig/halt-key! :tutorial.logger/logger [_ logger]
  (info logger :logger/stopped {:component :tutorial.logger/logger}))

