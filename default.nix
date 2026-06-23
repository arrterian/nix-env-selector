{ pkgs ? import (fetchTarball "https://github.com/NixOS/nixpkgs/archive/refs/tags/25.11.tar.gz") {} }:

let
  src = pkgs.lib.cleanSourceWith {
    src = ./.;
    # Allow-list: only include files needed for the build and vsix packaging.
    filter = path: type:
      let
        baseName = baseNameOf (toString path);
        relPath = pkgs.lib.removePrefix (toString ./. + "/") (toString path);
        allowedDirs = [
          "src/"
          "resources/"
        ];
        # Top-level files to include (matched by full `relPath` to avoid matching
        # identically-named files inside `node_modules/` etc.).
        allowedFiles = [
          # Build inputs
          "shadow-cljs.edn"
          "package.json"
          "package-lock.json"
          # vsix packaging metadata
          ".vscodeignore"
          "CHANGELOG.md"
          "README.md"
          "LICENSE"
        ];
        dirAllowed = builtins.any (prefix:
          pkgs.lib.hasPrefix prefix relPath # inside an allowed dir
          ||
          pkgs.lib.hasPrefix relPath prefix # or parent that must be traversed to reach it
        ) allowedDirs;
      in
        if type == "directory" then dirAllowed
        else
          builtins.elem relPath allowedFiles
          || builtins.any (prefix: pkgs.lib.hasPrefix prefix relPath) allowedDirs;
  };

  # shadow-cljs runs on the JVM; nodejs is needed for npm and the ClojureScript
  # compiler's JS output stage.
  buildTools = with pkgs; [
    nodejs_22
    jdk17
  ];

  # shadow-cljs needs Maven/Clojure dependencies at compile time.
  # This fixed-output derivation fetches them with network access.
  mavenDeps = pkgs.stdenv.mkDerivation {
    name = "nix-env-selector-maven-deps";
    inherit src;

    nativeBuildInputs = buildTools ++ [
      pkgs.cacert
    ];

    buildPhase = ''
      # npm/shadow-cljs write caches to $HOME
      export HOME=$TMPDIR
      export SSL_CERT_FILE=${pkgs.cacert}/etc/ssl/certs/ca-bundle.crt
      npm ci
      patchShebangs node_modules
      # Run shadow-cljs classpath to trigger Maven dependency resolution
      npx shadow-cljs classpath
    '';

    installPhase = ''
      cp -r $HOME/.m2 $out
    '';

    outputHashAlgo = "sha256";
    outputHashMode = "recursive";
    outputHash = "sha256-m/chfPXSVmH62n3bQNef5RVfrzGlxe+gbhaaZhxXaGU=";

    impureEnvVars = pkgs.lib.fetchers.proxyImpureEnvVars;
  };
in
pkgs.buildNpmPackage {
  pname = "nix-env-selector";
  version = "1.3.1";

  inherit src;

  npmDepsHash = "sha256-JQ7CDgPsvR+TplGHy7WlmAlHR/NhRRrZ0Kiuc0WpbBw=";

  nativeBuildInputs = buildTools ++ (with pkgs; [
    # node-gyp (used by transitive dep `keytar` from `@vscode/vsce`) needs
    # python3 and pkg-config at build time.
    pkg-config
    python3
  ]);

  buildInputs = with pkgs; [
    # `keytar` (transitive dep of `@vscode/vsce`) compiles a native addon via
    # node-gyp at npm install time; it needs libsecret headers and .pc file.
    libsecret
  ];

  preBuild = ''
    # shadow-cljs writes its classpath cache to $HOME/.m2
    export HOME=$TMPDIR
    mkdir -p $HOME/.m2
    cp -r ${mavenDeps}/* $HOME/.m2/
  '';

  buildPhase = ''
    runHook preBuild

    npm run compile
    npx vsce package --allow-star-activation --out nix-env-selector.vsix

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out
    cp nix-env-selector.vsix $out/

    runHook postInstall
  '';

  # Skip the default npm build that buildNpmPackage runs; we do it ourselves.
  dontNpmBuild = true;
}
