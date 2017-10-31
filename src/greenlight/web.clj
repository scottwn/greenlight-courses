(ns greenlight.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]
            [clojure.java.jdbc :as db])
  (:use [hiccup.core]))

(def max_holes 36)

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

(defroutes app
   (GET "/" []
        (view-input))
   (POST "/confirmation" [course_id member_id holes]
         (let [course (Integer/parseInt course_id)
               member (Integer/parseInt member_id)
               number_holes (Integer/parseInt holes)
               holes_map (db/find-by-keys
                           (env :database-url)
                           :holes_remaining
                           {:member member :course course})]
           (cond (empty? (db/get-by-id (env :database-url) :courses course))
                 (go-back "There is no course with that ID.")
                 (empty? (db/get-by-id (env :database-url) :members member))
                 (go-back "There is no member with that ID.")
                 (empty? holes_map) (do
                                      (db/insert!
                                        (env :database_url)
                                        :holes_remaining
                                        [member course max_holes])
                                      (view-confirmation
                                        course
                                        member
                                        number_holes))
                 (< (- (get holes_map :holes_remaining) number_holes) 0)
                 (go-back (apply str [(get
                                        (db/get-by-id
                                          (env :database-url)
                                          :members
                                          member)
                                        :name)
                                      " can only play "
                                      (get holes_map :holes_remaining)
                                      " more holes at "
                                      (get (db/get-by-id
                                             (env :database-url)
                                             :courses
                                             course)
                                           :name)
                                      "."]))
                 :else (view-confirmation course member number_holes))))
   (POST "/" [course member holes]
         (let [course (Integer/parseInt course)
               member (Integer/parseInt member)
               holes (Integer/parseInt holes)
               holes_remaining (get
                                 (db/find-by-keys
                                   (env :database-url)
                                   :holes_remaining
                                   {:member member :course course})
                                 :holes_remaining)]
           (db/insert!
             (env :database-url)
             :transactions
             [:member :course :holes]
             [member course holes])
           (db/update!
             (env :database-url)
             :holes_remaining
             {:holes_remaining (- holes_remaining holes)}
             ["course = ?" course "member = ?" member])
           (view-input))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))
