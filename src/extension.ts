import * as vscode from "vscode";
import { readdir } from "fs";
import { exec as execCb, execSync } from "child_process";
import { applyEnv, parseEnv } from "./env";
import { Label, Notification, Command } from "./constants";

const SELECTED_ENV_CONFIG_KEY = "nixEnvSelector.nixShellConfig";
const NOT_MODIFIED_ENV = "NOT_MODIFIED_ENV";
const ENV_NAME_LABEL_PLACEHOLDER = "%ENV_NAME%";
const DEFAULT_CONFIG_NAME = "default.nix";

const getEnvShellCmd = (path: string) => `nix-shell ${path} --run env`;

const getNixEnvList = (dirPath: string) =>
  new Promise<string[]>((resolve, reject) => {
    readdir(dirPath, (err, dirs) => {
      if (err) {
        return reject(err);
      }

      return resolve(dirs.filter(dirName => /.*\.nix/i.test(dirName)));
    });
  });

export function activate(context: vscode.ExtensionContext) {
  // TODO: make proper subscription event or when restriction
  let nixConfigPath: string;
  if (!vscode.workspace.rootPath) {
    return;
  }

  const selectEnvCmd = vscode.commands.registerCommand(
    Command.SELECT_ENV_DIALOG,
    () => {
      getNixEnvList(appRoot)
        .then(dirs => {
          return vscode.window.showQuickPick(
            [
              {
                id: NOT_MODIFIED_ENV,
                label: Label.NOT_MODIFIED_ENV
              },
              ...dirs.map(fileName => ({
                id: `${appRoot}/${fileName}`,
                label: fileName
              }))
            ],
            {
              placeHolder: Label.SELECT_CONFIG_PLACEHOLDER
            }
          );
        })
        .then(envFile => {
          if (!envFile || envFile.id === nixConfigPath) {
            return undefined;
          }

          return vscode.commands.executeCommand(
            Command.SELECT_ENV_BY_PATH,
            envFile.id
          );
        });
    }
  );

  vscode.commands.registerCommand(
    Command.SELECT_ENV_BY_PATH,
    nixSelectedEnvFilePath => {
      config.update(
        SELECTED_ENV_CONFIG_KEY,
        nixSelectedEnvFilePath.replace(appRoot, "${workspaceRoot}"),
        vscode.ConfigurationTarget.Workspace
      );

      if (nixSelectedEnvFilePath === NOT_MODIFIED_ENV) {
        status.hide();
        return vscode.window
          .showInformationMessage(
            nixSelectedEnvFilePath === NOT_MODIFIED_ENV
              ? Notification.ENV_RESTORED
              : Notification.ENV_APPLIED.replace(
                  ENV_NAME_LABEL_PLACEHOLDER,
                  nixSelectedEnvFilePath
                ),
            Label.RELOAD
          )
          .then(selectedAction => {
            if (selectedAction === Label.RELOAD) {
              vscode.commands.executeCommand("workbench.action.reloadWindow");
            }
          });
      }

      status.text = Label.LOADING_ENV;
      status.show();

      execCb(getEnvShellCmd(nixSelectedEnvFilePath), err => {
        if (err) {
          return vscode.window.showErrorMessage(err.message);
        }

        status.text = Label.SELECTED_ENV_NEED_RELOAD;
        vscode.window
          .showInformationMessage(
            nixSelectedEnvFilePath === NOT_MODIFIED_ENV
              ? Notification.ENV_RESTORED
              : Notification.ENV_APPLIED.replace(
                  ENV_NAME_LABEL_PLACEHOLDER,
                  nixSelectedEnvFilePath
                ),
            Label.RELOAD
          )
          .then(selectedAction => {
            if (selectedAction === Label.RELOAD) {
              vscode.commands.executeCommand("workbench.action.reloadWindow");
            }
          });
      });
    }
  );

  context.subscriptions.push(selectEnvCmd);

  const status = vscode.window.createStatusBarItem(
    vscode.StatusBarAlignment.Left,
    100
  );

  const config = vscode.workspace.getConfiguration();
  const appRoot = vscode.workspace.rootPath;

  const nixConfigPathTemplate = config.get<string>(SELECTED_ENV_CONFIG_KEY);

  if (!nixConfigPathTemplate) {
    return getNixEnvList(appRoot)
      .then(dirs => [dirs.length, dirs.find(fileName => fileName === DEFAULT_CONFIG_NAME)])
      .then(([totalEnvCount, defaultEnvFile]) =>
        defaultEnvFile
          ? vscode.commands.executeCommand(Command.SELECT_ENV_BY_PATH, `${appRoot}/${DEFAULT_CONFIG_NAME}`)
          : !!totalEnvCount && vscode.commands.executeCommand(Command.SELECT_ENV_DIALOG)
      );
  } else {
    if (nixConfigPathTemplate !== NOT_MODIFIED_ENV) {
      nixConfigPath = nixConfigPathTemplate.replace(
        "${workspaceRoot}",
        appRoot
      );
      const env = parseEnv(execSync(getEnvShellCmd(nixConfigPath)));

      applyEnv(env);

      status.text = Label.SELECTED_ENV.replace(
        ENV_NAME_LABEL_PLACEHOLDER,
        nixConfigPath.split("/").reverse()[0]
      );
      status.command = Command.SELECT_ENV_DIALOG;
      status.show();
    }
  }
}

// this method is called when your extension is deactivated
export function deactivate() {}
