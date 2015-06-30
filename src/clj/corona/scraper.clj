(ns clj.corona.scraper
  (:refer-clojure :exclude [update])
  (:require [clj-webdriver.taxi :as taxi]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [clojure.data :refer [diff]]
            [com.rpl.specter :refer :all]
            [clojure.pprint :refer [pprint]]
            [cljs.build.api :as build]
            [cljs.compiler.api :as compiler]
            [taoensso.timbre :as timbre]
            [clojure.string :as s]))
(timbre/refer-timbre)
;; Copied verbatim from the defunct clojure-contrib (http://bit.ly/deep-merge-with)
(defn deep-merge-with [f & maps]
  (apply
   (fn m [& maps]
     (if (every? map? maps)
       (apply merge-with m maps)
       (apply f maps)))
   maps))
;;TODO convert this to cross from flatland/useful and the above to merge-in 
(defn cart [colls]
  (if (empty? colls)
    '(())
    (for [x (first colls)
          more (cart (rest colls))]
      (cons x more))))

(extend-protocol cljs.closure/Compilable
  clojure.lang.LazySeq
  (-compile [this opts] (cljs.closure/compile-form-seq this)))

(defn cljs-forms->js [forms]
  (spy (build/build forms
                     {:optimizations :advanced
                      :verbose true})))

(defprotocol browser-executable
  "A protocol which takes in a driver and arguments and evaluates a function in the browser."
  (eval-in-browser [this driver params]))

(defrecord CljsCode [variables code compiled]
  browser-executable
  (eval-in-browser [this driver env]
    (spy variables)
    ;;Compiling things all over the place now. HMm.
    (let [ns-forms (cljs-forms->js '(ns user.func))
          params (->> env
                      (map (fn [[sym value]] `(def ~sym ~value)))
                      (map cljs-forms->js)
                      (s/join "\n"))
          fn-call (cljs-forms->js (concat ['func] variables))
          script (str ns-forms
                      "var cljs = {}; cljs.user = {}; "
                      params
                      "cljs.user.func = " compiled ";\n"
                      "return " fn-call ";")]
      (taxi/execute-script driver script))))

(defn symbols->CljsCode [[variables & rst]]
  (let [code (concat ['defn 'func variables] rst)]
    (->CljsCode variables code (cljs-forms->js code))))

(def readers
  {'cljs symbols->CljsCode ;(fn [& args] [:clojurescript args])
   'enlive identity
   'css identity})

(defn read-format [s]
  (clojure.edn/read-string {:readers readers} s))

(def example (-> (io/resource "example.edn") slurp read-format))

(defn characteristics-of-function-or-symbol [arg]
  (or (:params arg) (when (symbol? arg) [arg])))

(defn characteristics-of-browser-action-arg [arg]
  {:consumes (characteristics-of-function-or-symbol arg)})

(defmulti  characteristics-of-an-action first)
(defmethod characteristics-of-an-action :action/visit [[_ arg]]
  (characteristics-of-browser-action-arg arg))

(defmethod characteristics-of-an-action :action/observe [[_ observation]]
  {:consumes (mapcat characteristics-of-function-or-symbol (vals observation))
   :produces {:variables (keys observation)}})

(defmethod characteristics-of-an-action :action/collect [[_ entity-type entity]]
  {:consumes (mapcat characteristics-of-function-or-symbol (vals entity))
   :produces {:entity-types #{entity-type}
              :variables (->> (vals entity) (filter list?) (map last) (mapcat keys) set)}})

(defn characteristics-of-a-task [{:keys [task/actions]}]
  (apply merge-with (comp set concat) (map characteristics-of-an-action actions)))

(defn characteristics-of-tasks [tasks]
  (update [ALL LAST] characteristics-of-a-task tasks))

(defn declared-variables-match-used-variables [{:keys [mission/tasks mission/variables]}]
  (= (set (keys variables))
     (as-> (characteristics-of-tasks tasks) $
       (vals $)
       (apply deep-merge-with concat $)
       (concat (get-in $ [:produces :variables]) (:consumes $))
       (set $))))

;;TODO all these mapv's are awful and look messy. 
(defn whats-executable-now [characteristics variables examined]
  (->> characteristics
       (map (fn [[target characteristic]]
              (->> (seq (:consumes characteristic))
                   (select-keys variables)
                   (mapv (fn [[variable value]] (vec (cart [[variable] value]))))
                   cart
                   (mapv (partial mapv vec))
                   (mapv (partial into {}))
                   (filter #(= (count (:consumes characteristic)) (count (keys %))))
                   set
                   (vector target))))
       (into {})
       (diff examined)
       second))

(defn eval-arg [{:keys [env variables driver]} arg]
  (cond
    (satisfies? browser-executable arg) (eval-in-browser arg driver env)
    (env arg)   (env arg)
    :else arg))


(defmulti  execute-action  (fn [_ action] (first action)))
(defmethod execute-action :action/visit [{:keys [env variables driver] :as state} [_ arg]]
  (taxi/to driver (spy (eval-arg state arg))))

(defmethod execute-action :action/observe [{:keys [env variables driver] :as state} [_ arg]]
  (as-> arg $
    (update [ALL LAST] (partial eval-arg state) $)
    (merge-with concat variables $)
    (assoc state :variables $)))

(defn execute-task [{:keys [task/actions] :as task} env driver]
  (info "Executing task")
  (info env)
  (reduce execute-action {:env env
                          :variables {}
                          :driver driver}
          actions))

(defn execute-step [{:keys [mission/tasks] :as plan} next-steps driver]
  (->> next-steps
       (mapcat (fn [[task envs]] (cart [[task] envs])))
       (map    (fn [[task env]] (execute-task (tasks task) env driver)))
       (map :variables)
       (update [ALL ALL LAST] vector)
       (apply merge-with concat)))

(defn execute-plan
  [{:keys [mission/parameters     mission/variables mission/schema
           mission/surrogate-keys mission/tasks]
    {:keys [parameter/driver]} :mission/parameters
    :as plan}]
  {:pre [(declared-variables-match-used-variables plan)]}
  (let [driver (taxi/new-driver driver)
        characteristics (characteristics-of-tasks tasks)
        examined (zipmap (keys tasks) (repeat #{}))]
    (.setJavascriptEnabled (:webdriver driver) true);;enable javascript
    (taxi/to driver "http://www.google.com");; load a page for javascript?
    (loop [variables variables examined examined]
      (Thread/sleep 100)
      (let [next-steps (whats-executable-now characteristics variables examined)]
        (if (->> next-steps vals (apply concat) empty?)
          (info "FINISHED")
          (let [new-variables (spy (execute-step plan next-steps driver))]
            (recur (merge-with (comp set concat) new-variables variables)
                   (merge-with (comp set concat) examined next-steps))))))))

;(execute-plan example)


