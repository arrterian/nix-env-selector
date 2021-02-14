{ nixpkgs ? import <nixpkgs> {} }:
let
  inherit (nixpkgs) pkgs;

  nixPackages = [
    pkgs.nodejs-14_x
    pkgs.jdk11
  ];
in
pkgs.stdenv.mkDerivation {
  name = "vscode-env-selector";
  buildInputs = nixPackages;
  postInstall =
    ''
      yarn install
    '';
}