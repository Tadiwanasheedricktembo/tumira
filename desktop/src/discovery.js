const os = require('os');
const bonjour = require('bonjour')();
const config = require('./config');

const getIPv4Interfaces = () => {
  const interfaces = os.networkInterfaces();
  const results = [];

  Object.entries(interfaces).forEach(([name, entries]) => {
    if (!entries) return;
    entries.forEach((entry) => {
      if (entry.family !== 'IPv4' || entry.internal || entry.address === '127.0.0.1') {
        return;
      }

      let label = name;
      if (/wi[-_]?fi|wlan|wireless|wl/i.test(name)) {
        label = 'Wi-Fi';
      } else if (/virtualbox|vbox|vmware|vmnet|host-only/i.test(name)) {
        label = 'VirtualBox';
      } else if (/ethernet|enp|eth/i.test(name)) {
        label = 'Ethernet';
      }

      results.push({ name, label, address: entry.address });
    });
  });

  return results;
};

const chooseAdvertisedInterface = (interfaces) => {
  const wifi = interfaces.find((entry) => entry.label === 'Wi-Fi');
  if (wifi) return wifi;
  const nonVm = interfaces.find((entry) => entry.label !== 'VirtualBox');
  return nonVm || interfaces[0] || null;
};

const advertiseServer = (port) => {
  try {
    const interfaces = getIPv4Interfaces();
    console.log('Available interfaces:');
    interfaces.forEach((entry) => {
      console.log(`* ${entry.label}: ${entry.address}`);
    });

    const advertised = chooseAdvertisedInterface(interfaces);
    if (advertised) {
      console.log('\nAdvertising on:');
      console.log(advertised.address);
    } else {
      console.log('\nAdvertising on:');
      console.log('none detected');
    }

    console.log('\nService:');
    console.log('_queryshare._tcp');
    console.log('Port:');
    console.log(port);

    const localHostname = os.hostname();
    const host = localHostname.endsWith('.local') ? localHostname : `${localHostname}.local`;
    console.log('Host:', host);

    const service = bonjour.publish({
      name: `${config.deviceName} - Tumira`,
      type: 'queryshare',
      host,
      port,
      txt: {
        deviceName: config.deviceName,
        version: config.version,
        protocol: 'queryshare'
      }
    });

    service.on('up', () => {
      console.log('Tumira discovery service is up on the local network.');
    });

    service.on('error', (error) => {
      console.warn('Discovery service error:', error.message || error);
    });

    return service;
  } catch (error) {
    console.warn('Unable to start discovery service:', error.message || error);
    return null;
  }
};

module.exports = {
  advertiseServer
};
