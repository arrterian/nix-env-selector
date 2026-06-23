(ns config
  (:require [vscode.workspace :as workspace]
            [utils.helpers :refer [render-workspace]]
            [utils.logger :as logger]))

(defonce config (atom {}))

;; See note [fresh-workspace-configuration].
(defn get-vscode-config []
  (workspace/get-configuration))

(defn- read-rendered [vscode-config key root]
  (when-let [v (workspace/config-get vscode-config key)]
    (render-workspace v root)))

;; Note [fresh-workspace-configuration]:
;; We must call `workspace.getConfiguration()` fresh each time rather than
;; caching it at module load time (e.g. via `defonce`).
;; In VSCode Remote Development, workspace-folder-level settings are not
;; yet available when the module is first evaluated (before `activate()` runs).
;; A cached object from that point would only return default values from `package.json`.
(defn update-config! []
  (let [vscode-config (workspace/get-configuration)
        workspace-root (first (workspace/get-folders))]
    (reset! config {:workspace-root   workspace-root
                    :nix-file         (read-rendered vscode-config :nix-env-selector/nix-file workspace-root)
                    :suggest-nix?     (workspace/config-get vscode-config :nix-env-selector/suggestion)
                    :nix-packages     (workspace/config-get vscode-config :nix-env-selector/packages)
                    :nix-args         (read-rendered vscode-config :nix-env-selector/args workspace-root)
                    :nix-shell-path   (read-rendered vscode-config :nix-env-selector/nix-shell-path workspace-root)
                    :use-flakes?      (workspace/config-get vscode-config :nix-env-selector/use-flakes)
                    :flake-shell      (not-empty (workspace/config-get vscode-config :nix-env-selector/flake-shell))
                    :patch-terminals? (workspace/config-get vscode-config :nix-env-selector/patch-terminals)
                    :log-level        (or (workspace/config-get vscode-config :nix-env-selector/log-level) "info")})))

(workspace/on-config-change (fn []
                              (update-config!)
                              (logger/set-level! (:log-level @config))))
