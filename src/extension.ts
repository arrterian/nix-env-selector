import * as vscode from "vscode";
import * as Action from "./actions";
import { ap } from "fp-ts/lib/Array";
import { Command } from "./constants";
import {
  mapNullable,
  fromNullable,
  some,
  none,
  getOrElse,
  Option,
  fold
} from "fp-ts/lib/Option";
import { getShellCmd, toUndefined } from "./helpers";
import Future, { parallel, FutureInstance } from "fluture";

const SELECTED_ENV_CONFIG_KEY = "nixEnvSelector.nixShellConfig";

const selectEnvCommandHandler = (
  workspaceRoot: string,
  config: vscode.WorkspaceConfiguration
) => () =>
  Action.getNixConfigList(workspaceRoot)
    .chain(Action.selectConfigFile(workspaceRoot))
    .map(mapNullable(({ id }) => ap([id])))
    .map(
      mapNullable(apNixConfigPath =>
        parallel(
          Infinity,
          apNixConfigPath([
            Action.updateEditorConfig(
              SELECTED_ENV_CONFIG_KEY,
              config,
              workspaceRoot
            ),
            Action.applyEnvByNixConfPath(getShellCmd("env")),
            Action.askReload
          ])
        ).map(some)
      )
    )
    .chain(
      getOrElse<FutureInstance<Error, Option<boolean[]>>>(() => Future.of(none))
    )
    .fork(
      err => vscode.window.showErrorMessage(err.message),
      fold(
        toUndefined,
        ([_1, _2, isReloadConfirmed]) =>
          isReloadConfirmed &&
          vscode.commands.executeCommand(Command.RELOAD_WINDOW)
      )
    );

export function activate(context: vscode.ExtensionContext) {
  const workspaceRoot = vscode.workspace.rootPath;

  // ignore activation when workspace is not selected
  if (!workspaceRoot) {
    return;
  }

  const config = vscode.workspace.getConfiguration();
  const maybeNixEnvConfig = fromNullable(
    config.get<string>(SELECTED_ENV_CONFIG_KEY)
  );
  
  const activateOrShowDialogWithConfig = Action.activateOrShowDialog(
    workspaceRoot
  );

  context.subscriptions.push(
    vscode.commands.registerCommand(
      Command.SELECT_ENV_DIALOG,
      selectEnvCommandHandler(workspaceRoot, config)
    )
  );

  return activateOrShowDialogWithConfig(maybeNixEnvConfig).fork(
    err => vscode.window.showErrorMessage(err.message),
    toUndefined
  );
}

// this method is called when your extension is deactivated
export function deactivate() {}
