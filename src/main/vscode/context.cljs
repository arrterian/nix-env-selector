(ns vscode.context)

(set! *warn-on-infer* false)

(defn subscribe [ctx cmd]
  (.push (.-subscriptions ctx) cmd))

(defn apply-env-collection! [ctx env-vars]
  (let [collection (.-environmentVariableCollection ctx)]
    (set! (.-persistent collection) true)
    (set! (.-description collection) "Nix environment variables from nix-shell")
    (.clear collection)
    (doseq [[name value] env-vars]
      (when (and name value)
        (.replace collection name value)))))

(defn clear-env-collection! [ctx]
  (let [collection (.-environmentVariableCollection ctx)]
    (set! (.-persistent collection) false)
    (.clear collection)))
