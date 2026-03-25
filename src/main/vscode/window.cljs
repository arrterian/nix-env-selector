(ns vscode.window
  (:require ["vscode" :refer [window]]
            [promesa.core :as p]
            [utils.interop :refer [js->clj' clj->js']]
            [utils.logger :as logger]
            [ext.lang :as l]
            [ext.constants :as constants]))

(defn show-quick-pick [options items]
  (let [pick-result (p/deferred)]
    (-> (.showQuickPick window (clj->js' items) (clj->js' options))
        (.then #(p/resolve! pick-result %1)
               #(p/reject! pick-result %1)))
    (p/chain pick-result
             js->clj')))


(defn show-notification [text items]
  (let [pick-result (p/deferred)]
    (-> (apply (.-showInformationMessage window) (clj->js' (into [text] items)))
        (.then #(p/resolve! pick-result %1)
               #(p/reject! pick-result %1)))
    (p/chain pick-result
             js->clj')))


(defn show-error-notification [text]
  (let [show-logs-label (-> l/lang :label :show-logs)]
    (-> (.showErrorMessage window text show-logs-label)
        (.then #(when (= show-logs-label (js->clj' %))
                  (logger/show-channel!))))))

(defn create-log-output-channel []
  (.createOutputChannel window constants/log-channel))
