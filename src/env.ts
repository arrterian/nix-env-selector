import { parse as parseEnv } from 'dotenv';

type EnvMapT = { [k: string]: string | undefined };

const set = (name: string, value: string | undefined) => {
  process.env[name] = value;
};

const setMultiple = (vars: [string, string | undefined][]) => {
  vars
    .filter(([_, value]) => !!value)
    .forEach(([name, value]) => set(name, value));
};

export const applyEnv = (envMap: EnvMapT) => {
  setMultiple(Object.entries(envMap));
};

export { parseEnv };