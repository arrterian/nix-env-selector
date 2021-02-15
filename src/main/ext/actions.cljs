(ns ext.actions
  (:require ["fs" :refer [readdir]]
            [vscode.window :as w]
            [vscode.command :as cmd]
            [vscode.workspace :as workspace]
            [vscode.status-bar :as status-bar]
            [ext.lang :as l]
            [ext.nix-env :as env]
            [manifold-cljs.deferred :as d]
            [utils.helpers :refer [unrender-workspace]]))

(defn get-nix-files [workspace-root]
  (let [files-res (d/deferred)]
    (readdir workspace-root
             (fn [err result]
               (if (nil? err)
                 (d/success! files-res result)
                 (d/error! files-res err))))
    files-res
    (d/chain files-res
             #(filter (fn [file]
                        (-> (re-matches #"(?i).*\.nix" file)
                            (nil?)
                            (not))) %1))))


(defn show-propose-env-dialog []
  (let [select-label  (-> l/lang :label :select-env)
        dismiss-label (-> l/lang :label :dismiss)
        dialog        (w/show-notification (-> l/lang :notification :env-available)
                                           [select-label dismiss-label])]
    (d/chain dialog
             #((cond
                 (= select-label %1) (cmd/execute :extension/select-env)
                 (= dismiss-label %1) (workspace/config-set! (workspace/get-configuration)
                                                             :workspace
                                                             :nix-env-selector/suggestion
                                                             false))))))


(defn show-reload-dialog []
  (let [reload-label   (-> l/lang :label :reload)
        reload-message (-> l/lang :notification :env-applied)
        dialog         (w/show-notification reload-message
                                            [reload-label])]
    (d/chain dialog
             #(when (= reload-label %1)
                (cmd/execute :workbench/action.reload-window)))))

(defn select-nix-environment [config workspace-root status]
  (fn []
    (d/chain (get-nix-files workspace-root)
             #(w/show-quick-pick {:place-holder (-> l/lang :label :select-config-placeholder)}
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
                   (workspace/config-set! config :workspace
                                          :nix-env-selector/nix-file
                                          js/undefined)
                   (workspace/config-set! config :workspace
                                          :nix-env-selector/suggestion
                                          false))

                 (not-empty nix-file-name)
                 (let [nix-file (str workspace-root "/" (:id nix-file-name))]
                   (workspace/config-set! config :workspace
                                          :nix-env-selector/nix-file
                                          (unrender-workspace nix-file workspace-root))
                   workspace-root)))
             (fn [nix-path]
               (when nix-path
                 (status-bar/show {:text (-> l/lang :label :env-loading)}
                                  status)
                 (env/get-nix-env-async {:nix-config     nix-path
                                         :nix-shell-path (workspace/config-get config :nix-env-selector/nix-shell-path)})))
             (fn [env-vars]
               (when env-vars
                 (env/set-current-env env-vars)
                 :ok))
             (fn [result]
               (when result
                 (status-bar/show {:text    (-> l/lang :label :env-need-reload)
                                   :command :extension/select-env} status)
                 (show-reload-dialog))))))
