(ns ext.lang)

(def ^:private lang-eng
  {:notification {:env-restored  "Original vscode environment will apply after reload"
                  :env-applied   "Applied. Reload your window using command palette"
                  :env-available "The nix environment is available in your workspace"}
   :label        {:env-loading               "$(loading~spin) Applying environment..."
                  :env-selected              "$(beaker) Environment: %ENV_NAME%"
                  :env-need-reload           "$(beaker) Need reload"
                  :select-config-placeholder "Select environment config"
                  :disabled-nix              "Disable Nix environment"
                  :reload                    "Reload"
                  :select-env                "Select"
                  :dismiss                   "Dismiss"}})

(def lang lang-eng)
