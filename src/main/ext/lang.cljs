(ns ext.lang)

(def ^:private lang-eng
  {:notification {:env-restored  "Original vscode environment will apply after reload"
                  :env-applied   "Environment successfully prepared and will be using after reload"
                  :env-available "The nix environment is available in your workspace"
                  :support       "Do you like the extension? You can support the author if you wish"}
   :label        {:env-loading               "$(loading~spin) Applying environment..."
                  :env-selected              "$(beaker) Environment: %ENV_NAME%"
                  :env-need-reload           "$(beaker) Need reload"
                  :select-config-placeholder "Select environment config"
                  :disabled-nix              "Disable Nix environment"
                  :reload                    "Reload"
                  :select-env                "Select"
                  :dismiss                   "Dismiss"
                  :support                   "Support"}})

(def lang lang-eng)
