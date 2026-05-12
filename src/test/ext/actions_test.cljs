(ns ext.actions-test
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [ext.actions :as a]
            [ext.lang :refer [lang]]))

(def ^:private L (:label lang))

;; ---------------------------------------------------------------------------
;; normalize-shell / same-shell?
;; ---------------------------------------------------------------------------

(deftest normalize-shell
  (are [in expected] (= expected (#'a/normalize-shell in))
    nil       nil
    ""        nil
    "default" nil
    "tests"   "tests"
    "ci"      "ci"))

(deftest same-shell?
  (testing "default and nil are equivalent"
    (is (true? (#'a/same-shell? nil "default")))
    (is (true? (#'a/same-shell? "default" nil)))
    (is (true? (#'a/same-shell? "default" "default")))
    (is (true? (#'a/same-shell? nil nil))))

  (testing "blank treated as nil"
    (is (true? (#'a/same-shell? "" nil)))
    (is (true? (#'a/same-shell? "" "default"))))

  (testing "different named shells are not the same"
    (is (false? (#'a/same-shell? "tests" "ci")))
    (is (false? (#'a/same-shell? "tests" nil)))
    (is (false? (#'a/same-shell? "tests" "default"))))

  (testing "named shell same to itself"
    (is (true? (#'a/same-shell? "tests" "tests")))))

;; ---------------------------------------------------------------------------
;; mark-current
;; ---------------------------------------------------------------------------

(deftest mark-current
  (testing "adds the `current` description when flagged"
    (is (= {:id "x" :label "X" :description (:flake-shell-current L)}
           (#'a/mark-current {:id "x" :label "X"} true))))

  (testing "leaves item alone when not current"
    (is (= {:id "x" :label "X"}
           (#'a/mark-current {:id "x" :label "X"} false)))))

;; ---------------------------------------------------------------------------
;; source-type-label / source-detail
;; ---------------------------------------------------------------------------

(deftest source-type-label
  (are [src expected] (= expected (#'a/source-type-label src))
    {:type :flake}     (:info-source-flake L)
    {:type :nix-shell} (:info-source-nix-shell L)
    {:type :packages}  (:info-source-packages L)
    {:type :none}      (:info-source-none L)))

(deftest source-detail
  (testing "formats path source"
    (is (= (str (:info-path L) ": /r/flake.nix")
           (#'a/source-detail {:type :flake :path "/r/flake.nix"}))))

  (testing "formats packages source with comma separator"
    (is (= (str (:info-packages L) ": hello, ripgrep")
           (#'a/source-detail {:type :packages :packages ["hello" "ripgrep"]}))))

  (testing "returns nil for empty source"
    (is (nil? (#'a/source-detail {:type :none})))))

;; ---------------------------------------------------------------------------
;; info-items — shape of the status-bar `Show actions` quick-pick
;; ---------------------------------------------------------------------------

(defn- ids [items]
  (->> items (map :id) (remove nil?) vec))

(deftest info-items
  (testing "active source: sync + select + disable + show-logs"
    (is (= ["action/sync" "action/select" "action/disable" "action/show-logs"]
           (ids (#'a/info-items {:type :flake :path "/r/flake.nix"})))))

  (testing "inactive (:none): only select + show-logs, no sync or disable"
    (is (= ["action/select" "action/show-logs"]
           (ids (#'a/info-items {:type :none})))))

  (testing "leading entry is a quick-pick separator"
    (is (= -1 (:kind (first (#'a/info-items {:type :none}))))))

  (testing "header includes the source type label"
    (let [header (-> (#'a/info-items {:type :flake :path "/r/flake.nix"})
                     first :label)]
      (is (re-find (re-pattern (:info-source-flake L)) header))
      (is (re-find #"/r/flake\.nix" header)))))

;; ---------------------------------------------------------------------------
;; shell-pick-items
;; ---------------------------------------------------------------------------

(defn- labels [items] (mapv :label items))
(defn- by-id [items id] (some #(when (= id (:id %)) %) items))

(deftest shell-pick-items
  (testing "all shells appear in the order provided"
    (let [out (#'a/shell-pick-items ["default" "tests" "ci"] "tests")]
      (is (= ["default" "tests" "ci"] (mapv :id out)))))

  (testing "current shell is marked with $(check) and `current` description"
    (let [out  (#'a/shell-pick-items ["default" "tests" "ci"] "tests")
          item (by-id out "tests")]
      (is (= "$(check) tests" (:label item)))
      (is (= (:flake-shell-current L) (:description item)))))

  (testing "non-current shells get whitespace prefix for alignment, no description"
    (let [out  (#'a/shell-pick-items ["default" "tests" "ci"] "tests")
          item (by-id out "ci")]
      (is (= "    ci" (:label item)))
      (is (nil? (:description item)))))

  (testing "nil current ≡ \"default\" — `default` gets the checkmark"
    (let [out (#'a/shell-pick-items ["default" "tests"] nil)]
      (is (= (:flake-shell-current L) (:description (by-id out "default"))))
      (is (nil? (:description (by-id out "tests"))))))

  (testing "after disable: empty/blank current marks nothing"
    (let [out (#'a/shell-pick-items ["tests" "ci"] "")]
      (is (every? nil? (map :description out)))
      (is (every? #(re-matches #"^    .+" %) (labels out)))))

  (testing "empty shell list yields empty result"
    (is (= [] (#'a/shell-pick-items [] "tests")))))

;; ---------------------------------------------------------------------------
;; type-pick-items
;; ---------------------------------------------------------------------------

(deftest type-pick-items
  (testing "no .nix files: notice + disable only"
    (let [out (#'a/type-pick-items [] {})]
      (is (= [nil "disable"] (mapv :id out)))
      (is (= -1 (:kind (first out))))
      (is (= (:type-picker-no-files L) (:label (first out))))))

  (testing "only flake.nix present: flake + disable"
    (let [out (#'a/type-pick-items ["flake.nix"] {})]
      (is (= ["flake" "disable"] (mapv :id out)))))

  (testing "only nix-shell file: nix-shell + disable"
    (let [out (#'a/type-pick-items ["shell.nix"] {})]
      (is (= ["nix-shell" "disable"] (mapv :id out)))))

  (testing "both present: flake + nix-shell + disable, in that order"
    (let [out (#'a/type-pick-items ["flake.nix" "shell.nix"] {})]
      (is (= ["flake" "nix-shell" "disable"] (mapv :id out)))))

  (testing "current type gets the `current` description"
    (let [cfg {:nix-file "/r/flake.nix" :use-flakes? true}
          out (#'a/type-pick-items ["flake.nix" "shell.nix"] cfg)]
      (is (= (:flake-shell-current L) (:description (by-id out "flake"))))
      (is (nil? (:description (by-id out "nix-shell"))))
      (is (nil? (:description (by-id out "disable"))))))

  (testing "after disable (:none): only `disable` is marked current"
    (let [out (#'a/type-pick-items ["flake.nix" "shell.nix"] {})]
      (is (nil? (:description (by-id out "flake"))))
      (is (nil? (:description (by-id out "nix-shell"))))
      (is (= (:flake-shell-current L) (:description (by-id out "disable")))))))

;; ---------------------------------------------------------------------------
;; nix-shell-file-items
;; ---------------------------------------------------------------------------

(deftest nix-shell-file-items
  (testing "all files listed, matching basename marked current"
    (let [cfg {:nix-file "/r/shell.nix"}
          out (#'a/nix-shell-file-items ["shell.nix" "alt.nix"] cfg)]
      (is (= ["shell.nix" "alt.nix"] (mapv :id out)))
      (is (= (:flake-shell-current L) (:description (by-id out "shell.nix"))))
      (is (nil? (:description (by-id out "alt.nix"))))))

  (testing "no nix-file in cfg: nothing marked current"
    (let [out (#'a/nix-shell-file-items ["shell.nix" "alt.nix"] {})]
      (is (every? nil? (map :description out)))))

  (testing "nix-file in cfg points to a file not in the workspace list"
    (let [cfg {:nix-file "/r/gone.nix"}
          out (#'a/nix-shell-file-items ["shell.nix"] cfg)]
      (is (every? nil? (map :description out))))))
