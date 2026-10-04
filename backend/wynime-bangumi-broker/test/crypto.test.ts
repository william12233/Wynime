import { describe, expect, it } from "vitest";
import { decryptJson, encryptJson, hashToken, randomToken } from "../src/crypto";

describe("broker crypto", () => {
  it("hashes deterministically without returning the source token", async () => {
    const token = randomToken();
    const first = await hashToken(token);
    const second = await hashToken(token);
    expect(first).toBe(second);
    expect(first).not.toBe(token);
  });

  it("round-trips encrypted session payloads", async () => {
    const value = { userKey: "bangumi:42", expiresAt: 1234 };
    const encrypted = await encryptJson(value, "test-session-encryption-key");
    expect(encrypted).not.toContain("bangumi:42");
    await expect(decryptJson<typeof value>(encrypted, "test-session-encryption-key")).resolves.toEqual(value);
  });
});
