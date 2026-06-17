import forge from "node-forge";

export const BROWSER_SUBTLE_UNAVAILABLE_ERROR = "BROWSER_SUBTLE_UNAVAILABLE";

export type BrowserEncryptionMode = "webcrypto" | "js-fallback";

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

function stringToBase64(raw: string): string {
  return btoa(raw);
}

function randomBytes(length: number): Uint8Array {
  const bytes = new Uint8Array(length);
  globalThis.crypto?.getRandomValues(bytes);
  return bytes;
}

function fallbackRandomBytes(length: number): Uint8Array {
  const raw = forge.random.getBytesSync(length);
  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

async function sha256HexWebCrypto(raw: string): Promise<string> {
  const encoded = new TextEncoder().encode(raw);
  const digest = await crypto.subtle.digest("SHA-256", encoded);
  return [...new Uint8Array(digest)].map((item) => item.toString(16).padStart(2, "0")).join("");
}

function sha256HexFallback(raw: string): string {
  const digest = forge.md.sha256.create();
  digest.update(raw, "utf8");
  return digest.digest().toHex();
}

async function encryptWithWebCrypto(plaintext: string): Promise<UploadCipherPackage> {
  if (!globalThis.crypto?.subtle) {
    throw new Error(BROWSER_SUBTLE_UNAVAILABLE_ERROR);
  }

  const dek = randomBytes(32);
  const iv = randomBytes(12);
  const key = await crypto.subtle.importKey("raw", toArrayBuffer(dek), "AES-GCM", false, ["encrypt"]);
  const encrypted = await crypto.subtle.encrypt(
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

function encryptWithForge(plaintext: string): UploadCipherPackage {
  const dek = fallbackRandomBytes(32);
  const iv = fallbackRandomBytes(12);
  const cipher = forge.cipher.createCipher("AES-GCM", forge.util.createBuffer(toArrayBuffer(dek)).getBytes());
  cipher.start({
    iv: forge.util.createBuffer(toArrayBuffer(iv)).getBytes(),
    tagLength: 128
  });
  cipher.update(forge.util.createBuffer(forge.util.encodeUtf8(plaintext)));
  cipher.finish();

  const ciphertext = cipher.output.getBytes();
  const tag = cipher.mode.tag.getBytes();
  const payload = ciphertext + tag;

  return {
    dekBase64: bytesToBase64(dek),
    ivBase64: bytesToBase64(iv),
    encDataBase64: stringToBase64(payload),
    dataHash: sha256HexFallback(plaintext),
    encryptionMode: "js-fallback"
  };
}

export function browserCryptoStatus() {
  return {
    hasWebCrypto: Boolean(globalThis.crypto?.subtle),
    isSecureContext: globalThis.isSecureContext
  };
}

export async function encryptForUpload(plaintext: string): Promise<UploadCipherPackage> {
  if (globalThis.crypto?.subtle) {
    return encryptWithWebCrypto(plaintext);
  }

  return encryptWithForge(plaintext);
}
