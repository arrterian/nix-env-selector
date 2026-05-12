# Change Log

All notable changes to the extension will be documented in this file.

## [1.3.0]

### Added

- Status bar item is now an interactive entry point: clicking it opens an actions popup (Sync / Select / Disable / Show output channel), and hovering shows a rich Markdown tooltip with the current environment summary (status, source, path, flake shell, args, custom shell binary, log level, workspace)

- Step-by-step env selection wizard invoked from the Select action (or `Nix-Env: Select environment`):
  - Step 1 — always asks "flake" vs "nix-shell" vs "Disable" (current type marked); if neither type exists in the workspace, an informative "No .nix or flake.nix files found" notice is shown above Disable
  - Step 2 — for flakes, lists devShells discovered via `nix flake show --json` (current shell marked); for nix-shell, lists `.nix` files (current file marked)
  - Re-running the wizard is non-destructive: picking the currently-active selection is a no-op (no re-apply, no reload prompt)

- Support for non-default flake devShells via the new `nixEnvSelector.flakeShell` setting; the active shell is passed through to `nix develop <dir>#<shell>` and surfaced in the tooltip

- Auto-detection of flake vs nix-shell at selection time: when the user picks a file in the wizard, `nixEnvSelector.useFlakes` is set automatically based on the filename (`flake.nix` → `true`, anything else → `false`). The saved setting remains the source of truth between selections, keeping the config backward-compatible

- New commands surfaced in the palette: `Nix-Env: Show environment actions`, `Nix-Env: Disable Nix environment`, `Nix-Env: Show output channel`

### Changed

- Status bar `$(beaker)` label and click target now route through the new actions popup
- Output channel logging is more uniform: per-action `Running action: …` is logged at invocation time (was previously logged at activation)

### Fixed

- `cwd` is now actually passed to `child_process.exec`/`execSync` for nix invocations (was being silently dropped by `clj->js`, so relative `import ./*.nix` paths could resolve incorrectly)
- `Nix-Env: Sync environment changes` now works for packages-only configs (previously was a no-op when only `nixEnvSelector.packages` was set)
- Status bar item and log output channel are now registered as `ctx.subscriptions` disposables so they're cleaned up on extension deactivation
- `parse-exported-vars` now logs a warning listing env vars dropped due to unparseable values (previously silent)
- `showInformationMessage` is invoked with proper `this` binding (the detached `apply` call was technically incorrect even though it happened to work)
- `get-nix-files` skips directories whose names end in `.nix` (only files are now listed in the picker)
- Missing `:env-custom` localization key that could render as empty text on the status bar

### Refactored

- Substantial cleanup pass on the ClojureScript codebase: `defn-` shorthand, idiomatic `re-find` / `run!`, `^js` type hints in interop sites, dead code removed (`donate-url`, `vscode.env` namespace, unused lang keys, stale `src/dev`/`src/test` source paths)
- `get-nix-env-sync` and `get-nix-env-async` share a common `prepare-invocation` preamble; config-rendering helper extracted; error tails consolidated into a single `handle-error`
- `actions.cljs` reorganised top-down by dependency (no more forward `declare`)
- Source files reformatted with `cljfmt` per the existing `.cljfmt.edn` rules

## [1.2.0]

- Inject nix env vars into all terminal types (interactive shells, task runners, AI agent tools, test frameworks) using VS Code's `environmentVariableCollection` API [[ISSUE-31](https://github.com/arrterian/nix-env-selector/issues/31)] [[ISSUE-55](https://github.com/arrterian/nix-env-selector/issues/55)]

- Persist the environment collection across restarts so the last known environment is restored before any extension code runs, covering `runOn: folderOpen` tasks [[ISSUE-55](https://github.com/arrterian/nix-env-selector/issues/55)]

- Add `Reload Window` button to the post-apply notification so the environment can be activated without opening the command palette

- Rename `Hit environment` command to `Sync environment changes`

- Add structured logger with ISO-8601 timestamps and severity levels (`DEBUG`, `INFO`, `WARN`, `ERROR`)

- Add `nixEnvSelector.logLevel` config setting (`debug` | `info` | `warn` | `error`, default `info`) — level changes apply live without a window reload

- Log raw stdout/stderr from nix commands at `DEBUG` level and full error stack traces at `ERROR` level

- Fix notification "Select" button doing nothing when `default.nix` is detected

- Improved error messages

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
