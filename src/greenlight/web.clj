(ns greenlight.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]
            [clojure.java.jdbc :as db])
  (:use [hiccup.core]))

;; Return 200 status and use hiccup to render html.
(defn view-layout [& content]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (html content)})

;; Splash page takes course representative input.
(defn view-input []
  (view-layout
    [:h2 "Welcome to the Greenlight Courses app"]
    [:form {:method "post" :action "/confirmation"}
      [:span "Course ID"] [:input {:type "number" :name "course_id"}] [:br]
      [:span "Course email"] [:input {:type "email" :name "course_email"}] [:br]
      [:span "Member ID"] [:input {:type "number" :name "member_id"}] [:br]
      [:span "Member email"] [:input {:type "email" :name "member_email"}] [:br]
      [:span "Number of holes"] [:input {:type "number" :name "holes"}] [:br]
      [:input {:type "submit" :value "Validate member"}]]))

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
