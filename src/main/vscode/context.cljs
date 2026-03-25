(ns vscode.context)

(set! *warn-on-infer* false)

(defn subscribe [ctx cmd]
  (.push (.-subscriptions ctx) cmd))

(defn apply-env-collection! [ctx env-vars]
  (let [collection (.-environmentVariableCollection ctx)]
    (.clear collection)
    (doseq [[name value] env-vars]
      (when (and name value)
        (.replace collection name value)))))

(defn clear-env-collection! [ctx]
  (.clear (.-environmentVariableCollection ctx)))
