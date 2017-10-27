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
     [:span "Member ID"] [:input {:type "number" :name "member_id"}] [:br]
     [:span "9 holes"] [:input {:type "radio" :name "holes" :value "9"}] [:br]
     [:span "18 holes"] [:input {:type "radio" :name "holes" :value "18"}] [:br]
     [:input {:type "submit" :value "Validate member"}]]))

(defn validate [table id]
  (not-empty (db/find-by-keys
               (env :database-url)
               table
               {:id id})))

(defn view-confirmation [course member holes]
  (view-layout
    [:span (get (db/get-by-id (env :database-url) :members member) :name)]
    [:span " is going to play "]
    [:span holes]
    [:span " holes at "]
    [:span (get (db/get-by-id (env :database-url) :courses course) :name)]
    [:span ". Is this correct?"] [:br]
    [:form {:method "post" :action "/"}
     [:input {:type "hidden" :name "course" :value course}]
     [:input {:type "hidden" :name "member" :value member}]
     [:input {:type "hidden" :name "holes" :value holes}]
     [:input {:type "submit" :value "Yeah let's go!"}]]
    [:form {:method "get" :action "/"}
     [:input {:type "submit" :value "Something's not right."}]]))

(defn go-back [content]
  (view-layout
    [:span content] [:br]
    [:a {:href "https://greenlight-courses.herokuapp.com"} "go back"]))

(defn view-bad-input [course_id course_email member_id member_email]
  (let [course (db/get-by-id (env :database-url) :courses course_id)
        member (db/get-by-id (env :database-url) :members member_id)]
    (cond (empty? course) (go-back "There is no course with that ID")
          (not (= (get course :contact_email) course_email))
          (go-back "Course email does not match ID")
          (empty? member) (go-back "There is no member with that ID")
          (not (= (get member :contact_email) member_email))
          (go-back "Member email does not match ID")
          :else (go-back "Unkown error!"))))

(defroutes app
   (GET "/" []
        (view-input))
   (POST "/confirmation" [course_id member_id holes]
         (let [course (Integer/parseInt course_id)
               member (Integer/parseInt member_id)
               number_holes (Integer/parseInt holes)]
           (if (and (validate :courses course_id)
                    (validate :members member_id))
             (view-confirmation course member number_holes)
             (view-bad-input course course_email member member_email))))
   (POST "/" [course member holes]
         (let [course (Integer/parseInt course)
               member (Integer/parseInt member)
               holes (Integer/parseInt holes)
               course_map (db/get-by-id (env :database-url) :courses course)
               member_map (db/get-by-id (env :database-url) :members member)
               course_holes (get course_map :holes)
               course_holes_this_week (get course_map :holes_this_week)
               member_holes (get member_map :holes)
               member_holes_this_week (get member_map :holes_this_week)]
           (db/update!
             (env :database-url)
             :courses
             {:holes (+ holes course_holes)}
             ["id = ?" course])
           (db/update!
             (env :database-url)
             :courses
             {:holes_this_week (+ holes course_holes_this_week)}
             ["id = ?" course])
           (db/update!
             (env :database-url)
             :members
             {:holes (+ holes member_holes)}
             ["id = ?" member])
           (db/update!
             (env :database-url)
             :members
             {:holes_this_week (+ holes member_holes_this_week)}
             ["id = ?" member])
           (view-input))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))
