import type {
  AccessRequest,
  AccessResponse,
  ResourceDetail,
  ResourceSummary,
  ResourceVerkle,
  SystemStatus,
  UploadRequest
} from "./types";

export const API_BASE =
  import.meta.env.VITE_API_BASE ||
  (typeof window !== "undefined" ? `${window.location.protocol}//${window.location.hostname}:8088` : "http://127.0.0.1:8088");

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json"
    },
    ...init
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `request failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export function fetchResources(): Promise<ResourceSummary[]> {
  return request<ResourceSummary[]>("/api/demo/resources");
}

export function fetchResourceDetail(dataId: string): Promise<ResourceDetail> {
  return request<ResourceDetail>(`/api/demo/resources/${dataId}`);
}

export function fetchResourceVerkle(dataId: string): Promise<ResourceVerkle> {
  return request<ResourceVerkle>(`/api/demo/resources/${dataId}/verkle`);
}

export function fetchSystemStatus(): Promise<SystemStatus> {
  return request<SystemStatus>("/api/demo/system-status");
}

export function uploadResource(payload: UploadRequest) {
  return request("/api/demo/upload", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function accessResource(payload: AccessRequest): Promise<AccessResponse> {
  return request<AccessResponse>("/api/demo/access", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}
