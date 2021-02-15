(ns main
  (:require [manifold-cljs.deferred :as d]
            [vscode.status-bar :as status]
            [vscode.context :refer [subsciribe]]
            [vscode.command :as cmd]
            [vscode.workspace :as workspace]
            [ext.actions :as act]
            [ext.nix-env :as env]
            [ext.lang :refer [lang]]
            [tools :refer [migrate-config!]]
            [utils.helpers :refer [render-workspace render-env-status]]))

(defn activate [ctx]
  (migrate-config!)
  (let [config         (workspace/get-configuration)
        workspace-root (first (workspace/get-folders))
        suggest-nix?   (workspace/config-get config :nix-env-selector/suggestion)
        nix-file       (-> (workspace/config-get config :nix-env-selector/nix-file)
                           (#(when %1 (render-workspace %1 workspace-root))))
        nix-packages   (workspace/config-get config :nix-env-selector/packages)
        nix-args       (workspace/config-get config :nix-env-selector/args)
        nix-shell-path (workspace/config-get config :nix-env-selector/nix-shell-path)
        status-bar     (status/create :left 100)]
    (if (or (not-empty nix-file) (not-empty nix-packages))
      (do
        (-> (env/get-nix-env-sync {:nix-config     nix-file
                                   :packages       nix-packages
                                   :args           nix-args
                                   :nix-shell-path nix-shell-path})
            (env/set-current-env))
        (->> status-bar
             (status/show {:text    (render-env-status lang nix-file)
                           :command :extension/select-env})))

      ;; show notification that nix config available
      ;; if workspace contains .nix file(s)
      (d/chain (act/get-nix-files workspace-root)
               #(when (and suggest-nix?
                           (> (count %1) 0))
                  (act/show-propose-env-dialog))))

    ;; register user commands
    (subsciribe ctx (cmd/create :extension/select-env (act/select-nix-environment config workspace-root status-bar)))))

(defn deactivate [])
