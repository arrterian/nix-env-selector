(ns vscode.status-bar
  (:require ["vscode" :refer [window]]
            [utils.interop :refer [keyword-to-path]]))

(set! *warn-on-infer* false)

(defn create [aligment, priority]
  (.createStatusBarItem window
                        (cond
                          (= :left aligment) 1
                          (= :right aligment) 2
                          :else 1)
                        priority))

(defn show [{:keys [text command]} status]
  ;; (.hide status)
  (set! (.-text status) (clj->js text))
  (when command
    (set! (.-command status) (keyword-to-path command)))
  (.show status))

(defn hide [status]
  (.hide status))
