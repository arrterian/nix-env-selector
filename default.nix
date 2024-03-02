{ nixpkgs ? import <nixpkgs> {} }:
let
  inherit (nixpkgs) lib pkgs stdenv;

  nixPackages = [
    pkgs.nodejs
    pkgs.jdk11
    pkgs.vsce
  ] ++ lib.optional stdenv.isDarwin pkgs.darwin.apple_sdk.frameworks.Security;
  packageJson = (lib.importJSON ./package.json);
in
pkgs.stdenv.mkDerivation rec {
  pname = packageJson.name;
  version = packageJson.version;

  src = ./.;

  buildInputs = nixPackages;
  buildPhase = ''
    npm install
    npm run compile
    echo y | vsce package
  '';

  installPhase = ''
    mkdir -p $out/bin
    cp ${pname}-${version}.vsix $out/bin
  '';
}
