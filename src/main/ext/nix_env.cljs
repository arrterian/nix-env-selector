(ns ext.nix-env
  (:require ["child_process" :refer [exec execSync]]
            [clojure.string :as s]
            [promesa.core :as p]))

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

(defn get-nix-env-sync [options]
  (-> (get-shell-env-cmd options)
      (execSync)
      (.toString)
      (parse-exported-vars)))

(defn get-nix-env-async [options]
  (let [env-result (p/deferred)]
    (exec (get-shell-env-cmd options)
          (fn [err result]
            (if (nil? err)
              (p/resolve! env-result result)
              (p/reject! env-result err))))
    (p/chain env-result parse-exported-vars)))

(defn set-current-env [env-vars]
  (mapv (fn [[name value]]
          (aset js/process.env name value))
        env-vars))
