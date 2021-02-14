(ns utils.helpers
  (:require [clojure.string :as s]))

(defn render-workspace [path workspace-root]
  (s/replace path "${workspaceRoot}" workspace-root))

(defn unrender-workspace [path workspace-root]
  (s/replace path workspace-root "${workspaceRoot}"))

(defn render-env-status [lang env-path]
  (s/replace (-> lang :label :env-selected)
             #"%ENV_NAME%"
             (if env-path
               (last (s/split env-path #"/"))
               (-> lang :label :env-custom))))