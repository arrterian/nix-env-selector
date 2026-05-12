(ns ext.nix-env-test
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [ext.nix-env :as env]))

;; ---------------------------------------------------------------------------
;; flake-path?
;; ---------------------------------------------------------------------------

(deftest flake-path?
  (testing "matches a path whose basename is flake.nix"
    (is (true?  (env/flake-path? "flake.nix")))
    (is (true?  (env/flake-path? "/work/repo/flake.nix")))
    (is (true?  (env/flake-path? "./flake.nix"))))

  (testing "rejects non-flake names"
    (is (false? (env/flake-path? "shell.nix")))
    (is (false? (env/flake-path? "/work/flake.nix.bak"))))

  (testing "rejects non-string / blank inputs"
    (is (false? (env/flake-path? nil)))
    (is (false? (env/flake-path? "")))))

;; ---------------------------------------------------------------------------
;; describe-source
;; ---------------------------------------------------------------------------

(deftest describe-source
  (testing "nix-file + use-flakes? → :flake"
    (is (= {:type :flake :path "/r/flake.nix"}
           (env/describe-source {:nix-file "/r/flake.nix" :use-flakes? true}))))

  (testing "nix-file without use-flakes? → :nix-shell"
    (is (= {:type :nix-shell :path "/r/shell.nix"}
           (env/describe-source {:nix-file "/r/shell.nix" :use-flakes? false}))))

  (testing "packages only → :packages"
    (is (= {:type :packages :packages ["a" "b"]}
           (env/describe-source {:nix-packages ["a" "b"]}))))

  (testing "nix-file takes precedence over packages"
    (is (= :flake
           (:type (env/describe-source {:nix-file     "/r/flake.nix"
                                        :use-flakes?  true
                                        :nix-packages ["a"]})))))

  (testing "empty nix-file is treated as absent"
    (is (= :none (:type (env/describe-source {:nix-file ""})))))

  (testing "empty packages collection is treated as absent"
    (is (= :none (:type (env/describe-source {:nix-packages []})))))

  (testing "nothing configured → :none"
    (is (= {:type :none} (env/describe-source {})))))

;; ---------------------------------------------------------------------------
;; build-nix-cmd (private)
;; ---------------------------------------------------------------------------

(defn- build [opts]
  (#'env/build-nix-cmd opts))

(deftest build-nix-cmd-flake
  (testing "default shell omits #shell suffix"
    (is (= "nix develop \"/r\" --command env"
           (build {:options      {:flake-shell "default"}
                   :dir          "/r"
                   :is-flake     true
                   :capture-env? true}))))

  (testing "nil shell omits #shell suffix"
    (is (= "nix develop \"/r\" --command env"
           (build {:options      {:flake-shell nil}
                   :dir          "/r"
                   :is-flake     true
                   :capture-env? true}))))

  (testing "named shell becomes installable suffix"
    (is (= "nix develop \"/r#tests\" --command env"
           (build {:options      {:flake-shell "tests"}
                   :dir          "/r"
                   :is-flake     true
                   :capture-env? true}))))

  (testing "capture-env? false omits --command env"
    (is (= "nix develop \"/r#tests\""
           (build {:options      {:flake-shell "tests"}
                   :dir          "/r"
                   :is-flake     true
                   :capture-env? false}))))

  (testing "custom nix-shell-path replaces `nix` and escapes spaces"
    (is (= "/opt/my\\ nix/bin/nix develop \"/r\" --command env"
           (build {:options      {:nix-shell-path "/opt/my nix/bin/nix"}
                   :dir          "/r"
                   :is-flake     true
                   :capture-env? true}))))

  (testing "args are appended"
    (is (= "nix develop \"/r\" --command env --impure"
           (build {:options      {:args "--impure"}
                   :dir          "/r"
                   :is-flake     true
                   :capture-env? true})))))

(deftest build-nix-cmd-nix-shell
  (testing "nix-config path is quoted"
    (is (= "nix-shell \"/r/shell.nix\" --run export"
           (build {:options      {:nix-config "/r/shell.nix"}
                   :dir          "/r"
                   :is-flake     false
                   :capture-env? true}))))

  (testing "packages produce repeated -p args"
    (is (= "nix-shell -p hello -p ripgrep --run export"
           (build {:options      {:packages ["hello" "ripgrep"]}
                   :dir          nil
                   :is-flake     false
                   :capture-env? true}))))

  (testing "missing source throws"
    (is (thrown? js/Error
                 (build {:options      {}
                         :dir          nil
                         :is-flake     false
                         :capture-env? true}))))

  (testing "capture-env? false omits --run export"
    (is (= "nix-shell \"/r/shell.nix\""
           (build {:options      {:nix-config "/r/shell.nix"}
                   :dir          "/r"
                   :is-flake     false
                   :capture-env? false}))))

  (testing "custom nix-shell-path is honored and space-escaped"
    (is (= "/opt/my\\ nix/bin/nix-shell \"/r/shell.nix\" --run export"
           (build {:options      {:nix-shell-path "/opt/my nix/bin/nix-shell"
                                  :nix-config     "/r/shell.nix"}
                   :dir          "/r"
                   :is-flake     false
                   :capture-env? true}))))

  (testing "args are appended"
    (is (= "nix-shell \"/r/shell.nix\" --run export -A some.thing"
           (build {:options      {:nix-config "/r/shell.nix"
                                  :args       "-A some.thing"}
                   :dir          "/r"
                   :is-flake     false
                   :capture-env? true})))))

;; ---------------------------------------------------------------------------
;; parse-exported-vars (private)  — `declare -x NAME="value"` format
;; ---------------------------------------------------------------------------

(deftest parse-exported-vars
  (testing "parses a single quoted entry"
    (is (= [["FOO" "bar"]]
           (vec (#'env/parse-exported-vars "declare -x FOO=\"bar\"")))))

  (testing "parses multiple entries separated by `declare -x`"
    (let [out (str "declare -x FOO=\"bar\"\n"
                   "declare -x BAZ=\"qux\"\n")]
      (is (= [["FOO" "bar"] ["BAZ" "qux"]]
             (vec (#'env/parse-exported-vars out))))))

  (testing "drops entries whose value is not JSON-parseable"
    (let [out (str "declare -x GOOD=\"ok\"\n"
                   "declare -x BAD=not-quoted\n")]
      (is (= [["GOOD" "ok"]]
             (vec (#'env/parse-exported-vars out))))))

  (testing "ignores whitespace-only chunks"
    (is (= []
           (vec (#'env/parse-exported-vars "\n\n   \n"))))))

;; ---------------------------------------------------------------------------
;; parse-env-vars (private) — `NAME=value` format used by `--command env`
;; ---------------------------------------------------------------------------

(deftest parse-env-vars
  (testing "parses simple NAME=value lines"
    (is (= [["FOO" "bar"] ["BAZ" "qux"]]
           (vec (#'env/parse-env-vars "FOO=bar\nBAZ=qux")))))

  (testing "preserves `=` inside values (splits on first `=`)"
    (is (= [["JSON" "{\"a\":1}"]
            ["URL"  "https://x.test?a=1&b=2"]]
           (vec (#'env/parse-env-vars "JSON={\"a\":1}\nURL=https://x.test?a=1&b=2")))))

  (testing "drops lines without `=`"
    (is (= [["FOO" "bar"]]
           (vec (#'env/parse-env-vars "FOO=bar\nMALFORMED")))))

  (testing "ignores empty lines"
    (is (= [["FOO" "bar"]]
           (vec (#'env/parse-env-vars "\nFOO=bar\n\n"))))))

;; ---------------------------------------------------------------------------
;; list-to-args (private)
;; ---------------------------------------------------------------------------

(deftest list-to-args
  (testing "joins prefix with each element"
    (is (= "-p hello -p ripgrep"
           (#'env/list-to-args "-p" ["hello" "ripgrep"]))))

  (testing "empty list yields empty string"
    (is (= "" (#'env/list-to-args "-p" [])))))

;; ---------------------------------------------------------------------------
;; flake-target (private)
;; ---------------------------------------------------------------------------

(deftest flake-target
  (are [shell expected] (= expected (#'env/flake-target "/r" shell))
    nil       "/r"
    ""        "/r"
    "default" "/r"
    "tests"   "/r#tests"))
