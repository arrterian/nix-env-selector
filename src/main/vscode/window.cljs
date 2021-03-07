(ns vscode.window
  (:require ["vscode" :refer [window]]
            [promesa.core :as p]
            [utils.interop :refer [js->clj' clj->js']]))

(set! *warn-on-infer* false)

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
