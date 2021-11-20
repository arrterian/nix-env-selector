(ns vscode.window
  (:require ["vscode" :refer [window]]
            [vscode.context :as context]
            [promesa.core :as p]
            [utils.interop :refer [js->clj' clj->js']]
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


(defn create-output-channel [ctx]
  (let [output-channel (.createOutputChannel window constants/log-channel)]
    (context/add-to-global-state ctx constants/log-channel output-channel)))


(defn get-output-channel [ctx]
  (context/get-from-global-state ctx constants/log-channel))


(defn write-log [^OutputChannel channel text]
  (.appendLine channel text))
