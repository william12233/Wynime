import type { PlaybackChange } from "./protocol";

export interface ExistingPlaybackRecord {
  lastPlayedAt: number;
  completed: boolean;
  deviceId: string;
}

export function isPlaybackChangeNewer(
  change: PlaybackChange,
  current: ExistingPlaybackRecord,
  deviceId: string,
): boolean {
  if (change.lastPlayedAt !== current.lastPlayedAt) {
    return change.lastPlayedAt > current.lastPlayedAt;
  }
  if (change.completed !== current.completed) return change.completed;
  return deviceId > current.deviceId;
}
