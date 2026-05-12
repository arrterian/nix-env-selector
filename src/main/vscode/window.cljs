(ns vscode.window
  (:require ["vscode" :refer [window]]
            [promesa.core :as p]
            [utils.interop :refer [js->clj' clj->js']]
            [utils.logger :as logger]
            [ext.lang :as l]
            [ext.constants :as constants]))

(defn- thenable->promise [thenable]
  ;; promesa understands thenables natively; this is just for symmetry/typing.
  (p/then thenable identity))

(defn show-quick-pick [options items]
  (-> (.showQuickPick window (clj->js' items) (clj->js' options))
      (thenable->promise)
      (p/then js->clj')))

(defn show-notification [text items]
  (-> (.apply (.-showInformationMessage window)
              window
              (clj->js' (into [text] items)))
      (thenable->promise)
      (p/then js->clj')))


(defn show-error-notification [text]
  (let [show-logs-label (-> l/lang :label :show-logs)]
    (-> (.showErrorMessage window text show-logs-label)
        (.then #(when (= show-logs-label (js->clj' %))
                  (logger/show-channel!))))))

(defn create-log-output-channel []
  (.createOutputChannel window constants/log-channel))
