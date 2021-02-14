(ns utils.interop
  (:require [camel-snake-kebab.core :refer [->kebab-case-keyword ->camelCaseKeyword ->camelCaseString]]
            [clojure.walk :refer [postwalk]]
            [goog.object :as obj]))

(defn ^:private numeric-string? [s]
  (and (string? s)
       (some? (re-matches #"[0-9]+" s))))
(defn ^:private pascal-case? [s]
  (and (string? s)
       (contains? #{\A \B \C \D \E \F \G \H \I \J \K \L \M \N \O \P \Q \R \S \T \U \V \W \X \Y \Z}
                  (first s))))

(defn ^:private key->str [k]
  (let [n (name k)]
    (cond
      (pascal-case? n) n
      :else (->camelCaseString k))))

(defn ^:private convert-map-keys [m f]
  (postwalk (fn [x]
              (if (map-entry? x)
                [(f (key x)) (val x)]
                x))
            m))


(defn clj->js'
  [obj]
  (clj->js (convert-map-keys obj (fn [k]
                                   (if (keyword? k)
                                     (key->str k)
                                     k)))))

(defn ^:private js-key->clj [k]
  (cond
    (keyword? k) k
    (numeric-string? k) (js/parseInt k)
    (pascal-case? k) (keyword k)
    :else (->kebab-case-keyword k)))

(defn js->clj'
  [obj]
  (let [convert (fn convert [x]
                  (cond
                    (seq? x)
                    (doall (map convert x))

                    (map-entry? x)
                    (MapEntry. (convert (key x)) (convert (val x)) nil)

                    (coll? x)
                    (into (empty x) (map convert) x)

                    (array? x)
                    (persistent!
                     (reduce #(conj! %1 (convert %2))
                             (transient []) x))

                    (identical? (type x) js/Object)
                    (persistent!
                     (reduce (fn [r k]
                               (if (= "ref" k)
                                 (assoc! r :ref (obj/get x k))
                                 (assoc! r (js-key->clj k) (convert (obj/get x k)))))
                             (transient {}) (js-keys x)))
                    :else x))]
    (convert obj)))

(defn keyword-to-path [key]
  (str
   (->camelCaseString (namespace key))
   "."
   (->camelCaseString (name key))))