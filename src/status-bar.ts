import * as vscode from 'vscode';
import { Command } from './constants';
import { Option, toUndefined } from "fp-ts/lib/Option";

const ENV_NAME_LABEL_PLACEHOLDER = "%ENV_NAME%";

const status = vscode.window.createStatusBarItem(
  vscode.StatusBarAlignment.Left,
  100
);

export const showStatus = (text: string, maybeCommand: Option<Command>) => <T>(bypass: T) => {
  status.text = text;
  status.command = toUndefined(maybeCommand);
  status.show();
  return bypass;
};

export const showStatusWithEnv = (text: string, maybeCommand: Option<Command>) => (envName: string) => {
  status.text = text.replace(ENV_NAME_LABEL_PLACEHOLDER, envName);
  status.command = toUndefined(maybeCommand);
  status.show();
  return envName;
};

export const hideStatus = <T>(bypass: T) => {
  status.hide();
  return bypass;
};