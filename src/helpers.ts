import { isSome, none, fromPredicate, mapNullable, getOrElse, Option } from "fp-ts/lib/Option";
import { pipe } from "fp-ts/lib/pipeable";
import { NOT_MODIFIED_ENV } from "./constants";

export const getShellCmd = (internalCommand: "env", attr: Option<string>) => (path: string) => {
  const toOption = fromPredicate<string>(
    configPath => configPath !== NOT_MODIFIED_ENV
  );

  const attrArg = pipe(
    attr,
    mapNullable(attr => `-A ${attr}`),
    getOrElse(() => '')
  );

  return pipe(
    path,
    toOption,
    mapNullable(path => `nix-shell ${attrArg} ${path} --run ${internalCommand}`)
  );
};

export const toUndefined = (..._: any) => undefined;
