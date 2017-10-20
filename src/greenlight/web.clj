(ns greenlight.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]
            [clojure.java.jdbc :as db])
  (:use [hiccup.core]))

;(defn splash []
;  {:status 200
;   :headers {"Content-Type" "text/html"}
;   :body (

(defn view-layout [& content]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (html content)})

(defn view-input []
  (view-layout
    [:h2 "add two numbers"]
    [:form {:method "post" :action "/"}
      [:input.math {:type "text" :name "a"}] [:span.math " + "]
      [:input.math {:type "text" :name "b"}] [:br]
      [:input.action {:type "submit" :value "add"}]]))

(defn view-output [a b sum]
  (view-layout
    [:h2 "two numbers added"]
    [:p.math a " + " b " = " sum]
    [:a.action {:href "/"} "add more numbers"]))

(defn parse-input [a b]
  [(Integer/parseInt a) (Integer/parseInt b)])

(defroutes app
   (GET "/" []
        (view-input))
   (POST "/" [a b]
         (let [[a b] (parse-input a b)
               sum (+ a b)]
           (view-output a b sum))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))
