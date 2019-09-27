# Nix Environment Selector (🧪 *Experimental Release*)

The extension allows you switch environment for Visual Studio Code and extensions based on `.nix` config files.

## Motivation

Nix package manager provide an convenient solution for creating isolated environments with specific configuration of package list. This way is working as well from command line, but not prepared for using with IDE. For example if you have an language server extension that require compiler installed inside isolated environment is no simple way to link 'em with each other. The solution allows you manage environment for project based on Visual Studio Code workspace.

## Getting started

* First of all you should install [Nix package manager](https://nixos.org/nix/).
* [Install the extension](https://marketplace.visualstudio.com/items?itemName=arrterian.nix-env-selector)
* Prepare your nix env config with extension `.nix` and place in your project workspace.

> Actually you can set non workspace located file but in this case you should set full path into parameter `"nixEnvSelector.nixShellConfig"` in your config.

* Type `"Select environment"` in command pallet

## Haskell Project running example

We have the following config for our environment (`shell.nix`):

```nix
{ nixpkgs ? import <nixpkgs> {} }:
let
  inherit (nixpkgs) pkgs;
  inherit (pkgs) haskellPackages;

  haskellDeps = ps: with ps; [
    base
    lens
    mtl
    random
  ];

  ghc = pkgs.haskell.packages.ghc864.ghcWithPackages haskellDeps;

  nixPackages = [
    ghc
    pkgs.gdb
    haskellPackages.cabal-install
  ];
in
pkgs.stdenv.mkDerivation {
  name = "snadbox-haskell-workspace";
  buildInputs = nixPackages;
}
```

Haskell IDE Engine installed in user environment and require GHC compiler for work correctly. We want avoid to install the compiler globally such as different project can require different version of GHC. We will install the GHC compiler inside nix store (described in `shell.nix` above).

Now lets try to open our project in Visual Studio Code.

![Without Env Demo](resources/without-env-demo.gif)

You can see that IDE can't find a compiler. Lets enable the `shell.nix` env.

![With Env Demo](resources/with-env-demo.gif)

Bingo 🎉🎉🎉. Everything is fine now 😈

## Supported Platforms

The extension have been tested on MacOS. Should works on linux platform as well, but not tested yet. Feel free to create an issue if you found a problem.

## License

[MIT](LICENSE)
