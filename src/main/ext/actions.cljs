(ns ext.actions
  (:require ["fs" :refer [readdir]]
            [config :refer [config vscode-config]]
            [vscode.window :as w]
            [vscode.command :as cmd]
            [vscode.workspace :as workspace]
            [vscode.status-bar :as status-bar]
            [ext.lang :as l]
            [ext.nix-env :as env]
            [promesa.core :as p]
            [utils.helpers :refer [unrender-workspace]]))

(defn get-nix-files [workspace-root]
  (let [files-res (p/deferred)]
    (readdir workspace-root
             (fn [err result]
               (if (nil? err)
                 (p/resolve! files-res result)
                 (p/reject! files-res err))))
    files-res
    (p/chain files-res
             #(filter (fn [file]
                        (-> (re-matches #"(?i).*\.nix" file)
                            (nil?)
                            (not))) %1))))


(defn show-propose-env-dialog []
  (let [select-label  (-> l/lang :label :select-env)
        dismiss-label (-> l/lang :label :dismiss)
        dialog        (w/show-notification (-> l/lang :notification :env-available)
                                           [select-label dismiss-label])]
    (p/chain dialog
             #((cond
                 (= select-label %1) (cmd/execute :nix-env-selector/select-env)
                 (= dismiss-label %1) (workspace/config-set! vscode-config
                                                             :workspace
                                                             :nix-env-selector/suggestion
                                                             false))))))


(defn show-reload-dialog []
  (let [reload-label   (-> l/lang :label :reload)
        reload-message (-> l/lang :notification :env-applied)
        dialog         (w/show-notification reload-message
                                            [reload-label])]
    (p/chain dialog
             #(when (= reload-label %1)
                (cmd/execute :workbench/action.reload-window)))))

(defn load-env-by-path [status]
  (fn [path-p]
    (p/chain path-p
             (fn [nix-path]
               (when nix-path
                 (status-bar/show {:text (-> l/lang :label :env-loading)}
                                  status)
                 (env/get-nix-env-async {:nix-config     nix-path
                                         :nix-shell-path (:nix-shell-path @config)})))
             (fn [env-vars]
               (when env-vars
                 (env/set-current-env env-vars)
                 :ok))
             (fn [result]
               (when result
                 (status-bar/show {:text    (-> l/lang :label :env-need-reload)
                                   :command :nix-env-selector/select-env} status)
                 (show-reload-dialog))))))

(defn hit-nix-environment [status]
  (fn []
    (-> (p/promise (:nix-file @config))
        ((load-env-by-path status)))))

(defn select-nix-environment [status]
  (fn []
    (-> (get-nix-files (:workspace-root @config))
        (p/chain #(w/show-quick-pick {:place-holder (-> l/lang :label :select-config-placeholder)}
                                     (into [{:id    "disable"
                                             :label (-> l/lang :label :disabled-nix)}]
                                           (map (fn [file-name]
                                                  {:id    file-name
                                                   :label file-name}) %1)))
                 (fn [nix-file-name]
                   (cond
                     (= "disable" (:id nix-file-name))
                     (do
                       (status-bar/hide status)
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
                       (workspace/config-set! vscode-config
                                              :workspace
                                              :nix-env-selector/nix-file
                                              (unrender-workspace nix-file (:workspace-root @config)))
                       (:workspace-root @config))))
                 (load-env-by-path status))
        (p/catch #(js/console.error %)))))
