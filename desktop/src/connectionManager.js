const { v4: uuidv4 } = require('uuid');
const config = require('./config');

const ACTIVE_CLIENT_TTL_MS = 2 * 60 * 1000; // 2 minutes
const PRUNE_INTERVAL_MS = 30 * 1000; // 30 seconds

const activeClients = new Map(); // clientId -> { clientId, deviceName, deviceType, appVersion, ipAddress, lastSeen, status }
const sessions = new Map(); // sessionId -> { sessionId, sender, receiver, fileName, size, mimeType, status, createdAt }

const now = () => Date.now();

const registerClient = (socket, handshakeData = {}) => {
  const clientId = socket.id;
  const ip = socket.handshake && socket.handshake.address ? socket.handshake.address : (socket.request && socket.request.connection && socket.request.connection.remoteAddress) || 'unknown';
  const entry = {
    clientId,
    deviceName: handshakeData.deviceName || handshakeData.device || 'Unknown Device',
    deviceType: handshakeData.deviceType || 'unknown',
    appVersion: handshakeData.appVersion || 'unknown',
    ipAddress: ip,
    lastSeen: now(),
    status: 'online'
  };
  activeClients.set(clientId, entry);
  console.log('CLIENT_CONNECTED', JSON.stringify({ clientId, deviceName: entry.deviceName, ip: entry.ipAddress }));
  return entry;
};

const updateHeartbeat = (clientId) => {
  const entry = activeClients.get(clientId);
  if (!entry) return null;
  entry.lastSeen = now();
  if (entry.status !== 'online') entry.status = 'online';
  activeClients.set(clientId, entry);
  return entry;
};

const unregisterClient = (clientId) => {
  const existed = activeClients.get(clientId);
  if (existed) {
    activeClients.delete(clientId);
    console.log('CLIENT_DISCONNECTED', JSON.stringify({ clientId }));
  }
};

const getClientList = ({ includeDashboard = false } = {}) => {
  return Array.from(activeClients.values())
    .filter((c) => includeDashboard || c.deviceType !== 'dashboard')
    .map((c) => ({ clientId: c.clientId, deviceName: c.deviceName, ipAddress: c.ipAddress, status: c.status, appVersion: c.appVersion, deviceType: c.deviceType }));
};

const pruneStaleClients = () => {
  const cutoff = now() - ACTIVE_CLIENT_TTL_MS;
  const removed = [];
  activeClients.forEach((entry, clientId) => {
    if (entry.lastSeen < cutoff) {
      activeClients.delete(clientId);
      removed.push(clientId);
      console.log('CLIENT_REMOVED_STALE', JSON.stringify({ clientId }));
    }
  });
  return removed;
};

// Sessions
const createSession = ({ sender, receiver, fileId, fileName, size, mimeType }) => {
  // basic validation
  if (!sender || !receiver || !fileId || !fileName) {
    throw new Error('Invalid session parameters');
  }

  if (size > config.maxFileSize) {
    const err = new Error('File too large');
    err.code = 'FILE_TOO_LARGE';
    throw err;
  }

  // prevent duplicate active session for same sender/receiver/fileName
  for (const s of sessions.values()) {
    if (s.sender === sender && s.receiver === receiver && s.fileName === fileName && s.status === 'active') {
      const err = new Error('Duplicate session');
      err.code = 'DUPLICATE_SESSION';
      throw err;
    }
  }

  const sessionId = uuidv4();
  const item = {
    sessionId,
    sender,
    receiver,
    fileId,
    fileName,
    size,
    mimeType,
    status: 'active',
    createdAt: new Date().toISOString()
  };
  sessions.set(sessionId, item);
  console.log('TRANSFER_STARTED', JSON.stringify({ sessionId, sender, receiver, fileName, size }));
  return item;
};

const updateSessionStatus = (sessionId, status, extra = {}) => {
  const s = sessions.get(sessionId);
  if (!s) return null;
  s.status = status;
  Object.assign(s, extra);
  if (status === 'completed' || status === 'failed' || status === 'cancelled') {
    s.completedAt = new Date().toISOString();
    console.log(status === 'completed' ? 'TRANSFER_COMPLETED' : 'TRANSFER_FAILED', JSON.stringify({ sessionId, ...extra }));
  }
  sessions.set(sessionId, s);
  return s;
};

const getSession = (sessionId) => sessions.get(sessionId) || null;

const listSessions = () => Array.from(sessions.values());

// start prune interval
setInterval(() => {
  pruneStaleClients();
}, PRUNE_INTERVAL_MS);

module.exports = {
  registerClient,
  unregisterClient,
  updateHeartbeat,
  getClientList,
  createSession,
  updateSessionStatus,
  getSession,
  listSessions
};
