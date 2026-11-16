export interface Env {
  REPLAY_MARKERS: DurableObjectNamespace;
  COLLECTION_STORES: DurableObjectNamespace;
  BANGUMI_CLIENT_ID: string;
  BANGUMI_CLIENT_SECRET: string;
  SESSION_ENCRYPTION_KEY: string;
  BANGUMI_AUTHORIZE_URL?: string;
  BANGUMI_TOKEN_URL?: string;
  BANGUMI_API_BASE_URL?: string;
  BANGUMI_CALLBACK_URL?: string;
  APP_LINK_REDIRECT_URI?: string;
  CUSTOM_SCHEME_REDIRECT_URI?: string;
  ANDROID_APP_PACKAGE?: string;
  ANDROID_APP_SHA256_CERT_FINGERPRINT?: string;
  ANDROID_APP_PACKAGES?: string;
  ANDROID_APP_SHA256_CERT_FINGERPRINTS?: string;
}

export interface PlaybackChange {
  subjectId: number;
  episodeId: number;
  positionMs: number;
  durationMs: number;
  completed: boolean;
  lastPlayedAt: number;
  baseRevision: number;
  deleted?: boolean;
}

export interface PlaybackServerChange {
  subjectId: number;
  episodeId: number;
  positionMs: number;
  durationMs: number;
  completed: boolean;
  lastPlayedAt: number;
  revision: number;
  deleted: boolean;
}

export interface PlaybackSyncRequest {
  deviceId: string;
  cursor: number;
  changes: PlaybackChange[];
}

export interface PlaybackSyncResponse {
  cursor: number;
  serverChanges: PlaybackServerChange[];
  accepted: Array<{
    subjectId: number;
    episodeId: number;
    revision: number;
  }>;
}

export interface OAuthStateRecord {
  redirectUri: string;
  expiresAt: number;
  status: "pending" | "complete" | "error";
  ticketHash?: string;
  error?: string;
}

export interface OAuthTicketRecord {
  expiresAt: number;
  payloadCiphertext: string;
}

export interface SessionRecord {
  userKey: string;
  expiresAt: number;
  payloadCiphertext: string;
}

export interface BangumiGrant {
  userKey: string;
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

export type CollectionSyncMode = "fast" | "reconcile";
export type CollectionRecordKind = "subject" | "episode";

export interface CollectionSyncMutation {
  mutationId: string;
  deviceId: string;
  kind: CollectionRecordKind;
  subjectId: number;
  episodeId?: number;
  modifiedAt: number;
  fieldModifiedAt?: Record<string, number>;
  changedFields: string[];
  collectionType?: string | null;
  rate?: number | null;
  comment?: string | null;
  tags?: string[] | null;
  private?: boolean | null;
  episodeStatus?: number | null;
  volumeStatus?: number | null;
}

export interface CollectionSyncRequest {
  deviceId: string;
  cursor: number;
  mode: CollectionSyncMode;
  fullSnapshot?: boolean;
  subjectIds?: number[];
  includeEpisodes?: boolean;
  changes: CollectionSyncMutation[];
}

export interface CollectionSyncRecord {
  kind: CollectionRecordKind;
  subjectId: number;
  episodeId?: number;
  collectionType?: string | null;
  rate?: number | null;
  comment?: string | null;
  tags?: string[] | null;
  private?: boolean | null;
  episodeStatus?: number | null;
  volumeStatus?: number | null;
  deleted: boolean;
  fieldModifiedAt: Record<string, number>;
  revision: number;
}

export interface CollectionSyncResponse {
  cursor: number;
  records: CollectionSyncRecord[];
  accepted: Array<{
    mutationId: string;
    revision: number;
    changed: boolean;
  }>;
  upstreamStatus: "not_requested" | "pending" | "unavailable" | "synced";
  pendingCount: number;
}

export interface CollectionStoreSyncResponse extends CollectionSyncResponse {
  /** Internal queue contents used by the worker's reconciliation pass. */
  pendingChanges: CollectionSyncMutation[];
}

export interface CollectionStoreMarkSyncedResponse {
  pendingCount: number;
}

export interface CollectionStorageProbeResponse {
  ok: true;
  storageLatencyMs: number;
}
