# Change Log

All notable changes to the extension will be documented in this file.

## [1.2.0]

- Replace `sendText`/`onDidOpenTerminal` terminal patching with VS Code's `environmentVariableCollection` API — nix env vars are now injected into all terminal types including task runners, AI agent tools, and test frameworks

- Set `environmentVariableCollection` as persistent so the last known environment is restored at next startup before any extension code runs, covering `runOn: folderOpen` tasks

- Add `Reload Window` button to the post-apply notification so the environment can be activated without opening the command palette

- Rename `Hit environment` command to `Refresh environment`

- Add structured logger with ISO-8601 timestamps and severity levels (`DEBUG`, `INFO`, `WARN`, `ERROR`)

- Add `nixEnvSelector.logLevel` config setting (`debug` | `info` | `warn` | `error`, default `info`) — level changes apply live without a window reload

- Log raw stdout/stderr from nix commands at `DEBUG` level and full error stack traces at `ERROR` level

- Fix notification "Select" button doing nothing when `default.nix` is detected

## [1.0.0]

- Rewrite codebase with ClojureScript from scratch

- Add notification that proposes to use nix config, if the last one is available in the workspace [[ISSUE-28](https://github.com/arrterian/nix-env-selector/issues/28)]

- Add config parameter `packages` as an alternative to nix-file configuration [[ISSUE-12](https://github.com/arrterian/nix-env-selector/issues/12)]

- Add ability to pass custom arguments to nix-shell through `args` config parameter [[ISSUE-27](https://github.com/arrterian/nix-env-selector/issues/27)]

- Add ability to pass custom arguments to nix-shell through `args` config parameter [[ISSUE-27](https://github.com/arrterian/nix-env-selector/issues/27)]

- Add ability to pass the custom path to `nix-shell` utility through config's `nixShellConfig` parameter [[ISSUE-38](https://github.com/arrterian/nix-env-selector/issues/38)]

- Fix bug with misbehaving when spaces were presented in nix-config path [[ISSUE-41](https://github.com/arrterian/nix-env-selector/issues/41)]

## [0.1.3]

- Reword README
- Fix typos in the descriptions of config settings

## [0.1.2]

- Update `mocha` to `7.1.1`

## [0.1.1]

- The extension tested under Windows platform. (Thank you [Rasmus Eskola](https://github.com/FruitieX))

## [0.1.0]

- Add ability use nix-shell with custom attribute parameter (David Turnbull)

## [Experimental Release]

- Initial release
