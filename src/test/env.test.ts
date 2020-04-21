import { assert } from "chai";

import { applyEnv } from "../env";

describe("env/applyEnv", () => {
  it("should apply environments map into global process var", () => {
    const result = applyEnv({
      test: "123",
    });

    assert.isTrue(result === true);
    assert.equal(process.env.test, "123");

    // cleanup
    delete process.env.test;
  });

  it("shouldn't apply undefined variables into global process var", () => {
    const result = applyEnv({
      test: undefined,
    });

    assert.isTrue(result === true);
    assert.notExists(process.env.test);
  });
});
