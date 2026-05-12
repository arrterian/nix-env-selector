(ns vscode.status-bar
  (:require ["vscode" :refer [window]]
            [utils.interop :refer [keyword-to-path]]))

(set! *warn-on-infer* false)

(defn create [alignment priority]
  (.createStatusBarItem window
                        (case alignment
                          :right 2
                          1)
                        priority))

(defn show [{:keys [text command]} status]
  (set! (.-text status) (clj->js text))
  (when command
    (set! (.-command status) (keyword-to-path command)))
  (.show status))

(defn hide [status]
  (.hide status))
