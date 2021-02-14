(ns vscode.workspace
  (:require ["vscode" :refer [workspace]]
            [utils.interop :refer [js->clj' keyword-to-path]]
            [manifold-cljs.deferred :as d]))

(set! *warn-on-infer* false)

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
  (let [update-result (d/deferred)
        target (cond
                 (= target :global) 1
                 (= target :workspace) 2
                 (= target :workspace-folder) 3
                 :else (throw (js/Error "Wrong target for updating config.")))
        path (keyword-to-path key)]
    (-> (.update config path value target)
        (.then #(d/success! update-result %1))
        (.catch #(d/error! update-result %1)))
    update-result))
