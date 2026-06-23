(ns ext.actions
  (:require ["fs" :refer [readdir]]
            ["path" :refer [dirname basename]]
            [clojure.string :as s]
            [config :refer [config get-vscode-config]]
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

;; ---------------------------------------------------------------------------
;; File system / VS Code dialog helpers
;; ---------------------------------------------------------------------------

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
                                          (workspace/config-set! (get-vscode-config)
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

;; ---------------------------------------------------------------------------
;; Disable / Show-logs commands
;; ---------------------------------------------------------------------------

(defn disable-nix-environment [status ctx]
  (logger/info "Disabling Nix environment")
  (status-bar/hide status)
  (vscode-ctx/clear-env-collection! ctx)
  (let [vscode-config (get-vscode-config)]
    (swap! config assoc :nix-file nil :use-flakes? false :flake-shell nil)
    (workspace/config-set! vscode-config :workspace :nix-env-selector/nix-file js/undefined)
    (workspace/config-set! vscode-config :workspace :nix-env-selector/use-flakes js/undefined)
    (workspace/config-set! vscode-config :workspace :nix-env-selector/flake-shell js/undefined)
    (workspace/config-set! vscode-config :workspace :nix-env-selector/suggestion false)))

(defn disable-nix-environment-command [status ctx]
  (fn []
    (disable-nix-environment status ctx)))

(defn show-logs-command []
  (fn []
    (logger/show-channel!)))

;; ---------------------------------------------------------------------------
;; Source description, popup items, tooltip markdown
;; ---------------------------------------------------------------------------

(defn- source-type-label [src]
  (let [t (:label l/lang)]
    (case (:type src)
      :flake     (:info-source-flake t)
      :nix-shell (:info-source-nix-shell t)
      :packages  (:info-source-packages t)
      :none      (:info-source-none t))))

(defn- source-detail [src]
  (let [t (:label l/lang)]
    (cond
      (:path src)         (str (:info-path t) ": " (:path src))
      (seq (:packages src)) (str (:info-packages t) ": "
                                 (s/join ", " (:packages src)))
      :else nil)))

(defn- info-items [src]
  (let [t       (:label l/lang)
        active? (not= :none (:type src))
        header  (cond-> (str (:info-source t) ": " (source-type-label src))
                  (source-detail src) (str " · " (source-detail src)))]
    (cond-> [{:kind -1 :label header}]
      active? (conj {:id "action/sync"      :label (:action-sync t)})
      true    (conj {:id "action/select"    :label (:action-select t)})
      active? (conj {:id "action/disable"   :label (:action-disable t)})
      true    (conj {:id "action/show-logs" :label (:action-show-logs t)}))))

(defn- on-off [t flag?]
  (if flag?
    (str "$(check) " (:info-on t))
    (str "$(close) " (:info-off t))))

(defn- row
  "Single info row with leading icon, bold key, em-dash separator, value.
  Trailing &nbsp; padding widens the hover so values aren't cramped."
  [icon key value]
  (str icon " **" key "** &nbsp;—&nbsp; " value "&nbsp;"))

(defn- code [s]
  (str "`" s "`"))

(defn build-tooltip-markdown
  "Build the hover-tooltip markdown shown over the status bar item."
  [cfg]
  (let [t              (:label l/lang)
        src            (env/describe-source cfg)
        rendered       (cond-> src
                         (:path src) (update :path unrender-workspace (:workspace-root cfg)))
        active?        (not= :none (:type src))
        flake?         (= :flake (:type src))
        status-emoji   (if active? "🟢" "🔴")
        status-word    (if active? (:info-status-active t) (:info-status-disabled t))
        header-html    (str "<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\">"
                            "<tr>"
                            "<td><h3>"
                            "$(beaker)&nbsp;&nbsp;" (:info-title t)
                            "</h3></td>"
                            "<td align=\"right\"><h3>"
                            status-emoji "&nbsp;" status-word
                            "</h3></td>"
                            "</tr></table>")
        source-row     (row "$(file-code)" (:info-source t) (source-type-label rendered))
        location-row   (cond
                         (:path rendered)
                         (row "$(folder-opened)" (:info-path t) (code (:path rendered)))

                         (seq (:packages rendered))
                         (row "$(package)" (:info-packages t)
                              (s/join ", " (map code (:packages rendered))))

                         :else nil)
        flakes-row     (row "$(symbol-snippet)" (:info-flakes t)
                            (on-off t (boolean (:use-flakes? cfg))))
        flake-shell-row (when flake?
                          (row "$(symbol-snippet)" (:info-flake-shell t)
                               (code (or (:flake-shell cfg) (:flake-shell-default t)))))
        args-row       (when-let [a (not-empty (:nix-args cfg))]
                         (row "$(symbol-parameter)" (:info-args t) (code a)))
        shell-row      (when-let [p (not-empty (:nix-shell-path cfg))]
                         (row "$(tools)" (:info-shell-path t) (code p)))
        terminals-row  (row "$(terminal)" (:info-terminals t)
                            (on-off t (boolean (:patch-terminals? cfg))))
        log-row        (row "$(output)" (:info-log-level t)
                            (code (or (:log-level cfg) "info")))
        workspace-row  (when-let [w (not-empty (:workspace-root cfg))]
                         (row "$(root-folder)" (:info-workspace t) (code w)))
        rows           (->> [source-row
                             location-row
                             flakes-row
                             flake-shell-row
                             args-row
                             shell-row
                             terminals-row
                             log-row
                             workspace-row]
                            (remove nil?))]
    (str header-html
         "<div style=\"margin:14px 0 12px 0;\">\n\n"
         (s/join "\n\n" rows)
         "</div>")))

;; ---------------------------------------------------------------------------
;; Flake-shell picker
;; ---------------------------------------------------------------------------

(defn- save-flake-shell! [shell-name]
  (workspace/config-set! (get-vscode-config)
                         :workspace
                         :nix-env-selector/flake-shell
                         (or shell-name js/undefined)))

(defn- save-nix-file! [path]
  (workspace/config-set! (get-vscode-config)
                         :workspace
                         :nix-env-selector/nix-file
                         (if path
                           (unrender-workspace path (:workspace-root @config))
                           js/undefined)))

(defn- save-use-flakes! [use-flakes?]
  (workspace/config-set! (get-vscode-config)
                         :workspace
                         :nix-env-selector/use-flakes
                         (boolean use-flakes?)))

(defn- mark-current [item is-current?]
  (cond-> item
    is-current? (assoc :description (-> l/lang :label :flake-shell-current))))

(defn- type-pick-items
  "Build step-1 (\"flake or nix-shell\") items. Always includes Disable.
  Flake / nix-shell options appear only when matching files exist in workspace.
  When the workspace has neither, a non-selectable notice is shown above Disable.
  The currently-active type is tagged with the `current` description."
  [files cfg]
  (let [t              (:label l/lang)
        flake-present? (some env/flake-path? files)
        shell-present? (some (complement env/flake-path?) files)
        none-present?  (not (or flake-present? shell-present?))
        current        (:type (env/describe-source cfg))]
    (cond-> []
      none-present?  (conj {:kind -1 :label (:type-picker-no-files t)})
      flake-present? (conj (mark-current {:id "flake"
                                          :label (:type-picker-flake t)}
                                         (= :flake current)))
      shell-present? (conj (mark-current {:id "nix-shell"
                                          :label (:type-picker-nix-shell t)}
                                         (= :nix-shell current)))
      true           (conj (mark-current {:id "disable"
                                          :label (:action-disable t)}
                                         (= :none current))))))

(defn- pick-env-type [files cfg]
  (->> (w/show-quick-pick
        {:place-holder (-> l/lang :label :type-picker-placeholder)
         :title        (-> l/lang :label :info-title)}
        (type-pick-items files cfg))
       (p/map :id)))

(defn- nix-shell-file-items
  "Step-2 nix-shell file picker items. The currently-active file is tagged
  with the `current` description so the user sees which is in use."
  [shell-files cfg]
  (let [current-basename (when-let [path (:nix-file cfg)]
                           (basename path))]
    (vec (for [name shell-files]
           (mark-current {:id name :label name}
                         (= name current-basename))))))

(defn- pick-shell-file [shell-files cfg]
  (->> (w/show-quick-pick
        {:place-holder (-> l/lang :label :select-config-placeholder)
         :title        (-> l/lang :label :info-title)}
        (nix-shell-file-items shell-files cfg))
       (p/map :id)))

(defn- normalize-shell
  "nil and \"default\" are equivalent — both resolve to devShells.<system>.default."
  [s]
  (let [s (not-empty s)]
    (when-not (= s "default") s)))

(defn- same-shell? [a b]
  (= (normalize-shell a) (normalize-shell b)))

(defn- shell-pick-items [shells current]
  (let [current-norm (normalize-shell current)]
    (vec (for [name shells]
           (let [is-current? (= (normalize-shell name) current-norm)]
             (cond-> {:id    name
                      :label (str (if is-current? "$(check) " "    ") name)}
               is-current? (assoc :description (-> l/lang :label :flake-shell-current))))))))

(defn pick-flake-shell
  "Show a QuickPick with available flake devShells. Always presents the list
  with the currently-selected shell marked. Returns a promise resolving to the
  picked shell name, or nil if the user cancelled or the flake exposes zero
  shells."
  [dir]
  (->> (env/list-flake-shells dir)
       (p/mapcat
        (fn [shells]
          (if (empty? shells)
            (do (w/show-error-notification (-> l/lang :label :flake-shell-none))
                (p/resolved nil))
            (->> (w/show-quick-pick {:place-holder (-> l/lang :label :flake-shell-placeholder)
                                     :title        (-> l/lang :label :info-flake-shell)}
                                    (shell-pick-items shells (:flake-shell @config)))
                 (p/map :id)))))))

;; ---------------------------------------------------------------------------
;; Env-loading pipeline
;; ---------------------------------------------------------------------------

(defn- nix-env-options [nix-path]
  {:nix-config     nix-path
   :packages       (:nix-packages @config)
   :args           (:nix-args @config)
   :nix-shell-path (:nix-shell-path @config)
   :use-flakes     (:use-flakes? @config)
   :flake-shell    (:flake-shell @config)})

(defn- has-env-source? [options]
  (or (not-empty (:nix-config options))
      (not-empty (:packages options))))

(defn load-env [options status ctx]
  (when (has-env-source? options)
    (logger/info (str "Loading environment (nix-config=" (:nix-config options)
                      ", packages=" (count (:packages options)) ")"))
    (status-bar/show {:text    (-> l/lang :label :env-loading)
                      :command :nix-env-selector/show-actions
                      :tooltip (build-tooltip-markdown @config)}
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
                                      :command :nix-env-selector/show-actions
                                      :tooltip (build-tooltip-markdown @config)} status))))
         (p/mapcat show-reload-dialog)
         (p/error (handle-error "Failed to load environment")))))

;; ---------------------------------------------------------------------------
;; Top-level command factories
;; ---------------------------------------------------------------------------

(defn show-actions [status ctx]
  (fn []
    (logger/info "Running action: Show actions")
    (let [cfg          @config
          src          (env/describe-source cfg)
          rendered-src (cond-> src
                         (:path src)
                         (update :path unrender-workspace (:workspace-root cfg)))]
      (->> (w/show-quick-pick {:title (-> l/lang :label :info-title)}
                              (info-items rendered-src))
           (p/map (fn [picked]
                    (case (:id picked)
                      "action/sync"        (cmd/execute :nix-env-selector/hit-env)
                      "action/select"      (cmd/execute :nix-env-selector/select-env)
                      "action/disable"     (disable-nix-environment status ctx)
                      "action/show-logs"   (logger/show-channel!)
                      nil)))
           (p/error (handle-error "Show actions failed"))))))

(defn hit-nix-environment [status ctx]
  (fn []
    (logger/info "Running action: Refresh environment")
    (load-env (nix-env-options (:nix-file @config)) status ctx)))

(defn- apply-flake-flow [status ctx flake-path]
  (->> (pick-flake-shell (dirname flake-path))
       (p/mapcat
        (fn [shell]
          (if (nil? shell)
            (p/resolved nil)
            (let [cfg        @config
                  unchanged? (and (= :flake (:type (env/describe-source cfg)))
                                  (same-shell? shell (:flake-shell cfg)))]
              (if unchanged?
                (do (logger/info "Flake selection unchanged; skipping re-apply")
                    (p/resolved nil))
                (do
                  (logger/info (str "Applying flake: " flake-path " (#" shell ")"))
                  (->> (save-nix-file! flake-path)
                       (p/mapcat (fn [_] (save-use-flakes! true)))
                       (p/mapcat (fn [_] (save-flake-shell! shell)))
                       (p/mapcat (fn [_]
                                   (load-env (-> (nix-env-options flake-path)
                                                 (assoc :use-flakes true
                                                        :flake-shell shell))
                                             status ctx))))))))))))

(defn- apply-nix-shell-flow [status ctx shell-files]
  (->> (pick-shell-file shell-files @config)
       (p/mapcat
        (fn [picked]
          (if (nil? picked)
            (p/resolved nil)
            (let [cfg        @config
                  path       (str (:workspace-root cfg) "/" picked)
                  current    (when (:nix-file cfg) (basename (:nix-file cfg)))
                  unchanged? (and (= :nix-shell (:type (env/describe-source cfg)))
                                  (= picked current))]
              (if unchanged?
                (do (logger/info "Nix-shell selection unchanged; skipping re-apply")
                    (p/resolved nil))
                (do
                  (logger/info (str "Applying nix-shell: " path))
                  (->> (save-nix-file! path)
                       (p/mapcat (fn [_] (save-use-flakes! false)))
                       (p/mapcat (fn [_]
                                   (load-env (-> (nix-env-options path)
                                                 (assoc :use-flakes false))
                                             status ctx))))))))))))

(defn select-nix-environment [status ctx]
  (fn []
    (logger/info "Running action: Select environment")
    (let [workspace-root (:workspace-root @config)
          flake-path     (str workspace-root "/flake.nix")]
      (->> (get-nix-files workspace-root)
           (p/mapcat
            (fn [files]
              (let [cfg         @config
                    shell-files (vec (remove env/flake-path? files))
                    items       (type-pick-items files cfg)]
                (if (empty? items)
                  (p/resolved nil)
                  (->> (pick-env-type files cfg)
                       (p/mapcat
                        (fn [picked]
                          (case picked
                            "flake"     (apply-flake-flow status ctx flake-path)
                            "nix-shell" (apply-nix-shell-flow status ctx shell-files)
                            "disable"   (do (disable-nix-environment status ctx)
                                            (p/resolved nil))
                            (p/resolved nil)))))))))
           (p/error (handle-error "Select environment failed"))))))
