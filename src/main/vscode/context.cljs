(ns vscode.context)

(set! *warn-on-infer* false)

(defn subsciribe [ctx cmd]
  (.push (.-subscriptions ctx) cmd))

(defn global-state [ctx]
  (.-globalState ctx))

(defn add-to-global-state [ctx key value]
  (let [state (global-state ctx)]
    (.update state key value)))

(defn get-from-global-state [ctx key]
  (let [state (global-state ctx)]
    (.get state key)))
