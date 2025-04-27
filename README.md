# 🇺🇦 Support Ukraine 🇺🇦
Hi folks,
My name is Roman Valihura. I'm the author of this extension. I'm Ukrainian.
I was born in Ukraine. I'm living here at the moment.

As you all know Russia invaded my country.
Russia has already killed thousands of civilians and continues the war and terror in Ukraine.
I have the luck that my region is pretty far from the frontline. But even here, I'm living in the air-alarm reality.
The reality where you should wake up in the middle of the night and go into the shelter. Because a rocket flies over your region.


Like a lot of Ukrainians now I became a volunteer in this hard time for my country.
We with a team producing Individual First Aid Kits for the Ukrainian army.
If you have a wish and ability to support the activity, you can make a donation on our website, which we made to collect funds for producing First Aid Kits.

More datails on the [aidkit.shop](https://aidkit.shop)

Thank you for your attention!


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

| Setting                       | Default | Description                                                   |
| ----------------------------- | ------- | ------------------------------------------------------------- |
| `nixEnvSelector.nixFile`      | null    | Path to the Nix config file                                   |
| `nixEnvSelector.packages`     | []      | List packages using as `-p` nix-shell args                    |
| `nixEnvSelector.args`         | null    | Custom args string for nix-shell. EX: `-A <something> --pure` |
| `nixEnvSelector.nixShellPath` | null    | Custom path for nix-shell executable                          |
| `nixEnvSelector.useFlakes` | false    | Enable support for `flake.nix`                          |


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
