(ns utils.helpers
  (:require [clojure.string :as s]))

;; `workspaceRoot` is deprecated, the new variable name is `workspaceFolder`.
(defn render-workspace [path workspace-root]
  (s/replace (s/replace path "${workspaceFolder}" workspace-root) "${workspaceRoot}" workspace-root))

(defn unrender-workspace [path workspace-root]
  (s/replace path workspace-root "${workspaceFolder}"))

(defn render-env-status [lang env-path]
  (s/replace (-> lang :label :env-selected)
             #"%ENV_NAME%"
             (if env-path
               (last (s/split env-path #"/"))
               (-> lang :label :env-custom))))