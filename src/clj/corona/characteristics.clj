(ns clj.corona.characteristics
  (:refer-clojure :exclude [update])
  (:require [com.rpl.specter :refer :all]))

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
