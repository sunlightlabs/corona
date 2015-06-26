(ns corona.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [corona.core-test]))

(enable-console-print!)

(defn runner []
  (if (cljs.test/successful?
       (run-tests
        'corona.core-test))
    0
    1))
