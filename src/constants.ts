export const Label = {
  LOADING_ENV: "$(beaker~spin) Applying environment...",
  SELECTED_ENV: "$(beaker) Environment: %ENV_NAME%",
  SELECTED_ENV_NEED_RELOAD: "$(beaker) Need reload",
  SELECT_CONFIG_PLACEHOLDER: "Select environment config",
  RELOAD: "Reload",
  NOT_MODIFIED_ENV: "Original user environment"
};

export const NOT_MODIFIED_ENV = "NOT_MODIFIED_ENV";

export const Notification = {
  ENV_RESTORED: "Original vscode environment will apply after reload",
  ENV_APPLIED:
    "Environment %ENV_NAME% successfully prepared and will be using after reload."
};

export const enum Command {
  SELECT_ENV_DIALOG = "extension.selectEnv",
  RELOAD_WINDOW = "workbench.action.reloadWindow",
}

export const enum ConfigPath {
  SELECTED_ENV_CONFIG_KEY = "nixEnvSelector.nixShellConfig",
  SELECTED_ATTR_CONFIG_KEY = "nixEnvSelector.nixShellConfigAttr",
}
