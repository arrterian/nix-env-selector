(ns utils.logger
  (:require [clojure.string :as s]))

(defonce ^:private ch (atom nil))

(def ^:private level-rank {"debug" 0 "info" 1 "warn" 2 "error" 3})

(defn init! [channel]
  (reset! ch channel))

(defn show-channel! []
  (when @ch (.show @ch)))

(defonce ^:private configured-level (atom "info"))

(defn set-level! [level]
  (reset! configured-level (or level "info")))

(defn- enabled? [level]
  (>= (get level-rank level 1)
      (get level-rank @configured-level 1)))

(defn- now []
  (.toISOString (js/Date.)))

(defn- write [level msg]
  (when (and (enabled? level) @ch)
    (.appendLine @ch (str "[" (now) "] [" (.toUpperCase level) "] " msg))))

(defn debug [msg]
  (write "debug" msg))

(defn info [msg]
  (write "info" msg))

(defn warn [msg]
  (write "warn" msg))

(defn error
  ([msg]
   (write "error" msg))
  ([msg err]
   (write "error" msg)
   (when err
     (doseq [line (s/split-lines (or (.-stack err) (.-message err) (str err)))]
       (write "error" (str "  " line))))))
