(ns ext.nix-env
  (:require ["child_process" :refer [exec execSync]]
            ["path" :refer [dirname]]
            [clojure.string :as s]
            [promesa.core :as p]
            [vscode.window :as w]))

(defn ^:private list-to-args [pref-arg list]
  (s/join " " (map #(str pref-arg " " %1) list)))

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

(defn ^:private parse-exported-vars [output]
  (->> (s/split output #"declare -x")
       (filter not-empty)
       (map #(-> (s/split (s/trim %1) #"=" 2)
                 ((fn [[name value]]
                    [name (try
                            (js/JSON.parse value)
                            (catch js/Error _ nil))]))))
       (filter not-empty)))

(defn get-nix-env-sync [options log-channel]
  (let [cmd (get-shell-env-cmd options)]
    (w/write-log log-channel (str "Running command synchronously: " cmd))
    (-> (execSync (clj->js cmd {:cwd (dirname (:nix-config options))}))
        (.toString)
        (parse-exported-vars))))

(defn get-nix-env-async [options log-channel]
  (let [env-result (p/deferred)
        cmd (get-shell-env-cmd options)]
    (w/write-log log-channel (str "Running command asynchronously: " cmd))
    (exec (clj->js cmd {:cwd (dirname (:nix-config options))})
          (fn [err result stderr]
            (if (nil? err)
              (p/resolve! env-result result)
              (do
                (w/write-log log-channel (str "Error applying environment: " stderr))
                (p/reject! env-result err)))))
    (p/map parse-exported-vars env-result)))

(defn set-current-env [env-vars]
  (mapv (fn [[name value]]
          (aset js/process.env name value))
        env-vars))
