import { fromPredicate, mapNullable } from "fp-ts/lib/Option";
import { pipe } from "fp-ts/lib/pipeable";
import { NOT_MODIFIED_ENV } from "./constants";

export const getShellCmd = (internalCommand: "env") => (path: string) => {
  const toOption = fromPredicate<string>(
    configPath => configPath !== NOT_MODIFIED_ENV
  );
  return pipe(
    path,
    toOption,
    mapNullable(path => `nix-shell ${path} --run ${internalCommand}`)
  );
};

export const toUndefined = (..._: any) => undefined;
