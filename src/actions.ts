import * as vscode from "vscode";
import { node, of, FutureInstance, reject } from "fluture";
import { readdir } from "fs";
import { applyEnv, parseEnv } from "./env";
import { Label, Notification, Command, NOT_MODIFIED_ENV } from "./constants";
import { pipe } from "fp-ts/lib/pipeable";
import { execSync, exec } from "child_process";
import { fold, fromNullable, Option, mapNullable, none, some } from "fp-ts/lib/Option";
import { getShellCmd } from "./helpers";
import { flow } from "fp-ts/lib/function";

const ENV_NAME_LABEL_PLACEHOLDER = "%ENV_NAME%";

export const getNixConfigList = (dirPath: string) =>
  node<NodeJS.ErrnoException, string[]>(done => {
    readdir(dirPath, done);
  }).map(files => files.filter(fileName => /.*\.nix/i.test(fileName)));

const showQuickPick = <T extends vscode.QuickPickItem>(
  options: vscode.QuickPickOptions,
  items: T[]
) =>
  node<Error, T | undefined>(done =>
    vscode.window
      .showQuickPick(items, options)
      .then(result => done(null, result), err => done(err))
  ).map(fromNullable);

export const askReload = (nixEnvConfig: string) =>
  node<Error, boolean>(done =>
    vscode.window
      .showInformationMessage(
        nixEnvConfig === NOT_MODIFIED_ENV
          ? Notification.ENV_RESTORED
          : Notification.ENV_APPLIED.replace(
            ENV_NAME_LABEL_PLACEHOLDER,
            nixEnvConfig
          ),
        Label.RELOAD
      )
      .then(result => done(null, !!result), err => done(err))
  );

export const selectConfigFile = (workspaceRoot: string) => (
  configFiles: string[]
) =>
  showQuickPick(
    {
      placeHolder: Label.SELECT_CONFIG_PLACEHOLDER
    },
    [
      {
        id: NOT_MODIFIED_ENV,
        label: Label.NOT_MODIFIED_ENV
      },
      ...configFiles.map(fileName => ({
        id: `${workspaceRoot}/${fileName}`,
        label: fileName
      }))
    ]
  );

export const applyEnvByNixConfPath = (
  makeCmd: (path: string) => Option<string>
) => (nixConfigPath: string) =>
    of<Error, Option<string>>(makeCmd(nixConfigPath))
      .map(
        mapNullable(cmd =>
          node<Error, string>(done => exec(cmd, done))
            .map(parseEnv)
            .map(applyEnv)
        )
      )
      .chain(fold(() => of(false), id => id));

export const updateEditorConfig = (
  configKey: string,
  config: vscode.WorkspaceConfiguration,
  workspaceRoot: string
) => (nixConfigPath: string) =>
    node<Error, boolean>(done =>
      config
        .update(
          configKey,
          nixConfigPath.replace(workspaceRoot, "${workspaceRoot}"),
          vscode.ConfigurationTarget.Workspace
        )
        .then(_ => done(null, true), err => done(err))
    );

export const activateOrShowDialog = (workspaceRoot: string, nixAttr: Option<string>) =>
  fold<string, FutureInstance<Error, Option<boolean>>>(
    () => {
      return getNixConfigList(workspaceRoot)
        .map(dirs => dirs.length)
        .chain(totalEnvCount =>
          totalEnvCount > 0
            ? node<Error, boolean>(done =>
              vscode.commands
                .executeCommand(Command.SELECT_ENV_DIALOG)
                .then(_ => done(null, true), err => done(err))
            )
              .map(some)
            : of(none)
        );
    },
    nixConfigPathTemplate => {
      if (nixConfigPathTemplate === NOT_MODIFIED_ENV) {
        return of(none);
      }

      const nixConfigPath = nixConfigPathTemplate.replace(
        "${workspaceRoot}",
        workspaceRoot
      );

      // NOTE: all sync commands could throw exception
      try {
        return of(
          pipe(
            nixConfigPath,
            getShellCmd("env", nixAttr),
            mapNullable(
              flow(
                // HACK: sync operation using for block tread and prevent loading other
                // extension before environment will be applied
                execSync,
                parseEnv,
                applyEnv
              )
            ),
          )
        );
      } catch (err) {
        return reject(err);
      }
    }
  );
