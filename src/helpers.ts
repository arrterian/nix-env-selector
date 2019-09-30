export const getShellCmd = (internalCommand: "env") => (path: string) =>
  `nix-shell ${path} --run ${internalCommand}`;

export const toUndefined = (..._: any) => undefined;