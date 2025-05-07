(ns ext.nix-env
  (:require ["child_process" :refer [exec execSync]]
            ["path" :refer [dirname]]
            [clojure.string :as s]
            [promesa.core :as p]
            [vscode.window :as w]))

(defn ^:private list-to-args [pref-arg list]
  (s/join " " (map #(str pref-arg " " %1) list)))

(defn ^:private has-flake? [dir]
  (let [fs (js/require "fs")]
    (try
      (.existsSync fs (str dir "/flake.nix"))
      (catch js/Error _
        false))))

(defn ^:private get-shell-env-cmd [{:keys [packages
                                           nix-shell-path
                                           nix-config
                                           args]}]
  (str (if (empty? nix-shell-path)
         "nix-shell"
         (s/replace nix-shell-path #" " "\\ "))
       " "
       (cond
         (not-empty nix-config)
         (str "\"" nix-config "\"")

         (not-empty packages)
         (list-to-args "-p" packages)

         :else
         (throw (js/Error. "Nix-config or list of packages is necessary")))
       " --run export"
       (when args
         (str " " args))))

(defn ^:private get-flake-env-cmd [{:keys [nix-path dir args]}]
  (str (if (empty? nix-path)
         "nix"
         (s/replace nix-path #" " "\\ "))
       " develop "
       (when dir
         (str "\"" dir "\""))
       " --command env"
       (when args
         (str " " args))))

(defn ^:private parse-exported-vars [output]
  (->> (s/split output #"declare -x")
       (filter not-empty)
       (map #(-> (s/split (s/trim %1) #"=" 2)
                 ((fn [[name value]]
                    [name (try
                            (js/JSON.parse value)
                            (catch js/Error _ nil))]))))
       (filter not-empty)))

(defn ^:private parse-env-vars [output]
  (->> (s/split output #"\n")
       (filter not-empty)
       (map #(s/split %1 #"=" 2))
       (filter #(= (count %) 2))
       (map (fn [[name value]]
              [name value]))))

(defn get-nix-env-sync [{:keys [use-flakes] :as options} log-channel]
  (let [dir (dirname (:nix-config options))
        is-flake (and use-flakes (has-flake? dir))
        cmd (if is-flake
              (get-flake-env-cmd (assoc options :dir dir))
              (get-shell-env-cmd options))
        parser (if is-flake parse-env-vars parse-exported-vars)]
    (w/write-log log-channel (str "Running command synchronously: " cmd))
    (-> (execSync (clj->js cmd {:cwd dir}))
        (.toString)
        (parser))))

(defn get-nix-env-async [{:keys [use-flakes] :as options} log-channel]
  (let [env-result (p/deferred)
        dir (dirname (:nix-config options))
        is-flake (and use-flakes (has-flake? dir))
        cmd (if is-flake
              (get-flake-env-cmd (assoc options :dir dir))
              (get-shell-env-cmd options))
        parser (if is-flake parse-env-vars parse-exported-vars)]
    (w/write-log log-channel (str "Running command " (if is-flake "with flake" "with nix-shell") ": " cmd))
    (exec (clj->js cmd {:cwd dir})
          (fn [err result stderr]
            (if (nil? err)
              (p/resolve! env-result result)
              (do
                (w/write-log log-channel (str "Error applying environment: " stderr))
                (p/reject! env-result err)))))
    (p/map parser env-result)))

(defn set-current-env [env-vars]
  (mapv (fn [[name value]]
          (aset js/process.env name value))
        env-vars))
