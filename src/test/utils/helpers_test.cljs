(ns utils.helpers-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [utils.helpers :as h]
            [ext.lang :refer [lang]]))

(deftest render-workspace
  (testing "substitutes ${workspaceFolder} placeholder"
    (is (= "/work/repo/shell.nix"
           (h/render-workspace "${workspaceFolder}/shell.nix" "/work/repo"))))

  (testing "substitutes legacy ${workspaceRoot} placeholder"
    (is (= "/work/repo/shell.nix"
           (h/render-workspace "${workspaceRoot}/shell.nix" "/work/repo"))))

  (testing "substitutes every occurrence (literal text replacement)"
    (is (= "WS/sub/WS/file"
           (h/render-workspace "${workspaceFolder}/sub/${workspaceFolder}/file" "WS"))
        "both occurrences of ${workspaceFolder} are replaced")
    (is (= "WS:WS"
           (h/render-workspace "${workspaceFolder}:${workspaceRoot}" "WS"))
        "both placeholder spellings are replaced"))

  (testing "leaves unrelated paths untouched"
    (is (= "/absolute/path.nix"
           (h/render-workspace "/absolute/path.nix" "/work/repo")))))

(deftest unrender-workspace
  (testing "replaces literal workspace root with placeholder"
    (is (= "${workspaceFolder}/shell.nix"
           (h/unrender-workspace "/work/repo/shell.nix" "/work/repo"))))

  (testing "no-op when path does not contain the root"
    (is (= "/elsewhere/shell.nix"
           (h/unrender-workspace "/elsewhere/shell.nix" "/work/repo"))))

  (testing "round-trip: render → unrender preserves shape"
    (let [root  "/work/repo"
          input "${workspaceFolder}/sub/file.nix"]
      (is (= input (h/unrender-workspace (h/render-workspace input root) root))))))

(deftest render-env-status
  (testing "uses last path segment as env name"
    (is (= (-> lang :label :env-selected (.replace "%ENV_NAME%" "shell.nix"))
           (h/render-env-status lang "/work/repo/shell.nix"))))

  (testing "uses flake.nix basename"
    (is (= (-> lang :label :env-selected (.replace "%ENV_NAME%" "flake.nix"))
           (h/render-env-status lang "/work/repo/flake.nix"))))

  (testing "falls back to 'custom' when env-path is nil"
    (let [expected (-> lang :label :env-selected
                       (.replace "%ENV_NAME%" (-> lang :label :env-custom)))]
      (is (= expected (h/render-env-status lang nil))))))
