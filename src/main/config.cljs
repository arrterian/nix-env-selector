(ns config
  (:require [vscode.workspace :as workspace]
            [utils.helpers :refer [render-workspace]]
            [utils.logger :as logger]))

(defonce config (atom {}))

(defonce vscode-config  (workspace/get-configuration))

(defn- read-rendered [key root]
  (when-let [v (workspace/config-get vscode-config key)]
    (render-workspace v root)))

(defn update-config! []
  (let [workspace-root (first (workspace/get-folders))]
    (reset! config {:workspace-root   workspace-root
                    :nix-file         (read-rendered :nix-env-selector/nix-file workspace-root)
                    :suggest-nix?     (workspace/config-get vscode-config :nix-env-selector/suggestion)
                    :nix-packages     (workspace/config-get vscode-config :nix-env-selector/packages)
                    :nix-args         (read-rendered :nix-env-selector/args workspace-root)
                    :nix-shell-path   (read-rendered :nix-env-selector/nix-shell-path workspace-root)
                    :use-flakes?      (workspace/config-get vscode-config :nix-env-selector/use-flakes)
                    :patch-terminals? (workspace/config-get vscode-config :nix-env-selector/patch-terminals)
                    :log-level        (or (workspace/config-get vscode-config :nix-env-selector/log-level) "info")})))

(workspace/on-config-change (fn []
                              (update-config!)
                              (logger/set-level! (:log-level @config))))
