(ns vscode.env
  (:require ["vscode" :refer [env Uri]]))

(set! *warn-on-infer* false)

(defn open-external-url [url]
  (.openExternal env
                 (.parse Uri url)))
