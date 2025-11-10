(ns tutorial.print-test
  (:require [clojure.test :refer [deftest is]]
            [tutorial.print :as tp]
            [duct.test :as dt]))

(deftest unit-test
  (is (= "Hello World Hey!\n"
         (with-out-str (tp/hello {})))))

(deftest system-test
  (is (= "Hello World Hey!\nGoodbye.\n"
         (with-out-str
           (dt/with-system [_sys (dt/run)]
             (println "Goodbye."))))))