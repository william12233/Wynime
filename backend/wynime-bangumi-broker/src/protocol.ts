export interface Env {
  REPLAY_MARKERS: DurableObjectNamespace;
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
