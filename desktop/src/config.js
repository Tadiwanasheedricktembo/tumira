const path = require('path');

const ROOT = path.resolve(__dirname, '..');

module.exports = {
  port: Number(process.env.PORT || 3000),
  deviceName: process.env.TUMIRA_DEVICE_NAME || 'Tumira Desktop',
  version: process.env.TUMIRA_VERSION || process.env.QUERYSHARE_VERSION || '1.0',
  sharedDir: path.resolve(process.env.SHARED_DIR || path.join(ROOT, 'shared-files')),
  dbPath: path.resolve(process.env.DB_PATH || path.join(ROOT, 'database', 'files.json')),
  tempUploadDir: path.resolve(process.env.TEMP_UPLOAD_DIR || path.join(ROOT, '.tmp', 'uploads')),
  maxFileSize: Number(process.env.MAX_FILE_SIZE || 10 * 1024 * 1024 * 1024),
  acceptedMimeTypes: [
    'image/png',
    'image/jpeg',
    'image/gif',
    'image/webp',
    'video/mp4',
    'video/quicktime',
    'application/pdf',
    'text/plain',
    'application/zip',
    'application/octet-stream',
    'application/vnd.android.package-archive'
  ]
};
