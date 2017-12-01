(ns greenlight.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]
            [clojure.java.jdbc :as db])
  (:use [hiccup.core]
        [byte-streams]))

(def max-holes 36)

(defn get-member-name [id]
  (get
    (first
      (db/find-by-keys
        (env :database-url)
        :contacts
        {:email (get (db/get-by-id (env :database-url) :members id) :contact_email)}))
    :name))

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
     [:span "Course ID"] [:input {:type "number" :name "course-id"}] [:br]
     [:span "Member ID"] [:input {:type "number" :name "member-id"}] [:br]
     [:span "9 holes"] [:input {:type "radio" :name "holes" :value "9"}] [:br]
     [:span "18 holes"] [:input {:type "radio" :name "holes" :value "18"}] [:br]
     [:input {:type "submit" :value "Validate member"}]]))

(defn view-confirmation [course member holes]
  (view-layout
    [:span (get-member-name member)]
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
   (POST "/confirmation" [course-id member-id holes]
         (let [course (Integer/parseInt course-id)
               member (Integer/parseInt member-id)
               number-holes (Integer/parseInt holes)
               holes-map (first (db/find-by-keys
                                  (env :database-url)
                                  :holes_remaining
                                  {:member member :course course}))
               course-name (get (db/get-by-id
                                  (env :database-url)
                                  :courses
                                  course)
                                :name)]
           (cond (empty? (db/get-by-id (env :database-url) :courses course))
                 (go-back "There is no course with that ID.")
                 (empty? (db/get-by-id (env :database-url) :members member))
                 (go-back "There is no member with that ID.")
                 (empty? holes-map) (do
                                      (println "No record found in holes_remaining, adding max-holes")
                                      (db/insert!
                                        (env :database-url)
                                        :holes_remaining
                                        {:member member :course course :holes_remaining max-holes})
                                      (view-confirmation
                                        course
                                        member
                                        number-holes))
                 (= (get holes-map :holes_remaining) 0)
                 (go-back (apply str [(get-member-name member)
                                      " has already played "
                                      max-holes
                                      " holes at "
                                      course-name
                                      "."]))
                 (< (- (get holes-map :holes_remaining) number-holes) 0)
                 (go-back (apply str [(get-member-name member)
                                      " can only play "
                                      (get holes-map :holes_remaining)
                                      " more holes at "
                                      course-name
                                      "."]))
                 :else (view-confirmation course member number-holes))))
   (POST "/" [course member holes]
         (let [course (Integer/parseInt course)
               member (Integer/parseInt member)
               holes (Integer/parseInt holes)
               holes-remaining (get (first (db/find-by-keys
                                             (env :database-url)
                                             :holes_remaining
                                             {:member member :course course}))
                                             :holes_remaining)]
           (println holes-remaining "-" holes "=" (- holes-remaining holes))
           (db/insert!
             (env :database-url)
             :transactions
             {:member member :course course :holes holes})
           (db/update!
             (env :database-url)
             :holes_remaining
             {:holes_remaining (- holes-remaining holes)}
             ["course = ? and member = ?" course member])
           (view-input)))
   (GET "/resources" [email id]
        (let [id (Integer/parseInt id)
              member (db/get-by-id (env :database-url) :members id)
              picture (get member :picture)]
          (cond (empty? member)
                "There is no member with that ID.\n"
                (not (= (get member :contact_email) email))
                "ID and email don't match.\n"
                (empty? picture)
                ""
                :else (transfer picture (java.io.File. (str "/tmp/" id ".jpg")))))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))
