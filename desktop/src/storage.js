const fs = require('fs').promises;
const path = require('path');
const { v4: uuidv4 } = require('uuid');
const config = require('./config');

const ensureFolder = async (folderPath) => {
  await fs.mkdir(folderPath, { recursive: true });
};

const loadMetadata = async () => {
  try {
    const content = await fs.readFile(config.dbPath, 'utf8');
    return JSON.parse(content);
  } catch (error) {
    if (error.code === 'ENOENT') {
      await ensureFolder(path.dirname(config.dbPath));
      await fs.writeFile(config.dbPath, JSON.stringify([], null, 2), 'utf8');
      return [];
    }
    throw error;
  }
};

const saveMetadata = async (files) => {
  await ensureFolder(path.dirname(config.dbPath));
  await fs.writeFile(config.dbPath, JSON.stringify(files, null, 2), 'utf8');
};

const getAllFiles = async () => {
  return await loadMetadata();
};

const getFileById = async (id) => {
  const files = await loadMetadata();
  return files.find((file) => file.id === id);
};

const addFile = async ({ filename, filesize, mimeType, storagePath }) => {
  const files = await loadMetadata();
  const newFile = {
    id: uuidv4(),
    filename,
    filesize,
    mimeType,
    uploadDate: new Date().toISOString(),
    storagePath
  };
  files.push(newFile);
  await saveMetadata(files);
  return newFile;
};

const removeFile = async (id) => {
  const files = await loadMetadata();
  const index = files.findIndex((file) => file.id === id);
  if (index === -1) return null;
  const [removed] = files.splice(index, 1);
  await saveMetadata(files);
  return removed;
};

const updateFile = async (id, patch) => {
  const files = await loadMetadata();
  const file = files.find((item) => item.id === id);
  if (!file) return null;
  Object.assign(file, patch);
  await saveMetadata(files);
  return file;
};

const ensureStorage = async () => {
  await ensureFolder(config.sharedDir);
  await ensureFolder(config.tempUploadDir);
  await ensureFolder(path.dirname(config.dbPath));
  await loadMetadata();
};

module.exports = {
  config,
  ensureStorage,
  getAllFiles,
  getFileById,
  addFile,
  removeFile,
  updateFile
};
