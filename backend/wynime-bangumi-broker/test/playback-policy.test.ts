import { describe, expect, it } from "vitest";
import { isPlaybackChangeNewer } from "../src/playback-policy";

describe("playback conflict policy", () => {
  const current = { lastPlayedAt: 100, completed: false, deviceId: "device-b" };

  it("uses lastPlayedAt as the primary ordering", () => {
    expect(
      isPlaybackChangeNewer(
        {
          subjectId: 1,
          episodeId: 2,
          positionMs: 50,
          durationMs: 100,
          completed: false,
          lastPlayedAt: 101,
          baseRevision: 1,
        },
        current,
        "device-a",
      ),
    ).toBe(true);
  });

  it("uses completion and then device id as deterministic tie breakers", () => {
    expect(
      isPlaybackChangeNewer(
        {
          subjectId: 1,
          episodeId: 2,
          positionMs: 100,
          durationMs: 100,
          completed: true,
          lastPlayedAt: 100,
          baseRevision: 1,
        },
        current,
        "device-a",
      ),
    ).toBe(true);
    expect(
      isPlaybackChangeNewer(
        {
          subjectId: 1,
          episodeId: 2,
          positionMs: 50,
          durationMs: 100,
          completed: false,
          lastPlayedAt: 100,
          baseRevision: 1,
        },
        current,
        "device-a",
      ),
    ).toBe(false);
    expect(
      isPlaybackChangeNewer(
        {
          subjectId: 1,
          episodeId: 2,
          positionMs: 50,
          durationMs: 100,
          completed: false,
          lastPlayedAt: 100,
          baseRevision: 1,
        },
        current,
        "device-c",
      ),
    ).toBe(true);
  });
});
