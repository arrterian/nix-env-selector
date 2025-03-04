with (import (fetchTarball "https://github.com/NixOS/nixpkgs/archive/refs/tags/24.05.tar.gz") {});

let
  nixPackages = [
    nodejs_22
    jdk17
  ];
in
pkgs.stdenv.mkDerivation {
  name = "vscode-env-selector";
  buildInputs = nixPackages;
}
