(ns vscode.command
  (:require ["vscode" :refer [commands]]
            [utils.interop :refer [clj->js' keyword-to-path]]))

(set! *warn-on-infer* false)

(defn create [cmd-id handler]
  (.registerCommand commands
                    (keyword-to-path cmd-id)
                    (clj->js' handler)))
(defn execute [cmd-id]
  (.executeCommand commands
                    (keyword-to-path cmd-id)))