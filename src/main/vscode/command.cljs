(ns vscode.command
  (:require ["vscode" :refer [commands]]
            [utils.logger :as logger]
            [utils.interop :refer [clj->js' keyword-to-path]]))

(set! *warn-on-infer* false)

(defn create [cmd-id handler]
  (.registerCommand commands
                    (keyword-to-path cmd-id)
                    (clj->js' handler)))

(defn execute [cmd-id]
  (logger/debug (str "Executing command: " (keyword-to-path cmd-id)))
  (.executeCommand commands
                   (keyword-to-path cmd-id)))

(defn execute-raw [cmd-str]
  (.executeCommand commands cmd-str))
