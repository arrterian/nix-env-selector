(ns vscode.context)

(set! *warn-on-infer* false)

(defn subsciribe [ctx cmd]
  (.push (.-subscriptions ctx) cmd))

(defn global-state [ctx]
  (.-globalState ctx))
