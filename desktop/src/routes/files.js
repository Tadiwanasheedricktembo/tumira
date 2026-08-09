const express = require('express');
const fs = require('fs');
const path = require('path');
const multer = require('multer');
const { addFile, getAllFiles, getFileById, removeFile, config } = require('../storage');
const { emit } = require('../socket');

const router = express.Router();

const fileStorage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, config.sharedDir),
  filename: (req, file, cb) => cb(null, `${Date.now()}-${file.originalname.replace(/[^a-zA-Z0-9._-]/g, '_')}`)
});

const chunkStorage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, config.tempUploadDir),
  filename: (req, file, cb) => cb(null, `${Date.now()}-${file.fieldname}-${Math.random().toString(36).slice(2)}`)
});

const uploadFile = multer({
  storage: fileStorage,
  limits: { fileSize: config.maxFileSize },
  fileFilter: (req, file, cb) => {
    if (config.acceptedMimeTypes.includes(file.mimetype) || file.mimetype.startsWith('video/') || file.mimetype.startsWith('image/')) {
      return cb(null, true);
    }
    return cb(new Error('Unsupported mime type'));
  }
});

const uploadChunk = multer({
  storage: chunkStorage,
  limits: { fileSize: config.maxFileSize },
  fileFilter: (req, file, cb) => {
    if (config.acceptedMimeTypes.includes(req.headers['x-mime-type']) || req.headers['x-mime-type'].startsWith('video/') || req.headers['x-mime-type'].startsWith('image/')) {
      return cb(null, true);
    }
    return cb(new Error('Unsupported mime type'));
  }
});

const isChunkedUpload = (req) => {
  return req.headers['x-upload-id'] && req.headers['x-chunk-index'] != null && req.headers['x-total-chunks'] && req.headers['x-filename'];
};

const assembleChunks = async (uploadId, totalChunks, filename, mimeType) => {
  const chunkDir = path.join(config.tempUploadDir, uploadId);
  const targetName = `${Date.now()}-${filename.replace(/[^a-zA-Z0-9._-]/g, '_')}`;
  const outputPath = path.join(config.sharedDir, targetName);
  await fs.promises.mkdir(config.sharedDir, { recursive: true });

  const writeStream = fs.createWriteStream(outputPath);
  for (let index = 0; index < Number(totalChunks); index += 1) {
    const chunkPath = path.join(chunkDir, String(index));
    if (!fs.existsSync(chunkPath)) {
      writeStream.destroy();
      throw new Error(`Missing chunk ${index}`);
    }
    await new Promise((resolve, reject) => {
      const chunkStream = fs.createReadStream(chunkPath);
      chunkStream.on('error', reject);
      chunkStream.on('end', resolve);
      chunkStream.pipe(writeStream, { end: false });
    });
  }

  await new Promise((resolve, reject) => {
    writeStream.end(() => resolve());
    writeStream.on('error', reject);
  });

  await fs.promises.rm(chunkDir, { recursive: true, force: true });
  const stats = await fs.promises.stat(outputPath);
  return {
    storagePath: outputPath,
    filesize: stats.size,
    filename,
    mimeType
  };
};

router.get('/', async (req, res) => {
  const files = await getAllFiles();
  res.json(files.map((file) => ({ ...file, url: `/api/files/${file.id}` })));
});

router.get('/:id', async (req, res) => {
  const file = await getFileById(req.params.id);
  if (!file) {
    return res.status(404).json({ error: 'File not found' });
  }

  emit('downloadStarted', { id: file.id, filename: file.filename, timestamp: new Date().toISOString() });

  const stat = await fs.promises.stat(file.storagePath);
  const range = req.headers.range;
  const total = stat.size;
  let start = 0;
  let end = total - 1;

  if (range) {
    const parts = range.replace(/bytes=/, '').split('-');
    start = Number(parts[0]);
    end = parts[1] ? Number(parts[1]) : end;
    if (start >= total || end >= total) {
      res.status(416).setHeader('Content-Range', `bytes */${total}`);
      return res.end();
    }
    res.status(206);
    res.setHeader('Content-Range', `bytes ${start}-${end}/${total}`);
  }

  const stream = fs.createReadStream(file.storagePath, { start, end });
  res.setHeader('Content-Disposition', `attachment; filename="${file.filename}"`);
  res.setHeader('Content-Type', file.mimeType || 'application/octet-stream');
  res.setHeader('Accept-Ranges', 'bytes');
  res.setHeader('Content-Length', end - start + 1);

  stream.on('close', () => {
    emit('downloadCompleted', { id: file.id, filename: file.filename, timestamp: new Date().toISOString() });
  });
  stream.on('error', () => {
    res.end();
  });

  stream.pipe(res);
});

router.post('/upload', async (req, res, next) => {
  if (isChunkedUpload(req)) {
    const chunkUploadHandler = uploadChunk.single('chunk');
    return chunkUploadHandler(req, res, async (err) => {
      if (err) return next(err);
      const uploadId = req.headers['x-upload-id'];
      const chunkIndex = req.headers['x-chunk-index'];
      const totalChunks = req.headers['x-total-chunks'];
      const filename = req.headers['x-filename'];
      const mimeType = req.headers['x-mime-type'] || 'application/octet-stream';
      const chunkFile = req.file;
      if (!chunkFile) {
        return res.status(400).json({ error: 'Chunk file missing' });
      }

      const uploadFolder = path.join(config.tempUploadDir, uploadId);
      await fs.promises.mkdir(uploadFolder, { recursive: true });
      const chunkPath = path.join(uploadFolder, String(chunkIndex));
      await fs.promises.rename(chunkFile.path, chunkPath);

      emit('uploadStarted', { uploadId, chunkIndex: Number(chunkIndex), totalChunks: Number(totalChunks), filename, timestamp: new Date().toISOString() });

      const receivedChunks = await fs.promises.readdir(uploadFolder);
      if (receivedChunks.length === Number(totalChunks)) {
        const assembled = await assembleChunks(uploadId, totalChunks, filename, mimeType);
        const saved = await addFile(assembled);
        emit('uploadCompleted', { id: saved.id, filename: saved.filename, timestamp: new Date().toISOString() });
        emit('files_updated', await getAllFiles());
        return res.status(201).json(saved);
      }

      return res.status(202).json({ status: 'chunk received', chunkIndex: Number(chunkIndex) });
    });
  }

  const uploadSingle = uploadFile.single('file');
  uploadSingle(req, res, async (err) => {
    if (err) return next(err);
    if (!req.file) {
      return res.status(400).json({ error: 'File missing' });
    }

    emit('uploadStarted', { filename: req.file.originalname, timestamp: new Date().toISOString() });

    const saved = await addFile({
      filename: req.file.originalname,
      filesize: req.file.size,
      mimeType: req.file.mimetype,
      storagePath: req.file.path
    });

    emit('uploadCompleted', { id: saved.id, filename: saved.filename, timestamp: new Date().toISOString() });
    emit('files_updated', await getAllFiles());
    return res.status(201).json(saved);
  });
});

router.delete('/:id', async (req, res) => {
  const file = await removeFile(req.params.id);
  if (!file) {
    return res.status(404).json({ error: 'File not found' });
  }

  try {
    await fs.promises.unlink(file.storagePath);
  } catch (error) {
    console.warn('Unable to remove file from disk:', error.message || error);
  }

  emit('files_updated', await getAllFiles());
  res.json({ success: true, id: file.id });
});

module.exports = router;
