(ns ext.nix-env
  (:require ["child_process" :refer [exec execSync]]
            ["path" :refer [dirname]]
            [clojure.string :as s]
            [promesa.core :as p]
            [utils.logger :as logger]))

(defn- list-to-args [pref-arg list]
  (s/join " " (map #(str pref-arg " " %1) list)))

(defn- has-flake? [dir]
  (let [fs (js/require "fs")]
    (try
      (.existsSync fs (str dir "/flake.nix"))
      (catch js/Error _
        false))))

(defn- build-nix-cmd [{:keys [options dir is-flake capture-env?]}]
  (let [{:keys [nix-shell-path nix-config packages args]} options]
    (if is-flake
      (str (if (empty? nix-shell-path) "nix" (s/replace nix-shell-path #" " "\\ "))
           " develop"
           (when dir (str " \"" dir "\""))
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
        is-flake (and use-flakes (has-flake? dir))]
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
