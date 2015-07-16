(ns corona.dev
  (:require [environ.core :refer [env]]
            [net.cgrand.enlive-html :refer [set-attr prepend append html]]
            [cemerick.piggieback :as piggieback]
            [weasel.repl.websocket :as weasel]
            [figwheel-sidecar.auto-builder :as fig-auto]
            [figwheel-sidecar.core :as fig]
            [clojurescript-build.auto :as auto]
            [clojure.java.shell :refer [sh]]
            [com.stuartsierra.component :as component]))

(def inject-devmode-html
  (comp
     (set-attr :class "is-dev")
     (prepend (html [:script {:type "text/javascript" :src "/js/out/goog/base.js"}]))
     (prepend (html [:script {:type "text/javascript" :src "/react/react.js"}]))
     (append  (html [:script {:type "text/javascript"} "goog.require('corona.main')"]))))

(defn browser-repl []
  (let [repl-env (weasel/repl-env :ip "0.0.0.0" :port 9001)]
    (piggieback/cljs-repl repl-env)))

(defn start-figwheel []
  (let [server (fig/start-server { :css-dirs ["resources/public/css"] })
        config {:builds [{:id "dev"
                          :source-paths ["src/cljs" "env/dev/cljs"]
                          :compiler {:output-to            "resources/public/js/app.js"
                                     :output-dir           "resources/public/js/out"
                                     :source-map           "resources/public/js/out.js.map"
                                     :source-map-timestamp true
                                     :preamble             ["react/react.min.js"]}}]
                :figwheel-server server}]
    {:server server
     :autobuilder (fig-auto/autobuild* config)}))

(defrecord FigwheelServer [is-dev?]
  component/Lifecycle
  (start [component]
    (merge component (if is-dev? (start-figwheel) nil)))
  (stop [{:keys [server autobuilder] :as component}]
    (when autobuilder (auto/stop-autobuild! autobuilder))
    (when server (fig/stop-server      server))
    (merge component {:server nil :autobuilder nil})))

(defn new-figwheel [is-dev?]
  (map->FigwheelServer {:is-dev? is-dev?}))
