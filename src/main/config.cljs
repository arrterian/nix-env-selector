(ns config
  (:require [vscode.workspace :as workspace]
            [utils.helpers :refer [render-workspace]]))

(defonce config (atom {}))

(defonce vscode-config  (workspace/get-configuration))

(defn update-config! []
  (let [workspace-root (first (workspace/get-folders))]
    (reset! config {:workspace-root workspace-root
                    :nix-file       (-> (workspace/config-get vscode-config :nix-env-selector/nix-file)
                                        (#(when %1 (render-workspace %1 workspace-root))))
                    :suggest-nix?   (workspace/config-get vscode-config :nix-env-selector/suggestion)
                    :nix-packages   (workspace/config-get vscode-config :nix-env-selector/packages)
                    :nix-args       (-> (workspace/config-get vscode-config :nix-env-selector/args)
                                        (#(when %1 (render-workspace %1 workspace-root))))
                    :nix-shell-path (-> (workspace/config-get vscode-config :nix-env-selector/nix-shell-path)
                                        (#(when %1 (render-workspace %1 workspace-root))))})))

(workspace/on-config-change update-config!)
