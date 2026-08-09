let io;
const config = require('./config');
const cm = require('./connectionManager');

const initSocket = (server) => {
  const { Server } = require('socket.io');
  io = new Server(server, {
    cors: {
      origin: '*',
      methods: ['GET', 'POST']
    }
  });

  io.on('connection', (socket) => {
    const clientId = socket.id;
    // register shallow client entry; handshake may follow
    cm.registerClient(socket, {});

    socket.on('handshake', (data = {}, callback) => {
      try {
        cm.registerClient(socket, data);
        console.log('HANDSHAKE_RECEIVED', JSON.stringify({ clientId, device: data.deviceName || data.device }));
        const response = {
          success: true,
          serverName: 'Tumira Desktop',
          serverVersion: config.version || '1.0',
          transferSupported: true,
          timestamp: new Date().toISOString()
        };
        if (typeof callback === 'function') callback(response);
        // broadcast updated client list to all connected clients
        io.emit('CLIENT_LIST', cm.getClientList());
        // notify the connected device specifically
        socket.emit('CONNECTION_ACKNOWLEDGED', {
          message: 'Welcome to Tumira Desktop',
          connectedAt: new Date().toISOString(),
          serverInfo: { name: config.deviceName, version: config.version }
        });
      } catch (error) {
        if (typeof callback === 'function') callback({ success: false, error: error.message });
      }
    });

    socket.on('send_message', (payload = {}, callback) => {
      try {
        console.log('RECEIVED_SEND_MESSAGE', JSON.stringify({ clientId, payload }));
        const { text, to } = payload;
        if (!text || !text.trim()) {
          if (typeof callback === 'function') callback({ success: false, error: 'Message cannot be empty' });
          return;
        }

        if (to && !io.sockets.sockets.get(to)) {
          if (typeof callback === 'function') callback({ success: false, error: 'Recipient not connected' });
          return;
        }
        
        const message = {
          id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
          from: clientId,
          fromName: cm.getClientList().find(c => c.clientId === clientId)?.deviceName || 'Unknown',
          to,
          text: text.trim(),
          timestamp: new Date().toISOString()
        };

        // Send to recipient or broadcast if no specific recipient
        if (to) {
          io.to(to).emit('message_received', message);
          console.log('RELAY_PRIVATE_MESSAGE', JSON.stringify({ from: clientId, to, messageId: message.id }));
        } else {
          io.emit('message_received', message);
          console.log('RELAY_BROADCAST_MESSAGE', JSON.stringify({ from: clientId, messageId: message.id }));
        }

        // Always echo send acknowledgement back to sender
        socket.emit('message_sent', { ...message, delivered: true });
        if (typeof callback === 'function') callback({ success: true, messageId: message.id });
      } catch (error) {
        console.error('SEND_MESSAGE_ERROR', error);
        if (typeof callback === 'function') callback({ success: false, error: error.message });
      }
    });


    socket.on('heartbeat', (payload) => {
      cm.updateHeartbeat(clientId);
      socket.emit('heartbeat_ack', { timestamp: new Date().toISOString() });
    });

    socket.on('list_devices', (cb) => {
      if (typeof cb === 'function') cb({ success: true, devices: cm.getClientList() });
    });

    socket.on('request_transfer', (req, cb) => {
      // req: { to, fileId, fileName, size, mimeType }
      try {
        const { to, fileId, fileName, size, mimeType } = req || {};
        const session = cm.createSession({ sender: clientId, receiver: to, fileId, fileName, size, mimeType });
        // notify receiver with transfer metadata and server URL info
        io.to(to).emit('TRANSFER_START', {
          ...session,
          fileId,
          sender: clientId,
          serverPort: config.port
        });
        if (typeof cb === 'function') cb({ success: true, sessionId: session.sessionId });
      } catch (error) {
        if (typeof cb === 'function') cb({ success: false, error: error.message, code: error.code });
      }
    });

    socket.on('transfer_progress', (payload) => {
      // payload: { sessionId, transferred }
      const { sessionId, transferred } = payload || {};
      const s = cm.getSession(sessionId);
      if (!s) return;
      // forward to interested parties
      io.to(s.sender).emit('TRANSFER_PROGRESS', { sessionId, transferred });
      io.to(s.receiver).emit('TRANSFER_PROGRESS', { sessionId, transferred });
    });

    socket.on('transfer_complete', (payload) => {
      // payload: { sessionId }
      const { sessionId } = payload || {};
      const s = cm.updateSessionStatus(sessionId, 'completed');
      if (!s) return;
      io.to(s.sender).emit('TRANSFER_COMPLETE', s);
      io.to(s.receiver).emit('TRANSFER_COMPLETE', s);
    });

    socket.on('transfer_failed', (payload) => {
      const { sessionId, reason } = payload || {};
      const s = cm.updateSessionStatus(sessionId, 'failed', { reason });
      if (!s) return;
      io.to(s.sender).emit('TRANSFER_FAILED', s);
      io.to(s.receiver).emit('TRANSFER_FAILED', s);
    });

    socket.on('disconnect', (reason) => {
      cm.unregisterClient(clientId);
      io.emit('CLIENT_LIST', cm.getClientList());
    });
  });

  return io;
};

const getIo = () => io;

const emit = (event, payload) => {
  if (io) io.emit(event, payload);
};

module.exports = {
  initSocket,
  getIo,
  emit
};
