with (import (fetchTarball "https://github.com/NixOS/nixpkgs/archive/refs/tags/26.05.tar.gz") {});

let
  nixPackages = [
    nodejs_22
    jdk17
    cljfmt
  ];
in
pkgs.stdenv.mkDerivation {
  name = "vscode-env-selector";
  buildInputs = nixPackages;
}
