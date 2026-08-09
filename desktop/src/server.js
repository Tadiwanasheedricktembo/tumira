const path = require('path');
const os = require('os');
const express = require('express');
const http = require('http');
const cors = require('cors');
const morgan = require('morgan');
const { ensureStorage, config } = require('./storage');
const pingRoutes = require('./routes/ping');
const deviceRoutes = require('./routes/device');
const filesRoutes = require('./routes/files');
const { initSocket } = require('./socket');
const { advertiseServer } = require('./discovery');

const app = express();
const server = http.createServer(app);
const io = initSocket(server);

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(morgan('dev'));

app.use('/api/ping', pingRoutes);
app.use('/api/device', deviceRoutes);
app.use('/api/files', filesRoutes);

const publicPath = path.join(__dirname, 'public');
app.use(express.static(publicPath));
app.get('/', (req, res) => {
  res.sendFile(path.join(publicPath, 'index.html'));
});

app.use((err, req, res, next) => {
  console.error(err);
  const status = err.status || 500;
  res.status(status).json({ error: err.message || 'Internal server error' });
});

const getIPv4Addresses = () => {
  const addresses = [];
  const interfaces = os.networkInterfaces();

  Object.values(interfaces).forEach((entries) => {
    if (!entries) return;
    entries.forEach((entry) => {
      if (entry.family !== 'IPv4' || entry.internal || entry.address === '127.0.0.1') {
        return;
      }
      addresses.push(entry.address);
    });
  });

  return addresses;
};

const start = async () => {
  await ensureStorage();

  server.listen(config.port, '0.0.0.0', () => {
    console.log(`Tumira Server running on port ${config.port}`);
    console.log('SERVER_START', JSON.stringify({ port: config.port, deviceName: config.deviceName }));

    const addresses = getIPv4Addresses();
    if (addresses.length) {
      console.log('\nAvailable Network Addresses:');
      addresses.forEach((address) => {
        console.log(`http://${address}:${config.port}`);
      });
      console.log(`\nAPI Test:\nhttp://${addresses[0]}:${config.port}/api/ping\n`);
    } else {
      console.log('No non-internal IPv4 addresses were detected.');
    }

    console.log('Discovery service:');
    console.log('_queryshare._tcp');

    console.log(`Device name: ${config.deviceName}`);
    advertiseServer(config.port);
  });
};

start().catch((error) => {
  console.error('Failed to start server:', error);
  process.exit(1);
});
