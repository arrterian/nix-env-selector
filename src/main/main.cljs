(ns main
  (:require [config :refer [config update-config!]]
            [promesa.core :as p]
            [vscode.status-bar :as status]
            [vscode.context :refer [subscribe apply-env-collection!]]
            [vscode.command :as cmd]
            [vscode.window :as w]
            [ext.actions :as act]
            [ext.nix-env :as env]
            [ext.lang :refer [lang]]
            [utils.logger :as logger]
            [utils.helpers :refer [render-env-status]]))

(defn activate [ctx]
  (let [log-channel (w/create-log-output-channel)]
    (logger/init! log-channel)
    (update-config!)
    (logger/set-level! (:log-level @config))
    (logger/info (str "Log level: " (:log-level @config)))
    (logger/info "Initializing config...")
    (logger/info (str "Loaded config: " @config))
    (let [status-bar (status/create :left 100)]
      (if (or (not-empty (:nix-file @config)) (not-empty (:nix-packages @config)))
        (try
          (let [env-vars (env/get-nix-env-sync {:nix-config      (:nix-file @config)
                                                :packages        (:nix-packages @config)
                                                :args            (:nix-args @config)
                                                :nix-shell-path  (:nix-shell-path @config)
                                                :use-flakes      (:use-flakes @config)
                                                :flake-dev-shell (:flake-dev-shell @config)})]
            (env/set-current-env env-vars)
            (apply-env-collection! ctx env-vars)
            (logger/info (str "Applied " (count env-vars) " variables to extension host and terminal collection")))
          (->> status-bar
               (status/show {:text    (render-env-status lang (:nix-file @config))
                             :command :nix-env-selector/select-env}))
          (catch :default e
            (logger/error "Failed to apply environment on startup" e)
            (w/show-error-notification (-> lang :notification :env-error))))

        ;; show notification that nix config available
        ;; if workspace contains .nix file(s)
        (p/chain (act/get-nix-files (:workspace-root @config))
                 #(when (and (:suggest-nix? @config)
                             (> (count %1) 0))
                    (act/show-propose-env-dialog))))

      ;; register user commands
      (subscribe ctx (cmd/create :nix-env-selector/select-env (act/select-nix-environment status-bar ctx)))
      (subscribe ctx (cmd/create :nix-env-selector/hit-env (act/hit-nix-environment status-bar ctx))))))

(defn deactivate [])
