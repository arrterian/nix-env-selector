# 🇺🇦 Support Ukraine 🇺🇦
Hi folks,
My name is Roman Valihura. I'm the author of this extension. I'm Ukrainian.
I was born in Ukraine. I'm living here at the moment.

As you all know Russia invaded my country.
Russia has already killed thousands of civilians and continues the war and terror in Ukraine.

If you have a wish and ability to support Ukraine, please consider donating to the **Come Back Alive** foundation — one of the largest and most transparent Ukrainian charitable funds supporting the Armed Forces of Ukraine.

[Donate to Come Back Alive](https://savelife.in.ua/en/donate-en/#donate-army-card-once)

Thank you for your support!


# Nix Environment Selector

<p align="center">
  <img
    width="200"
    height="200"
    src="https://raw.githubusercontent.com/arrterian/nix-env-selector/master/resources/icon.png"
    alt="nix-env-selector-logo"/>
</p>

<p align="center">
  Extension that lets you use environments declared in <kbd>.nix</kbd> files in Visual Studio Code.
</p>

## Motivation

Nix package manager provides a way of creating isolated
environments with a specific configuration of packages.
These environments are usually activated in the terminal
and are not convenient to use within an IDE.

One option is to run `nix-shell` on the command line and then
launch `code` within the activated shell.
However, this process can quickly become tedious.
`Nix Environment Selector` provides an alternative: can automatically apply the environment.

## Getting started

-   Install [Nix package manager](https://nixos.org/nix/).
-   Restart VS Code (to make sure that `nix-shell` is in the PATH)
-   [Install the extension](https://marketplace.visualstudio.com/items?itemName=arrterian.nix-env-selector).
-   Create the Nix environment config (like `default.nix` or `shell.nix` or `flake.nix`) in
    the root of your project's workspace.
-   Open Command Palette (<kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>P</kbd>)
    and run `Nix-Env: Select Environment` command.
-   Choose the Nix environment you'd like to apply.
-   Wait for the environment to build.
-   Restart VS Code to apply the built environment.

## Example

### Haskell project

To run a Haskell application you need to have **GHC** (Haskell compiler) installed.
With Nix package manager we can create an isolated environment containing only
the GHC version and the dependencies that the project needs without
polluting the user's environment.

Environment configuration in `shell.nix`:

```nix
{ pkgs ? import <nixpkgs> { } }:
with pkgs;

let
  haskellDeps = ps: with ps; [
    base
    lens
    mtl
    random
  ];
  haskellEnv = haskell.packages.ghc865.ghcWithPackages haskellDeps;
in mkShell {
  buildInputs = [
    haskellEnv
    haskellPackages.cabal-install
    gdb
  ];
}
```

Now let's try to open our project in Visual Studio Code.

![Without Env Demo](resources/without-env-demo.gif)

As you can see VS Code can't find the GHC compiler. Let's apply
the environment declared in `shell.nix`.

![With Env Demo](resources/with-env-demo.gif)

Bingo 🎉🎉🎉. Everything is working now 😈

## Configuration

You can configure the extension in `.vscode/settings.json`
file (located in the root of the workspace). Here are the configuration settings:

| Setting                        | Default | Description                                                                  |
| ------------------------------ | ------- | ---------------------------------------------------------------------------- |
| `nixEnvSelector.nixFile`       | null    | Path to the Nix config file                                                  |
| `nixEnvSelector.packages`      | []      | List of packages passed as `-p` args to nix-shell                           |
| `nixEnvSelector.args`          | null    | Additional args for nix-shell. EX: `-A <something> --pure`                  |
| `nixEnvSelector.nixShellPath`  | null    | Custom path to the nix-shell executable                                      |
| `nixEnvSelector.useFlakes`     | false   | Enable support for `flake.nix`                                               |
| `nixEnvSelector.suggestion`    | true    | Show a proposal to select an environment when a `.nix` file is detected      |
| `nixEnvSelector.logLevel`      | `info`  | Output channel verbosity: `debug` \| `info` \| `warn` \| `error`            |


## Supported Platforms

-   MacOS
-   Linux
-   Windows (with `Remote - WSL` extension)

## Support

If you like the extension and want to support author, click the button bellow.

<a
  href="https://secure.wayforpay.com/payment/selector"
  alt="donate">
  <img
      width="170"
      height="35"
      src="https://raw.githubusercontent.com/arrterian/nix-env-selector/master/resources/donate-wfp.png"
      alt="donate"/>
</a>

## License

[MIT](LICENSE)
