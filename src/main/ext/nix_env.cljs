(ns ext.nix-env
  (:require ["child_process" :refer [exec execSync]]
            ["path" :refer [dirname]]
            [clojure.string :as s]
            [promesa.core :as p]
            [utils.logger :as logger]))

(defn ^:private list-to-args [pref-arg list]
  (s/join " " (map #(str pref-arg " " %1) list)))

(defn ^:private has-flake? [dir]
  (let [fs (js/require "fs")]
    (try
      (.existsSync fs (str dir "/flake.nix"))
      (catch js/Error _
        false))))

(defn ^:private build-nix-cmd [{:keys [options dir is-flake capture-env?]}]
  (let [{:keys [nix-shell-path nix-config packages args flake-dev-shell]} options
        flake-ref (when dir
                    (if (not-empty flake-dev-shell)
                      (str dir "#" flake-dev-shell)
                      dir))]
    (if is-flake
      (str (if (empty? nix-shell-path) "nix" (s/replace nix-shell-path #" " "\\ "))
           " develop"
           (when flake-ref (str " \"" flake-ref "\""))
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

(defn get-nix-env-sync [{:keys [use-flakes] :as options}]
  (let [dir (dirname (:nix-config options))
        is-flake (and use-flakes (has-flake? dir))
        cmd (build-nix-cmd {:options options :dir dir :is-flake is-flake :capture-env? true})
        parser (if is-flake parse-env-vars parse-exported-vars)]
    (logger/info (str "Running (sync): " cmd))
    (let [stdout (-> (execSync (clj->js cmd {:cwd dir}))
                     (.toString))
          result (parser stdout)]
      (logger/debug (str "stdout:\n" stdout))
      (logger/info (str "Parsed " (count result) " environment variables"))
      result)))

(defn get-nix-env-async [{:keys [use-flakes] :as options}]
  (let [env-result (p/deferred)
        dir (dirname (:nix-config options))
        is-flake (and use-flakes (has-flake? dir))
        cmd (build-nix-cmd {:options options :dir dir :is-flake is-flake :capture-env? true})
        parser (if is-flake parse-env-vars parse-exported-vars)]
    (logger/info (str "Running (" (if is-flake "flake" "nix-shell") "): " cmd))
    (exec (clj->js cmd {:cwd dir})
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
    (p/map (fn [raw]
             (let [vars (parser raw)]
               (logger/info (str "Parsed " (count vars) " environment variables"))
               vars))
           env-result)))

(defn flake? [nix-path]
  (and nix-path
       (s/ends-with? nix-path "/flake.nix")))

(defn ^:private extract-dev-shell-names [parsed]
  (let [;; Newer "inventory" schema (Nix with flake-schemas):
        ;; inventory.devShells.output.children.<system>.children.<name>
        inventory-systems (get-in parsed ["inventory" "devShells" "output" "children"])
        ;; Older flat schema: devShells.<system>.<name>
        flat-systems (get parsed "devShells")
        systems (or inventory-systems flat-systems)]
    (->> (vals (or systems {}))
         (filter map?)
         (mapcat (fn [system-data]
                   (or (keys (get system-data "children"))
                       (when-not (contains? system-data "filtered")
                         (keys system-data)))))
         (distinct)
         (vec))))

(defn list-flake-dev-shells [{:keys [nix-shell-path nix-config]}]
  (let [result (p/deferred)
        dir (dirname nix-config)
        nix-bin (if (empty? nix-shell-path) "nix" (s/replace nix-shell-path #" " "\\ "))
        cmd (str nix-bin " flake show \"" dir "\" --json --no-write-lock-file")]
    (logger/info (str "Listing flake devShells: " cmd))
    (exec cmd
          #js {:cwd dir :maxBuffer (* 32 1024 1024)}
          (fn [err stdout stderr]
            (if (nil? err)
              (try
                (let [parsed (js->clj (js/JSON.parse stdout))
                      shell-names (extract-dev-shell-names parsed)]
                  (logger/debug (str "flake show stdout length: " (count stdout)))
                  (logger/info (str "Found devShells: " shell-names))
                  (p/resolve! result shell-names))
                (catch js/Error e
                  (logger/error "Failed to parse flake show output" e)
                  (p/reject! result e)))
              (do
                (logger/debug (str "stderr:\n" stderr))
                (logger/error "nix flake show failed" (js/Error. stderr))
                (p/reject! result err)))))
    result))

(defn set-current-env [env-vars]
  (mapv (fn [[name value]]
          (aset js/process.env name value))
        env-vars))
