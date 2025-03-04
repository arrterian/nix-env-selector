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
    (p/map #(filter (fn [file]
                      (-> (re-matches #"(?i).*\.nix" file)
                          (nil?)
                          (not))) %1)
           files-res)))

(defn show-propose-env-dialog [log-channel]
  (let [select-label  (-> l/lang :label :select-env)
        dismiss-label (-> l/lang :label :dismiss)
        dialog        (w/show-notification (-> l/lang :notification :env-available)
                                           [select-label dismiss-label])]
    (p/mapcat dialog
              #((cond
                  (= select-label %1) (cmd/execute :nix-env-selector/select-env log-channel)
                  (= dismiss-label %1) (workspace/config-set! vscode-config
                                                              :workspace
                                                              :nix-env-selector/suggestion
                                                              false))))))

(defn show-reload-dialog []
  (let [reload-message (-> l/lang :notification :env-applied)]
    (w/show-notification reload-message [])))

(defn load-env-by-path [nix-path status log-channel]
  (when nix-path
    (w/write-log log-channel (str "Loading env in path: " nix-path))
    (status-bar/show {:text (-> l/lang :label :env-loading)}
                     status)
    (->> (env/get-nix-env-async {:nix-config     nix-path
                                 :args           (:nix-args @config)
                                 :nix-shell-path (:nix-shell-path @config)}
                                log-channel)
         (p/map (fn [env-vars]
                  (when env-vars
                    (env/set-current-env env-vars)
                    :ok)))
         (p/map (fn [result]
                  (when result
                    (status-bar/show {:text    (-> l/lang :label :env-need-reload)
                                      :command :nix-env-selector/select-env} status))))
         (p/mapcat show-reload-dialog))))

(defn hit-nix-environment [status log-channel]
  (w/write-log log-channel "Running action: Hit environment")
  (fn []
    (-> (:nix-file @config)
        (load-env-by-path status log-channel))))

(defn select-nix-environment [status log-channel]
  (w/write-log log-channel "Running action: Select environment")
  (fn []
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
                      (w/write-log log-channel "Selected to disable Nix environment")
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
                      (w/write-log log-channel (str "Selected Nix file: " nix-file))
                      (workspace/config-set! vscode-config
                                             :workspace
                                             :nix-env-selector/nix-file
                                             (unrender-workspace nix-file (:workspace-root @config)))
                      nix-file))))
         (p/mapcat #(load-env-by-path %1 status log-channel))
         (p/error #(js/console.error %)))))
