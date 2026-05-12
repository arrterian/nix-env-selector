(ns ext.nix-env
  (:require ["child_process" :refer [exec execSync]]
            ["path" :refer [dirname]]
            [clojure.string :as s]
            [promesa.core :as p]
            [utils.logger :as logger]))

(defn- list-to-args [pref-arg list]
  (s/join " " (map #(str pref-arg " " %1) list)))

(defn flake-dir? [dir]
  (let [fs (js/require "fs")]
    (try
      (.existsSync fs (str dir "/flake.nix"))
      (catch js/Error _
        false))))

(defn describe-source
  "Inspect the workspace config and return a map describing the active env source.
  :type is one of :flake, :nix-shell, :packages, :none."
  [{:keys [nix-file nix-packages use-flakes?]}]
  (let [nix-file (not-empty nix-file)
        pkgs     (seq nix-packages)]
    (cond
      nix-file (let [dir (dirname nix-file)
                     flake? (and use-flakes? (flake-dir? dir))]
                 {:type (if flake? :flake :nix-shell)
                  :path nix-file})
      pkgs     {:type :packages :packages (vec pkgs)}
      :else    {:type :none})))

(defn list-flake-shells
  "Run `nix flake show --json` in dir and return a promise resolving to a
  deduplicated vector of devShell names. Resolves to [] on parse failure or
  when the flake exposes no devShells. Rejects on nix command failure."
  [dir]
  (let [result (p/deferred)
        cmd "nix flake show --json --no-write-lock-file"]
    (logger/info (str "Running: " cmd " (cwd=" dir ")"))
    (exec cmd
          #js {:cwd dir}
          (fn [err stdout stderr]
            (if err
              (do
                (when (not-empty stderr) (logger/debug (str "stderr:\n" stderr)))
                (logger/error "nix flake show failed" (js/Error. (or stderr (.-message err))))
                (p/reject! result err))
              (try
                (let [parsed     (js/JSON.parse stdout)
                      dev-shells (.-devShells parsed)]
                  (if (nil? dev-shells)
                    (p/resolve! result [])
                    (let [systems (.keys js/Object dev-shells)
                          names   (->> systems
                                       (mapcat (fn [sys]
                                                 (let [shells (aget dev-shells sys)]
                                                   (.keys js/Object shells))))
                                       distinct
                                       vec)]
                      (logger/info (str "Found " (count names) " flake devShell(s): "
                                        (s/join ", " names)))
                      (p/resolve! result names))))
                (catch js/Error e
                  (logger/error "Failed to parse flake show output" e)
                  (p/resolve! result []))))))
    result))

(defn- flake-target
  "Build the flake installable string `dir` or `dir#shell` for `nix develop`."
  [dir flake-shell]
  (let [shell (not-empty flake-shell)]
    (str dir (when (and shell (not= shell "default")) (str "#" shell)))))

(defn- build-nix-cmd [{:keys [options dir is-flake capture-env?]}]
  (let [{:keys [nix-shell-path nix-config packages args flake-shell]} options]
    (if is-flake
      (str (if (empty? nix-shell-path) "nix" (s/replace nix-shell-path #" " "\\ "))
           " develop"
           (when dir (str " \"" (flake-target dir flake-shell) "\""))
           (when capture-env? " --command env")
           (when args (str " " args)))
      (str (if (empty? nix-shell-path) "nix-shell" (s/replace nix-shell-path #" " "\\ "))
           " "
           (cond
             (not-empty nix-config) (str "\"" nix-config "\"")
             (not-empty packages)   (list-to-args "-p" packages)
             :else (throw (js/Error. "Nix-config or list of packages is necessary")))
           (when capture-env? " --run export")
           (when args (str " " args))))))

(defn- parse-exported-vars [output]
  (let [entries (->> (s/split output #"declare -x")
                     (map s/trim)
                     (filter not-empty)
                     (map (fn [entry]
                            (let [[name value] (s/split entry #"=" 2)]
                              [name (try
                                      (js/JSON.parse value)
                                      (catch js/Error _ ::parse-failed))]))))
        failed  (filter (fn [[_ v]] (= v ::parse-failed)) entries)]
    (when (seq failed)
      (logger/warn (str "Dropped " (count failed)
                        " env var(s) with unparseable values: "
                        (s/join ", " (map first failed)))))
    (->> entries
         (remove (fn [[_ v]] (= v ::parse-failed))))))

(defn- parse-env-vars [output]
  (->> (s/split output #"\n")
       (filter not-empty)
       (map #(s/split %1 #"=" 2))
       (filter #(= (count %) 2))))

(defn- prepare-invocation
  "Resolve dir, flake-vs-nix-shell mode, the command string, and the matching
  parser. Returns a map with :dir :is-flake :cmd :parser."
  [{:keys [use-flakes] :as options}]
  (let [dir      (dirname (:nix-config options))
        is-flake (and use-flakes (flake-dir? dir))]
    {:dir      dir
     :is-flake is-flake
     :cmd      (build-nix-cmd {:options options :dir dir :is-flake is-flake :capture-env? true})
     :parser   (if is-flake parse-env-vars parse-exported-vars)}))

(defn- log-parsed [vars]
  (logger/info (str "Parsed " (count vars) " environment variables"))
  vars)

(defn get-nix-env-sync [options]
  (let [{:keys [dir is-flake cmd parser]} (prepare-invocation options)]
    (logger/info (str "Running (sync, " (if is-flake "flake" "nix-shell") "): " cmd))
    (let [stdout (.toString (execSync cmd #js {:cwd dir}))]
      (logger/debug (str "stdout:\n" stdout))
      (log-parsed (parser stdout)))))

(defn get-nix-env-async [options]
  (let [{:keys [dir is-flake cmd parser]} (prepare-invocation options)
        env-result (p/deferred)]
    (logger/info (str "Running (" (if is-flake "flake" "nix-shell") "): " cmd))
    (exec cmd
          #js {:cwd dir}
          (fn [err result stderr]
            (if (nil? err)
              (do
                (logger/debug (str "stdout:\n" result))
                (when (not-empty stderr)
                  (logger/debug (str "stderr:\n" stderr)))
                (logger/info "Command completed successfully")
                (p/resolve! env-result result))
              (do
                (logger/debug (str "stderr:\n" stderr))
                (logger/error "nix command failed" (js/Error. stderr))
                (p/reject! env-result err)))))
    (p/map (comp log-parsed parser) env-result)))

(defn set-current-env [env-vars]
  (run! (fn [[name value]]
          (aset js/process.env name value))
        env-vars))
