const express = require('express');
const os = require('os');
const router = express.Router();
const config = require('../config');

const getLanInterfaces = () => {
  const interfaces = os.networkInterfaces();
  const results = [];

  for (const [name, names] of Object.entries(interfaces)) {
    if (!names) continue;
    for (const addr of names) {
      if (addr.family === 'IPv4' && !addr.internal && addr.address !== '127.0.0.1') {
        const isWindowsHotspot = /local area connection|hotspot|mobile hotspot/i.test(name) || /^192\.168\.137\./.test(addr.address);
        let label = name;
        let priority = 20;

        if (isWindowsHotspot) {
          label = 'Windows Hotspot';
          priority = 100;
        } else if (/wi[-_ ]?fi|wlan|wireless/i.test(name)) {
          label = 'Wi-Fi / Hotspot';
          priority = 80;
        } else if (/ethernet|enp|eth/i.test(name)) {
          label = 'Ethernet';
          priority = 60;
        } else if (/virtualbox|vbox|vmware|vmnet|host-only|wsl|docker/i.test(name)) {
          label = 'Virtual / Adapter';
          priority = 10;
        }

        results.push({
          name,
          label,
          address: addr.address,
          priority
        });
      }
    }
  }

  return results.sort((a, b) => b.priority - a.priority);
};

const choosePreferredIp = (interfaces) => {
  return (interfaces[0] || { address: '127.0.0.1' }).address;
};

router.get('/', (req, res) => {
  const interfaces = getLanInterfaces();
  res.json({
    deviceName: config.deviceName,
    version: config.version,
    ip: choosePreferredIp(interfaces),
    interfaces,
    port: config.port,
    protocol: 'http'
  });
});

module.exports = router;
