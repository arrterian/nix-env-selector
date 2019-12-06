import * as vscode from "vscode";
import * as Action from "./actions";
import { ap } from "fp-ts/lib/Array";
import { flow } from "fp-ts/lib/function";
import { pipe } from "fp-ts/lib/pipeable";
import { Command, Label, ConfigPath } from "./constants";
import {
  flatten,
  fromNullable,
  some,
  none,
  getOrElse,
  Option,
  fold,
  mapNullable
} from "fp-ts/lib/Option";
import { getShellCmd, toUndefined } from "./helpers";
import Future, { FutureInstance, map, parallel } from "fluture";
import { showStatus, showStatusWithEnv, hideStatus } from "./status-bar";

type ErrorHandler = (err: Error) => any;

const handleError: ErrorHandler = flow(
  hideStatus,
  err => vscode.window.showErrorMessage(err.message),
);

const selectEnvCommandHandler = (
  workspaceRoot: string,
  config: vscode.WorkspaceConfiguration
) => () => {
    const nixAttr = fromNullable(
      config.get<string>(ConfigPath.SELECTED_ATTR_CONFIG_KEY)
    );

    Action.getNixConfigList(workspaceRoot)
      .chain(Action.selectConfigFile(workspaceRoot))
      .map(
        mapNullable(
          flow(
            showStatus(Label.LOADING_ENV, some(Command.SELECT_ENV_DIALOG)),
            ({ id }) => ap([id]),
            apNixConfigPath =>
              parallel(
                1,
                apNixConfigPath([
                  Action.updateEditorConfig(
                    ConfigPath.SELECTED_ENV_CONFIG_KEY,
                    config,
                    workspaceRoot
                  ),
                  flow(
                    Action.applyEnvByNixConfPath(getShellCmd("env", nixAttr)),
                    map(showStatus(Label.SELECTED_ENV_NEED_RELOAD, none))
                  ),
                  Action.askReload
                ])
              ).map(some)
          )
        )
      )
      .chain(
        getOrElse<FutureInstance<Error, Option<boolean[]>>>(() => Future.of(none))
      )
      .fork(
        handleError,
        mapNullable(
          ([_1, _2, isReloadConfirmed]) =>
            isReloadConfirmed &&
            vscode.commands.executeCommand(Command.RELOAD_WINDOW)
        )
      );
    };

export function activate(context: vscode.ExtensionContext) {
  const workspaceRoot = vscode.workspace.rootPath;

  // ignore activation when workspace is not selected
  if (!workspaceRoot) {
    return;
  }

  const config = vscode.workspace.getConfiguration();
  const maybeNixEnvConfig = fromNullable(
    config.get<string>(ConfigPath.SELECTED_ENV_CONFIG_KEY)
  );

  const maybeNixAttrConfig = fromNullable(
    config.get<string>(ConfigPath.SELECTED_ATTR_CONFIG_KEY)
  );

  const activateOrShowDialogWithConfig = Action.activateOrShowDialog(
    workspaceRoot,
    maybeNixAttrConfig
  );

  context.subscriptions.push(
    vscode.commands.registerCommand(
      Command.SELECT_ENV_DIALOG,
      selectEnvCommandHandler(workspaceRoot, config)
    )
  );

  return activateOrShowDialogWithConfig(maybeNixEnvConfig)
    .map(mapNullable(() => maybeNixEnvConfig))
    .map(flatten)
    .map(
      fold(
        () => hideStatus("unknown"),
        envConfigPath => envConfigPath.split("/").reverse()[0]
      )
    )
    .fork(
      handleError,
      showStatusWithEnv(Label.SELECTED_ENV, some(Command.SELECT_ENV_DIALOG))
    );
}

// this method is called when your extension is deactivated
export function deactivate() { }
