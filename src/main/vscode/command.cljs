(ns vscode.command
  (:require ["vscode" :refer [commands]]
            [vscode.window :as w]
            [utils.interop :refer [clj->js' keyword-to-path]]))

(set! *warn-on-infer* false)

(defn create [cmd-id handler]
  (.registerCommand commands
                    (keyword-to-path cmd-id)
                    (clj->js' handler)))
(defn execute [cmd-id log-channel]
  (w/write-log log-channel (str "Executing command: " cmd-id))
  (.executeCommand commands
                   (keyword-to-path cmd-id)))
