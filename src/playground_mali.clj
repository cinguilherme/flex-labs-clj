(ns playground-mali
  (:require [malli.core :as m] 
            [malli.generator :as g]))

(def Address [:map
   [:street string?]
   [:city string?]
   [:state string?]
   [:zip string?]])

(def Phone [:map
   [:number string?]
   [:type #{:home :work :mobile}]])

(def User
  [:map
   [:id int?]
   [:name string?]
   [:email string?]
   ;[:phone Phone]
   [:address Address]
   [:age int?]])

(take 5 (repeatedly (fn [] (g/generate User))))

(m/validate User (g/generate User))

