(ns corona.server
  (:require [clojure.java.io :as io]
            [compojure.core :refer [GET POST defroutes]]
            [corona.dev :refer [inject-devmode-html]]
            [compojure.route :refer [resources]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            ;;[net.cgrand.reload :refer [auto-reload]]
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [org.httpkit.server :refer [run-server]]
            [clj-webdriver.taxi :as taxi]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [com.stuartsierra.component :as component]
            ))

;;Copied from https://github.com/ptaoussanis/sente
;; (let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
;;               connected-uids]}
;;       (sente/make-channel-socket! sente-web-server-adapter {})]
;;   (def ring-ajax-post                ajax-post-fn)
;;   (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
;;   (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
;;   (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
;;   (def connected-uids                connected-uids) ; Watchable, read-only atom
;;   )

(deftemplate page (io/resource "index.html") []
  [:body] inject-devmode-html) ;;TODO rip out dev mode somehow

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})
;  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
;  (POST "/chsk" req (ring-ajax-post                req))
  (GET "/*" req (page )))

#_(def coronoa-server
  (-> routes
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params))

(defrecord WebServer [port is-dev?]
  component/Lifecycle
  (start [component]
    (print "Starting web server on port" port ".\n")
    (let [http-handler
          (if is-dev?
            (reload/wrap-reload (wrap-defaults #'routes api-defaults))
            (wrap-defaults routes api-defaults))]
      (assoc component :web-server
             (run-server http-handler {:port port :join? false}))))
  (stop [{:keys [web-server] :as component}]
    (web-server :timeout 100)
    (assoc component :server nil)))

(defn new-webserver [port is-dev?]
  (map->WebServer {:port port :is-dev? is-dev?}))
