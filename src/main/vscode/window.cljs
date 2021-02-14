(ns vscode.window
  (:require ["vscode" :refer [window]]
            [manifold-cljs.deferred :as d]
            [utils.interop :refer [js->clj' clj->js']]))

(set! *warn-on-infer* false)

(defn show-quick-pick [options items]
  (let [pick-result (d/deferred)]
    (-> (.showQuickPick window (clj->js' items) (clj->js' options))
        (.then #(d/success! pick-result %1)
               #(d/error! pick-result %1)))
    (d/chain pick-result
             js->clj')))


(defn show-notification [text items]
  (let [pick-result (d/deferred)]
    (-> (apply (.-showInformationMessage window) (clj->js' (into [text] items)))
        (.then #(d/success! pick-result %1)
               #(d/error! pick-result %1)))
    (d/chain pick-result
             js->clj')))
