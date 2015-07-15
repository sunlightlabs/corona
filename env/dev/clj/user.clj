(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer [javadoc]]
   [clojure.pprint :refer [pprint]]
   [clojure.reflect :refer [reflect]]
   [clojure.repl :refer [apropos dir doc find-doc pst source]]
   [clojure.set :as set]
   [environ.core :refer [env]]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   [corona.server :refer [new-webserver]]
   [corona.dev :refer [new-figwheel]]
   [com.stuartsierra.component :as component]
   [corona.dev :refer [browser-repl]]
   ))


(def system
  "A Var containing an object representing the application under
  development."
  nil)

(defn init
  "Creates and initializes the system under development in the Var
  #'system."
  []
  (let [port (Integer. (or (env :port) 10555))
        is-dev? (env :is-dev)
        system
        (component/system-map
         :webserver (new-webserver port is-dev?)
         :figwheel (new-figwheel is-dev?))]
    (alter-var-root #'system (constantly system))))

(defn start
  "Starts the system running, updates the Var #'system."
  []
  (alter-var-root #'system component/start))

(defn stop
  "Stops the system if it is currently running, updates the Var #'system."
  []
  (alter-var-root #'system  (fn [s] (when s (component/stop s)))))

(defn go
  "Initializes and starts the system running."
  []
  (init)
  (start)
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))
