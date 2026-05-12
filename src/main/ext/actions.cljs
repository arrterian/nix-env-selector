(ns ext.actions
  (:require ["fs" :refer [readdir]]
            [config :refer [config vscode-config]]
            [vscode.window :as w]
            [vscode.command :as cmd]
            [vscode.workspace :as workspace]
            [vscode.status-bar :as status-bar]
            [vscode.context :as vscode-ctx]
            [ext.lang :as l]
            [ext.nix-env :as env]
            [promesa.core :as p]
            [utils.logger :as logger]
            [utils.helpers :refer [unrender-workspace]]))

(defn get-nix-files [workspace-root]
  (let [files-res (p/deferred)]
    (readdir workspace-root
             #js {:withFileTypes true}
             (fn [err result]
               (if (nil? err)
                 (p/resolve! files-res result)
                 (p/reject! files-res err))))
    (p/map (fn [dirents]
             (->> dirents
                  (filter #(.isFile ^js %))
                  (map #(.-name ^js %))
                  (filter #(re-find #"(?i)\.nix$" %))))
           files-res)))

(defn show-propose-env-dialog []
  (let [select-label  (-> l/lang :label :select-env)
        dismiss-label (-> l/lang :label :dismiss)
        dialog        (w/show-notification (-> l/lang :notification :env-available)
                                           [select-label dismiss-label])]
    (p/mapcat #(cond
                  (= select-label %1)  (do (logger/info "User chose to select Nix environment")
                                           (cmd/execute :nix-env-selector/select-env))
                  (= dismiss-label %1) (do (logger/info "User dismissed Nix environment suggestion")
                                          (workspace/config-set! vscode-config
                                                                  :workspace
                                                                  :nix-env-selector/suggestion
                                                                  false)))
              dialog)))

(defn show-reload-dialog []
  (let [reload-label   (-> l/lang :label :reload)
        reload-message (-> l/lang :notification :env-applied)]
    (p/mapcat #(when (= reload-label %1)
                 (cmd/execute-raw "workbench.action.reloadWindow"))
              (w/show-notification reload-message [reload-label]))))

(defn- handle-error [message]
  (fn [e]
    (logger/error message e)
    (w/show-error-notification (-> l/lang :notification :env-error))))

(defn- nix-env-options [nix-path]
  {:nix-config     nix-path
   :packages       (:nix-packages @config)
   :args           (:nix-args @config)
   :nix-shell-path (:nix-shell-path @config)
   :use-flakes     (:use-flakes? @config)})

(defn- has-env-source? [options]
  (or (not-empty (:nix-config options))
      (not-empty (:packages options))))

(defn load-env [options status ctx]
  (when (has-env-source? options)
    (logger/info (str "Loading environment (nix-config=" (:nix-config options)
                      ", packages=" (count (:packages options)) ")"))
    (status-bar/show {:text (-> l/lang :label :env-loading)}
                     status)
    (->> (env/get-nix-env-async options)
         (p/map (fn [env-vars]
                  (when env-vars
                    (env/set-current-env env-vars)
                    (if (:patch-terminals? @config)
                      (do (vscode-ctx/apply-env-collection! ctx env-vars)
                          (logger/info (str "Applied " (count env-vars) " variables to extension host and terminal collection")))
                      (logger/info (str "Applied " (count env-vars) " variables to extension host (terminal patching disabled)")))
                    :ok)))
         (p/map (fn [result]
                  (when result
                    (status-bar/show {:text    (-> l/lang :label :env-need-reload)
                                      :command :nix-env-selector/select-env} status))))
         (p/mapcat show-reload-dialog)
         (p/error (handle-error "Failed to load environment")))))

(defn hit-nix-environment [status ctx]
  (fn []
    (logger/info "Running action: Refresh environment")
    (load-env (nix-env-options (:nix-file @config)) status ctx)))

(defn select-nix-environment [status ctx]
  (fn []
    (logger/info "Running action: Select environment")
    (->> (get-nix-files (:workspace-root @config))
         (p/mapcat #(w/show-quick-pick {:place-holder (-> l/lang :label :select-config-placeholder)}
                                       (into [{:id    "disable"
                                               :label (-> l/lang :label :disabled-nix)}]
                                             (map (fn [file-name]
                                                    {:id    file-name
                                                     :label file-name}) %1))))
         (p/map (fn [nix-file-name]
                  (cond
                    (= "disable" (:id nix-file-name))
                    (do
                      (logger/info "Disabling Nix environment")
                      (status-bar/hide status)
                      (vscode-ctx/clear-env-collection! ctx)
                      (workspace/config-set! vscode-config
                                             :workspace
                                             :nix-env-selector/nix-file
                                             js/undefined)
                      (workspace/config-set! vscode-config
                                             :workspace
                                             :nix-env-selector/suggestion
                                             false))

                    (not-empty nix-file-name)
                    (let [nix-file (str (:workspace-root @config) "/" (:id nix-file-name))]
                      (logger/info (str "Selected Nix file: " nix-file))
                      (workspace/config-set! vscode-config
                                             :workspace
                                             :nix-env-selector/nix-file
                                             (unrender-workspace nix-file (:workspace-root @config)))
                      nix-file))))
         (p/mapcat #(load-env (nix-env-options %1) status ctx))
         (p/error (handle-error "Select environment failed")))))
