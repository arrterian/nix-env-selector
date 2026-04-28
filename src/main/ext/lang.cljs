(ns ext.lang)

(def ^:private lang-eng
  {:notification {:env-restored  "Original vscode environment will apply after reload"
                  :env-applied   "Applied. Reload your window to activate the environment"
                  :env-available "The nix environment is available in your workspace"
                  :env-error     "Nix Env Selector: Failed to apply environment. See output channel for details."
                  :no-dev-shells "Nix Env Selector: No devShells found in flake."}
   :label        {:env-loading               "$(loading~spin) Applying environment..."
                  :env-selected              "$(beaker) Environment: %ENV_NAME%"
                  :env-need-reload           "$(beaker) Need reload"
                  :select-config-placeholder "Select environment config"
                  :select-dev-shell-placeholder "Select flake devShell"
                  :disabled-nix              "Disable Nix environment"
                  :reload                    "Reload"
                  :select-env                "Select"
                  :dismiss                   "Dismiss"
                  :show-logs                 "Show Error"}})

(def lang lang-eng)
