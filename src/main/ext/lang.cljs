(ns ext.lang)

(def ^:private lang-eng
  {:notification {:env-restored  "Original vscode environment will apply after reload"
                  :env-applied   "Environment successfully prepared and will be using after reload"
                  :env-available "The nix environment is available in your workspace"
                  :support       "Hi folks, My name is Roman Valihura. I'm the author of this extension. I'm Ukrainian. I was born in Ukraine. I'm living here at the moment.

As you all know Russia invaded my country. Russia has already killed thousands of civilians and continues the war and terror in Ukraine. I have the luck that my region is pretty far from the frontline. But even here, I'm living in the air-alarm reality. The reality where you should wake up in the middle of the night and go into the shelter. Because a rocket flies over your region.

Like a lot of Ukrainians now I became a volunteer in this hard time for my country.
We with a team producing Individual First Aid Kits for the Ukrainian army.

If you have a wish and ability to support the activity, you can make a donation on our website https://aidkit.shop
Thank you for your attention!"}
   :label        {:env-loading               "$(loading~spin) Applying environment..."
                  :env-selected              "$(beaker) Environment: %ENV_NAME%"
                  :env-need-reload           "$(beaker) Need reload"
                  :select-config-placeholder "Select environment config"
                  :disabled-nix              "Disable Nix environment"
                  :reload                    "Reload"
                  :select-env                "Select"
                  :dismiss                   "Dismiss"
                  :support                   "Donate IFAK"}})

(def lang lang-eng)
