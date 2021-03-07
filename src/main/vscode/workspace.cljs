(ns vscode.workspace
  (:require ["vscode" :refer [workspace]]
            [utils.interop :refer [js->clj' keyword-to-path]]
            [promesa.core :as p]))

(set! *warn-on-infer* false)

(defn ^:private config-target->number [target]
  (cond
    (= target :global) 1
    (= target :workspace) 2
    (= target :workspace-folder) 3
    :else (throw (js/Error "Wrong target for updating config."))))

(defn ^:private uri-to-string [js-uri]
  (.-path js-uri))

(defn get-folders []
  (->> (js->clj' (.-workspaceFolders workspace))
       (map (comp uri-to-string :uri))))

(defn get-configuration []
  (.getConfiguration workspace))

(defn config-get [config param]
  (let [js-param (keyword-to-path param)]
    (.get config js-param)))

(defn config-set! [config target key value]
  (let [update-result (p/deferred)
        target        (config-target->number target)
        path          (keyword-to-path key)]
    (-> (.update config path value target)
        (.then #(p/resolve! update-result %1))
        (.catch #(p/reject! update-result %1)))
    update-result))

(defn on-config-change [handler-fn]
  (.onDidChangeConfiguration workspace
                             (fn [_]
                               (handler-fn))))
