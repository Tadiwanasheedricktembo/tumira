const statusDot = document.getElementById('statusDot');
const statusText = document.getElementById('statusText');
const sidebarMeta = document.getElementById('sidebarMeta');
const filesCountBadge = document.getElementById('filesCountBadge');
const devicesCountBadge = document.getElementById('devicesCountBadge');
const feedArea = document.getElementById('feedArea');
const fileUploadInput = document.getElementById('fileUploadInput');
const uploadFileButton = document.getElementById('uploadFileButton');
const attachBtn = document.getElementById('attachBtn');
const mainInput = document.getElementById('mainInput');
const sendBtn = document.getElementById('sendBtn');
const navItems = document.querySelectorAll('.nav-item');
const headerIcon = document.getElementById('headerIcon');
const headerTitle = document.getElementById('headerTitle');
const headerSub = document.getElementById('headerSub');

let clientId = null;
let socket = null;
let connectedClients = [];
let fileSortOrder = 'desc';
let activeSection = 'files';

const fileTypeMap = {
  image: { color: '#53bdeb', label: 'IMG', icon: '🖼️' },
  doc: { color: '#cfc4ff', label: 'DOC', icon: '📄' },
  video: { color: '#fc8c6b', label: 'VID', icon: '🎬' },
  other: { color: '#6bcb77', label: 'FILE', icon: '📁' }
};

const formatSize = (bytes) => {
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const idx = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / 1024 ** idx).toFixed(1)} ${units[idx]}`;
};

const formatUploadTime = (value) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const now = new Date();
  const time = date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  if (date.toDateString() === now.toDateString()) {
    return `Today ${time}`;
  }
  return `${date.toLocaleDateString([], { month: 'short', day: 'numeric' })}, ${time}`;
};

const inferFileType = (filename) => {
  const extension = filename.toLowerCase().split('.').pop();
  const imageExt = ['jpg', 'jpeg', 'png', 'gif', 'webp'];
  const docExt = ['doc', 'docx', 'pdf', 'txt', 'md', 'ppt', 'pptx', 'xls', 'xlsx'];
  const videoExt = ['mp4', 'mov', 'avi', 'mkv', 'webm'];

  if (imageExt.includes(extension)) {
    return { ...fileTypeMap.image, label: extension.toUpperCase() };
  }
  if (docExt.includes(extension)) {
    return { ...fileTypeMap.doc, label: extension.toUpperCase() };
  }
  if (videoExt.includes(extension)) {
    return { ...fileTypeMap.video, label: extension.toUpperCase() };
  }
  return { ...fileTypeMap.other, label: extension ? extension.toUpperCase() : 'FILE' };
};

const setOnlineState = (online) => {
  if (online) {
    statusText.textContent = 'Online';
    statusDot.style.background = 'var(--accent-green)';
  } else {
    statusText.textContent = 'Offline';
    statusDot.style.background = 'gray';
  }
};

const setActiveSection = (section) => {
  activeSection = section;
  navItems.forEach((btn) => btn.classList.toggle('active', btn.dataset.section === section));
  // update header
  const map = {
    files: { icon: '📁', title: 'Files', sub: `${filesCountBadge.textContent} shared files` },
    devices: { icon: '🖥️', title: 'Devices', sub: `${devicesCountBadge.textContent} connected` },
    qr: { icon: '🔲', title: 'Hotspot Pairing', sub: 'Connect through this laptop' },
    activity: { icon: '📈', title: 'Activity', sub: headerSub.textContent || '' },
    chat: { icon: '💬', title: 'Chat', sub: 'All devices' }
  };
  const info = map[section] || map.files;
  headerIcon.textContent = info.icon;
  headerTitle.textContent = info.title;
  headerSub.textContent = info.sub;
  // render content
  if (section === 'files') fetchFiles();
  else if (section === 'devices') renderDevices();
  else if (section === 'qr') fetchDevice().then(() => renderQr());
  else if (section === 'activity') renderActivity();
  else if (section === 'chat') renderChatMessages();
};

const addActivity = (text) => {
  // add a left-aligned activity bubble to feed
  const div = document.createElement('div');
  div.className = 'bubble incoming';
  div.innerHTML = `<div class="activity-item"><strong>${text}</strong><div style="font-size:11px;color:var(--text-secondary);margin-top:6px">${new Date().toLocaleTimeString([], {hour:'numeric',minute:'2-digit'})}</div></div>`;
  feedArea.prepend(div);
};

// Play a short beep using WebAudio API
const playBeep = (frequency = 880, duration = 0.12) => {
  try {
    const C = window.AudioContext || window.webkitAudioContext;
    const ctx = new C();
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.type = 'sine';
    o.frequency.value = frequency;
    g.gain.value = 0.0001;
    o.connect(g);
    g.connect(ctx.destination);
    o.start();
    g.gain.exponentialRampToValueAtTime(0.06, ctx.currentTime + 0.01);
    g.gain.exponentialRampToValueAtTime(0.00001, ctx.currentTime + duration);
    setTimeout(() => { try { o.stop(); ctx.close(); } catch (e) {} }, duration * 1000 + 60);
  } catch (e) {
    console.warn('playBeep failed', e);
  }
};

// Show a browser notification (asks permission if needed) and play sound
const showDesktopNotification = async (title, body) => {
  if (!('Notification' in window)) return;
  if (Notification.permission === 'granted') {
    try { new Notification(title, { body }); } catch (e) { console.warn('notify', e); }
    playBeep();
  } else if (Notification.permission !== 'denied') {
    const perm = await Notification.requestPermission();
    if (perm === 'granted') {
      try { new Notification(title, { body }); } catch (e) { console.warn('notify', e); }
      playBeep();
    }
  }
};

const renderDevices = () => {
  const visibleClients = connectedClients.filter((device) => device.deviceType !== 'dashboard' && device.clientId !== clientId);
  feedArea.innerHTML = '';
  // system pill
  const sys = document.createElement('div'); sys.className = 'day-sep'; sys.innerHTML = `<div class="day-pill">${visibleClients.length} devices on this network</div>`; feedArea.append(sys);
  if (visibleClients.length === 0) {
    const empty = document.createElement('div');
    empty.className = 'bubble incoming';
    empty.textContent = 'No mobile devices are connected yet.';
    feedArea.append(empty);
  }
  visibleClients.forEach((device) => {
    const isSelf = device.clientId === clientId;
    const div = document.createElement('div');
    div.className = 'bubble incoming';
    div.innerHTML = `
      <div style="display:flex;gap:12px;align-items:center;">
        <div class="file-icon" style="background:#2a3942">🖥️</div>
        <div style="flex:1"><div style="font-weight:600">${device.deviceName} ${isSelf?'<span style="color:var(--accent-green);font-weight:600">(you)</span>':''}</div><div style="font-size:12px;color:var(--text-secondary);margin-top:6px">${device.ipAddress||'unknown'} · ${device.deviceType} · ${isSelf?'local':'paired'}</div></div>
      </div>
    `;
    div.addEventListener('click', ()=> showDeviceDetail(device));
    feedArea.append(div);
  });
  devicesCountBadge.textContent = visibleClients.length;
};

const showDeviceDetail = (device) => {
  // show a focused detail view for the selected device
  const detail = document.createElement('div'); detail.className='bubble incoming';
  detail.innerHTML = `
    <div style="display:flex;gap:12px;align-items:center">
      <div class="file-icon" style="background:#2a3942">🖥️</div>
      <div style="flex:1">
        <div style="font-weight:700">${device.deviceName}</div>
        <div style="color:var(--text-secondary);margin-top:6px">${device.deviceType} · ${device.ipAddress||'unknown'}</div>
        <div style="margin-top:10px">Client ID: <code style="background:rgba(255,255,255,0.03);padding:4px 6px;border-radius:6px">${device.clientId}</code></div>
      </div>
      <div style="display:flex;flex-direction:column;gap:8px"><button class="icon-btn" onclick="window.open('/api/pair/${device.clientId}','_blank')">Open</button><button class="icon-btn" onclick="alert('Disconnect not implemented')">Disconnect</button></div>
    </div>
  `;
  feedArea.append(detail);
  feedArea.scrollTop = feedArea.scrollHeight;
};

// chat scope controls removed (not used in redesigned UI)

const addChatMessage = (message, local = false) => {
  const div = document.createElement('div');
  div.className = `bubble ${local ? 'outgoing':'incoming'}`;
  div.innerHTML = `<div style="flex:1">${message.text}<div style="font-size:11px;color:var(--text-secondary);text-align:right;margin-top:6px">${new Date(message.timestamp||Date.now()).toLocaleTimeString([], {hour:'numeric',minute:'2-digit'})} ${local? '✔✔':''}</div></div>`;
  feedArea.appendChild(div);
  feedArea.scrollTop = feedArea.scrollHeight;
};

const renderFiles = (files) => {
  feedArea.innerHTML = '';
  filesCountBadge.textContent = files.length;
  document.getElementById('nav-files-sub').textContent = `${files.length} shared files`;
  // day separator
  const day = document.createElement('div'); day.className='day-sep'; day.innerHTML=`<div class="day-pill">Today</div>`; feedArea.append(day);
  files.forEach((file) => {
    const info = inferFileType(file.filename);
    const div = document.createElement('div'); div.className='bubble outgoing file-bubble';
    div.innerHTML = `
      <div class="file-icon" style="background:${info.color}">${info.icon}</div>
      <div class="file-meta">
        <div style="display:flex;gap:8px;align-items:center"><div style="font-weight:600;max-width:420px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${file.filename}</div><div style="font-size:11px;color:var(--text-secondary);padding:2px 8px;background:rgba(255,255,255,0.03);border-radius:6px">${info.label}</div><div style="font-size:11px;color:var(--text-secondary)">${formatSize(file.filesize)}</div></div>
        <div class="file-actions" style="margin-top:6px">
          <a href="${file.url}" style="color:var(--accent-green);text-decoration:none">Download ⬇</a>
          <button class="delete-btn" data-id="${file.id}" style="background:transparent;border:none;color:var(--text-secondary);cursor:pointer">Delete ✖</button>
          <div style="margin-left:12px;font-size:11px;color:var(--text-secondary)">${formatUploadTime(file.uploadDate)}</div>
        </div>
      </div>
    `;
    div.querySelector('.delete-btn').addEventListener('click', async (e) => { const id = e.target.dataset.id; await fetch(`/api/files/${id}`, { method:'DELETE' }); addActivity('File deleted'); fetchFiles(); });
    feedArea.append(div);
  });
  feedArea.scrollTop = feedArea.scrollHeight;
};

const fetchFiles = async () => {
  const res = await fetch('/api/files');
  const files = await res.json();
  const sortedFiles = files.sort((a,b)=>{ const aT = new Date(a.uploadDate).getTime()||0; const bT=new Date(b.uploadDate).getTime()||0; return fileSortOrder==='asc'? aT-bT: bT-aT; });
  if (activeSection === 'files') renderFiles(sortedFiles);
  return sortedFiles;
};

const uploadFile = async (file) => {
  const form = new FormData(); form.append('file', file);
  const response = await fetch('/api/files/upload', { method:'POST', body: form });
  if (!response.ok) { addActivity('Failed to share file'); return; }
  addActivity(`Shared ${file.name}`);
  await fetchFiles();
};

const sendChatMessage = () => {
  const text = mainInput.value.trim(); if (!text) return;
  if (!socket || !socket.connected) { addActivity('Chat send failed: disconnected'); return; }
  const payload = { text };
  socket.emit('send_message', payload, (response)=>{ if (!response?.success) addActivity(`Chat send failed: ${response?.error||'unknown'}`); else addActivity('Message sent'); });
  addChatMessage({ text, timestamp: Date.now() }, true);
  mainInput.value = '';
};

const updateQr = (container, data, size=130) => {
  container.innerHTML = '';
  new QRCode(container, { text: JSON.stringify({ deviceName: data.deviceName, ip: data.ip, alternateIps: data.alternateIps || [], port: data.port, protocol: data.protocol, version: data.version, mode: 'hotspot' }), width: size, height: size, colorDark:'#000000', colorLight:'#ffffff', correctLevel: QRCode.CorrectLevel.H });
};

const renderQr = async () => {
  const data = await fetchDevice();
  feedArea.innerHTML = '';
  const sys = document.createElement('div'); sys.className='day-sep'; sys.innerHTML=`<div class="day-pill">Hotspot Pairing</div>`; feedArea.append(sys);
  if (!data) { const m = document.createElement('div'); m.className='bubble incoming'; m.textContent = 'Device is offline — cannot generate QR.'; feedArea.append(m); return; }
  const interfaces = Array.isArray(data.interfaces) && data.interfaces.length ? data.interfaces : [{ label: 'Server address', name: 'default', address: data.ip }];
  const div = document.createElement('div'); div.className='bubble incoming';
  div.style.maxWidth = '760px';
  div.innerHTML = `
    <div style="display:flex;gap:18px;align-items:flex-start;flex-wrap:wrap;width:100%">
      <div id="qrWrap" style="width:160px;height:160px;background:#fff;padding:10px;border-radius:8px"></div>
      <div style="flex:1;min-width:260px">
        <div style="font-weight:700;font-size:16px">Hotspot Pairing</div>
        <div style="font-size:12px;color:var(--text-secondary);margin-top:8px;line-height:1.45">
          Turn on this laptop's mobile hotspot, connect your phone to that hotspot, then scan this QR in the Tumira Android app. If scan pairing fails, choose the address that starts with the same numbers as the phone Wi-Fi network.
        </div>
        <div style="margin-top:14px;font-size:12px;color:var(--text-secondary)">Server address</div>
        <select id="pairingIpSelect" style="margin-top:6px;width:100%;background:var(--input-bg);color:var(--text-primary);border:1px solid var(--border);border-radius:8px;padding:8px"></select>
        <div id="pairingUrl" style="margin-top:10px;font-size:12px;color:var(--accent-green);word-break:break-all"></div>
        <div style="display:grid;gap:6px;margin-top:14px;font-size:12px;color:var(--text-secondary);line-height:1.45">
          <div>1. On Windows: Settings > Network & internet > Mobile hotspot.</div>
          <div>2. Connect your phone to the laptop hotspot Wi-Fi.</div>
          <div>3. Open Tumira on Android and scan this QR.</div>
        </div>
      </div>
    </div>`;
  feedArea.append(div);
  const wrap = div.querySelector('#qrWrap');
  const select = div.querySelector('#pairingIpSelect');
  const url = div.querySelector('#pairingUrl');

  interfaces.forEach((entry) => {
    const option = document.createElement('option');
    option.value = entry.address;
    const recommended = entry.address === data.ip ? ' recommended' : '';
    option.textContent = `${entry.label || entry.name}: ${entry.address}${recommended}`;
    option.selected = entry.address === data.ip;
    select.append(option);
  });

  const renderSelectedQr = () => {
    const selectedIp = select.value || data.ip;
    const alternateIps = interfaces
      .map((entry) => entry.address)
      .filter((address) => address && address !== selectedIp);
    url.textContent = `${data.protocol}://${selectedIp}:${data.port}`;
    updateQr(wrap, { deviceName: data.deviceName, ip: selectedIp, alternateIps, port: data.port, protocol: data.protocol, version: data.version }, 140);
  };

  select.addEventListener('change', renderSelectedQr);
  renderSelectedQr();
};

const renderActivity = async () => {
  feedArea.innerHTML = '';
  const files = await fetchFiles();
  const sys = document.createElement('div'); sys.className='day-sep'; sys.innerHTML=`<div class="day-pill">Recent Activity</div>`; feedArea.append(sys);
  if (!files || files.length===0) { const m = document.createElement('div'); m.className='bubble incoming'; m.textContent = 'No recent activity.'; feedArea.append(m); return; }
  files.slice(0,10).forEach(f=>{ const d = document.createElement('div'); d.className='bubble incoming file-bubble'; d.innerHTML = `<div class="file-icon" style="background:#2a3942">📁</div><div class="file-meta"><div style="font-weight:600">${f.filename}</div><div style="font-size:11px;color:var(--text-secondary)">${formatUploadTime(f.uploadDate)}</div></div>`; feedArea.append(d); });
};

const renderChatMessages = () => {
  feedArea.innerHTML = '';
  const sys = document.createElement('div'); sys.className='day-sep'; sys.innerHTML=`<div class="day-pill">Chat</div>`; feedArea.append(sys);
  const m = document.createElement('div'); m.className='bubble incoming'; m.textContent = 'No messages yet.'; feedArea.append(m);
};

const fetchDevice = async () => {
  try {
    const res = await fetch('/api/device'); if (!res.ok) throw new Error('Device fetch failed'); const data = await res.json();
    sidebarMeta.textContent = `${data.deviceName} · ${data.ip}:${data.port}`;
    setOnlineState(true);
    return data;
  } catch (e) { setOnlineState(false); sidebarMeta.textContent = 'Offline'; console.warn('Failed to load device info', e); return null; }
};
const initSocket = () => {
  socket = io();
  socket.on('connect', () => { clientId = socket.id; addActivity('Connected to server'); socket.emit('handshake', { deviceName:'Tumira Dashboard', deviceType:'dashboard' }); });
  socket.on('connect_error', ()=>{ addActivity('Socket connection error'); setOnlineState(false); });
  socket.on('disconnect', ()=>{ addActivity('Disconnected from server'); setOnlineState(false); });
  socket.on('CLIENT_LIST', (devices)=>{
    connectedClients = (devices||[]).filter((device) => device.deviceType !== 'dashboard' && device.clientId !== clientId);
    if (activeSection==='devices') renderDevices();
    document.getElementById('nav-devices-sub').textContent = `${connectedClients.length} connected`;
    devicesCountBadge.textContent = connectedClients.length;
  });
  socket.on('CONNECTION_ACKNOWLEDGED', (payload)=>{ addActivity(payload?.message||'Server acknowledged connection'); try{ showDesktopNotification('Device paired', payload?.message||'A device paired'); }catch(e){} });
  socket.on('message_received', (payload)=>{ if (!payload || payload.from===clientId) return; addChatMessage(payload,false); });
  socket.on('message_sent', (payload)=>{ if (payload) addChatMessage(payload,true); });
  socket.on('uploadStarted', (p)=>addActivity(`Upload started: ${p.filename||p.uploadId}`));
  socket.on('uploadCompleted', (p)=>{ addActivity(`Upload completed: ${p.filename}`); fetchFiles(); });
  socket.on('files_updated', ()=>{ addActivity('File list updated'); fetchFiles(); });

  // UI bindings
  navItems.forEach(btn=> btn.addEventListener('click', ()=> setActiveSection(btn.dataset.section)));
  uploadFileButton.addEventListener('click', ()=> fileUploadInput.click());
  fileUploadInput.addEventListener('change', async (e)=>{ const f = e.target.files?.[0]; if (f) { await uploadFile(f); e.target.value=''; } });
  attachBtn.addEventListener('click', ()=> fileUploadInput.click());
  sendBtn.addEventListener('click', sendChatMessage);
  mainInput.addEventListener('keydown', (e)=>{ if (e.key==='Enter' && !e.shiftKey) { e.preventDefault(); sendChatMessage(); } });
};

const initNav = () => { setActiveSection(activeSection); };

window.addEventListener('DOMContentLoaded', async () => {
  await fetchDevice();
  initSocket();
  initNav();
  // periodic refresh
  setInterval(()=>{ fetchDevice(); fetchFiles(); }, 15000);
});
