(ns main
  (:require [config :refer [config update-config!]]
            [promesa.core :as p]
            [vscode.status-bar :as status]
            [vscode.context :refer [subsciribe]]
            [vscode.command :as cmd]
            [ext.actions :as act]
            [ext.nix-env :as env]
            [ext.lang :refer [lang]]
            [utils.helpers :refer [render-env-status]]))

(defn activate [ctx]
  (update-config!)
  (let [status-bar     (status/create :left 100)]
    (if (or (not-empty (:nix-file @config)) (not-empty (:nix-packages @config)))
      (do
        (-> (env/get-nix-env-sync {:nix-config     (:nix-file @config)
                                   :packages       (:nix-packages @config)
                                   :args           (:nix-args @config)
                                   :nix-shell-path (:nix-shell-path @config)})
            (env/set-current-env))
        (->> status-bar
             (status/show {:text    (render-env-status lang (:nix-file @config))
                           :command :nix-env-selector/select-env})))

      ;; show notification that nix config available
      ;; if workspace contains .nix file(s)
      (p/chain (act/get-nix-files (:workspace-root @config))
               #(when (and (:suggest-nix? @config)
                           (> (count %1) 0))
                  (act/show-propose-env-dialog))))

    ;; register user commands
    (subsciribe ctx (cmd/create :nix-env-selector/select-env (act/select-nix-environment status-bar)))
    (subsciribe ctx (cmd/create :nix-env-selector/hit-env (act/hit-nix-environment status-bar)))))

(defn deactivate [])
