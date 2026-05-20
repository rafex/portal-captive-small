import { describe, it, expect } from "vitest";
import { postJson } from "../api.js";

describe("postJson", () => {
  it("returns parsed body on success", async () => {
    const fakeFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({ userId: "u1" }),
    });

    const out = await postJson("http://x", "/auth/register", { a: 1 }, fakeFetch);
    expect(out.userId).toBe("u1");
  });

  it("throws with backend error message", async () => {
    const fakeFetch = async () => ({
      ok: false,
      status: 400,
      json: async () => ({ error: "invalid_input" }),
    });

    await expect(postJson("http://x", "/auth/register", { a: 1 }, fakeFetch)).rejects.toThrow("invalid_input");
  });

  it("falls back to http_status when body is not json", async () => {
    const fakeFetch = async () => ({
      ok: false,
      status: 503,
      json: async () => {
        throw new Error("bad-json");
      },
    });

    await expect(postJson("http://x", "/auth/register", { a: 1 }, fakeFetch)).rejects.toThrow("http_503");
  });
});
