(ns tools
  (:require [vscode.workspace :as workspace]
            [manifold-cljs.deferred :as d]))

(defn migrate-config! []
  (let [config                (workspace/get-configuration)
        set-workspace-config! (partial workspace/config-set! config :workspace)]
    (d/chain
     (let [nix-file (workspace/config-get config :nix-env-selector/nix-shell-config)]
       (when-not (empty? nix-file)
         (set-workspace-config! :nix-env-selector/nix-file
                                nix-file)))

     (let [attr (workspace/config-get config :nix-env-selector/nix-shell-config-attr)]
       (when-not (or (empty? attr) (= "''" attr))
         (set-workspace-config! :nix-env-selector/args
                                (str "-A " attr))))


     (set-workspace-config! :nix-env-selector/nix-shell-config js/undefined)
     (set-workspace-config! :nix-env-selector/nix-shell-config-attr js/undefined))))
