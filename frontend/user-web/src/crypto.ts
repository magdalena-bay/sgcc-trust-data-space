export const BROWSER_SUBTLE_UNAVAILABLE_ERROR = "BROWSER_SUBTLE_UNAVAILABLE";

export type BrowserEncryptionMode = "webcrypto";

export interface UploadCipherPackage {
  dekBase64: string;
  ivBase64: string;
  encDataBase64: string;
  dataHash: string;
  encryptionMode: BrowserEncryptionMode;
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((value) => {
    binary += String.fromCharCode(value);
  });
  return btoa(binary);
}

function randomBytes(length: number): Uint8Array {
  const bytes = new Uint8Array(length);
  globalThis.crypto?.getRandomValues(bytes);
  return bytes;
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

async function sha256HexWebCrypto(raw: string): Promise<string> {
  const encoded = new TextEncoder().encode(raw);
  const digest = await globalThis.crypto.subtle.digest("SHA-256", encoded);
  return [...new Uint8Array(digest)].map((item) => item.toString(16).padStart(2, "0")).join("");
}

async function encryptWithWebCrypto(plaintext: string): Promise<UploadCipherPackage> {
  const subtle = globalThis.crypto?.subtle;
  if (!subtle || typeof subtle.importKey !== "function" || typeof subtle.encrypt !== "function") {
    throw new Error(BROWSER_SUBTLE_UNAVAILABLE_ERROR);
  }

  const dek = randomBytes(32);
  const iv = randomBytes(12);
  const key = await subtle.importKey("raw", toArrayBuffer(dek), "AES-GCM", false, ["encrypt"]);
  const encrypted = await subtle.encrypt(
    { name: "AES-GCM", iv: toArrayBuffer(iv) },
    key,
    new TextEncoder().encode(plaintext)
  );

  return {
    dekBase64: bytesToBase64(dek),
    ivBase64: bytesToBase64(iv),
    encDataBase64: bytesToBase64(new Uint8Array(encrypted)),
    dataHash: await sha256HexWebCrypto(plaintext),
    encryptionMode: "webcrypto"
  };
}

export function browserCryptoStatus() {
  const subtle = globalThis.crypto?.subtle;
  return {
    hasWebCrypto: Boolean(subtle && typeof subtle.importKey === "function" && typeof subtle.encrypt === "function"),
    isSecureContext: globalThis.isSecureContext
  };
}

export async function encryptForUpload(plaintext: string): Promise<UploadCipherPackage> {
  return encryptWithWebCrypto(plaintext);
}
