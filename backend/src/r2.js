/**
 * Cloudflare R2 image storage (S3-compatible) for MarketScope AI.
 *
 * PRIVATE bucket + presigned URLs — no public r2.dev endpoint, no custom
 * domain needed. The backend uploads member images (win proofs, community
 * post images) and hands out short-lived signed GET URLs; the DB keeps
 * only the object key, so image bytes stop living in Postgres.
 *
 * INERT until configured: when the four R2_* env vars are missing (e.g.
 * local dev, or before the owner has pasted keys), every helper reports
 * isR2Configured() = false and callers fall back to the legacy
 * base64-in-Postgres path. Nothing breaks without keys.
 *
 * Env vars (Render dashboard):
 *   R2_ACCOUNT_ID        — Cloudflare account id (part of the endpoint)
 *   R2_ACCESS_KEY_ID      — from the R2 API token
 *   R2_SECRET_ACCESS_KEY  — from the R2 API token (shown once)
 *   R2_BUCKET             — e.g. "marketscope-images"
 */
const { S3Client, PutObjectCommand, GetObjectCommand } = require("@aws-sdk/client-s3");
const { getSignedUrl } = require("@aws-sdk/s3-request-presigner");
const crypto = require("crypto");

const R2_ACCOUNT_ID = process.env.R2_ACCOUNT_ID || "";
const R2_ACCESS_KEY_ID = process.env.R2_ACCESS_KEY_ID || "";
const R2_SECRET_ACCESS_KEY = process.env.R2_SECRET_ACCESS_KEY || "";
const R2_BUCKET = process.env.R2_BUCKET || "";

let client = null;

/** True only when every credential is present — gates every R2 code path. */
function isR2Configured() {
  return !!(R2_ACCOUNT_ID && R2_ACCESS_KEY_ID && R2_SECRET_ACCESS_KEY && R2_BUCKET);
}

/** Lazy S3 client pointed at the account's R2 endpoint. */
function r2Client() {
  if (!isR2Configured()) return null;
  if (!client) {
    client = new S3Client({
      region: "auto",
      endpoint: `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com`,
      credentials: {
        accessKeyId: R2_ACCESS_KEY_ID,
        secretAccessKey: R2_SECRET_ACCESS_KEY
      }
    });
  }
  return client;
}

/**
 * Uploads image bytes to the private bucket.
 * @param {Buffer} buffer raw image bytes
 * @param {string} contentType e.g. "image/png"
 * @param {string} folder logical prefix, e.g. "wins" or "posts"
 * @returns {Promise<string>} the object key (stored in the DB)
 */
async function uploadImage(buffer, contentType, folder) {
  const s3 = r2Client();
  if (!s3) throw new Error("R2 is not configured");
  const ext = (contentType.split("/")[1] || "jpeg").replace("jpeg", "jpg");
  const key = `${folder}/${new Date().toISOString().slice(0, 10)}/${crypto.randomUUID()}.${ext}`;
  await s3.send(new PutObjectCommand({
    Bucket: R2_BUCKET,
    Key: key,
    Body: buffer,
    ContentType: contentType,
    CacheControl: "private, max-age=86400"
  }));
  return key;
}

/**
 * Short-lived signed GET URL for a stored object. The caller must have
 * already done its own access check BEFORE signing — whoever holds the
 * URL can fetch the bytes until it expires.
 * @param {string} key object key from uploadImage
 * @param {number} [expiresIn] seconds the URL stays valid (default 15 min)
 * @returns {Promise<string>} https URL on the R2 endpoint
 */
async function signedImageUrl(key, expiresIn = 900) {
  const s3 = r2Client();
  if (!s3) throw new Error("R2 is not configured");
  return getSignedUrl(s3, new GetObjectCommand({ Bucket: R2_BUCKET, Key: key }), { expiresIn });
}

module.exports = { isR2Configured, uploadImage, signedImageUrl };
