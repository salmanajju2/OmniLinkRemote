/**
 * OmniLink Remote - Web Controller & Viewer Client
 * High-performance WebRTC Remote Desktop with Direct Touch, File Transfer & Navigation
 */

// Global State
let ws = null;
let peerConnection = null;
let inputChannel = null;
let fileChannel = null;
let currentTargetHostId = null;
let iceServers = [{ urls: 'stun:stun.l.google.com:19302' }];
let isDragging = false;
let lastInputSendTime = 0;
let lastFrameCount = 0;
let lastFpsCheck = performance.now();

// DOM Elements
const wsStatus = document.getElementById('wsStatus');
const connectSection = document.getElementById('connectSection');
const sessionSection = document.getElementById('sessionSection');
const connectForm = document.getElementById('connectForm');
const partnerIdInput = document.getElementById('partnerId');
const partnerPinInput = document.getElementById('partnerPin');
const partnerPasswordInput = document.getElementById('partnerPassword');
const pinInputGroup = document.getElementById('pinInputGroup');
const unattendedInputGroup = document.getElementById('unattendedInputGroup');
const tabPinBtn = document.getElementById('tabPinBtn');
const tabUnattendedBtn = document.getElementById('tabUnattendedBtn');
const remoteVideo = document.getElementById('remoteVideo');
const screenWrapper = document.getElementById('screenWrapper');
const screenPlaceholder = document.getElementById('screenPlaceholder');
const touchPointer = document.getElementById('touchPointer');
const connectedDeviceName = document.getElementById('connectedDeviceName');
const fpsDisplay = document.getElementById('fpsDisplay');
const latencyDisplay = document.getElementById('latencyDisplay');

// Modals & Drawers
const fileTransferModal = document.getElementById('fileTransferModal');
const btnToggleFileTransfer = document.getElementById('btnToggleFileTransfer');
const btnCloseFileTransfer = document.getElementById('btnCloseFileTransfer');
const filePicker = document.getElementById('filePicker');
const fileQueue = document.getElementById('fileQueue');
const chatDrawer = document.getElementById('chatDrawer');
const btnToggleChat = document.getElementById('btnToggleChat');
const btnCloseChat = document.getElementById('btnCloseChat');
const chatMessages = document.getElementById('chatMessages');
const chatInput = document.getElementById('chatInput');
const btnSendChat = document.getElementById('btnSendChat');
const btnDisconnect = document.getElementById('btnDisconnect');

// Remote Navigation Buttons
const btnRemoteBack = document.getElementById('btnRemoteBack');
const btnRemoteHome = document.getElementById('btnRemoteHome');
const btnRemoteRecents = document.getElementById('btnRemoteRecents');
const btnRemoteNotifications = document.getElementById('btnRemoteNotifications');
const btnRemoteLock = document.getElementById('btnRemoteLock');

// Auto format 9-digit ID with spaces: "458 920 114"
partnerIdInput.addEventListener('input', (e) => {
  let val = e.target.value.replace(/\D/g, '').substring(0, 9);
  if (val.length > 6) {
    val = `${val.slice(0, 3)} ${val.slice(3, 6)} ${val.slice(6)}`;
  } else if (val.length > 3) {
    val = `${val.slice(0, 3)} ${val.slice(3)}`;
  }
  e.target.value = val;
});

// Toggle Auth Tabs (PIN vs Unattended Password)
tabPinBtn.addEventListener('click', () => {
  tabPinBtn.classList.add('active');
  tabUnattendedBtn.classList.remove('active');
  pinInputGroup.classList.remove('hidden');
  unattendedInputGroup.classList.add('hidden');
  partnerPinInput.required = true;
  partnerPasswordInput.required = false;
});

tabUnattendedBtn.addEventListener('click', () => {
  tabUnattendedBtn.classList.add('active');
  tabPinBtn.classList.remove('active');
  unattendedInputGroup.classList.remove('hidden');
  pinInputGroup.classList.add('hidden');
  partnerPasswordInput.required = true;
  partnerPinInput.required = false;
});

// 1. WebSocket Signaling Connection
function initWebSocket() {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsUrl = `${protocol}//${location.host}/ws`;

  ws = new WebSocket(wsUrl);

  ws.onopen = () => {
    updateWsStatus(true, 'Server Connected');
  };

  ws.onclose = () => {
    updateWsStatus(false, 'Disconnected (Reconnecting...)');
    setTimeout(initWebSocket, 3000);
  };

  ws.onerror = (err) => {
    console.error('Signaling error:', err);
  };

  ws.onmessage = async (event) => {
    const msg = JSON.parse(event.data);
    handleSignalingMessage(msg);
  };
}

function updateWsStatus(connected, text) {
  const dot = wsStatus.querySelector('.status-dot');
  const label = wsStatus.querySelector('.status-label');
  if (connected) {
    dot.className = 'status-dot connected';
  } else {
    dot.className = 'status-dot disconnected';
  }
  label.innerText = text;
}

// 2. Handle Signaling Messages
async function handleSignalingMessage(msg) {
  switch (msg.type) {
    case 'connect-accepted':
      currentTargetHostId = msg.targetHostId;
      connectedDeviceName.innerText = msg.deviceName || 'Android Device';
      if (msg.iceServers) iceServers = msg.iceServers;
      startWebRtcSession();
      break;

    case 'connect-error':
      alert(`Connection failed: ${msg.reason}`);
      document.getElementById('btnConnect').disabled = false;
      document.getElementById('btnConnect').innerHTML = '<i class="fa-solid fa-link"></i> Connect Now';
      break;

    case 'offer':
      if (peerConnection) {
        await peerConnection.setRemoteDescription(new RTCSessionDescription(msg.sdp));
        const answer = await peerConnection.createAnswer();
        await peerConnection.setLocalDescription(answer);
        sendSignaling({
          type: 'answer',
          targetId: currentTargetHostId,
          sdp: answer
        });
      }
      break;

    case 'answer':
      if (peerConnection) {
        await peerConnection.setRemoteDescription(new RTCSessionDescription(msg.sdp));
      }
      break;

    case 'ice-candidate':
      if (peerConnection && msg.candidate) {
        try {
          await peerConnection.addIceCandidate(new RTCIceCandidate(msg.candidate));
        } catch (e) {
          console.error('Error adding ICE candidate:', e);
        }
      }
      break;

    case 'chat-message':
      appendChatMessage(msg.text, 'theirs');
      break;
  }
}

function sendSignaling(msg) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(msg));
  }
}

// 3. Connect Form Submit
connectForm.addEventListener('submit', (e) => {
  e.preventDefault();
  const rawId = partnerIdInput.value.replace(/\s/g, '');
  const pin = partnerPinInput.value.trim();
  const password = partnerPasswordInput.value.trim();

  if (!rawId || rawId.length < 9) {
    alert('Please enter a valid 9-digit Partner ID');
    return;
  }

  const btn = document.getElementById('btnConnect');
  btn.disabled = true;
  btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Connecting...';

  sendSignaling({
    type: 'connect-request',
    targetId: rawId,
    pin: pin || undefined,
    password: password || undefined,
    clientName: 'Web Desktop Controller'
  });
});

// 4. Initialize WebRTC PeerConnection
function startWebRtcSession() {
  connectSection.classList.add('hidden');
  sessionSection.classList.remove('hidden');

  peerConnection = new RTCPeerConnection({
    iceServers: iceServers,
    sdpSemantics: 'unified-plan',
    iceCandidatePoolSize: 10
  });

  // Create High-Performance DataChannel for Touch & Gestures
  inputChannel = peerConnection.createDataChannel('omni-input', {
    ordered: true,
    maxRetransmits: 0 // Ultra-low latency, zero queue delay
  });

  inputChannel.onopen = () => {
    console.log('✅ Touch & Input DataChannel Ready');
  };

  // Create DataChannel for Direct P2P File Transfer
  fileChannel = peerConnection.createDataChannel('omni-file', {
    ordered: true
  });
  setupFileChannel(fileChannel);

  // Receive Remote Screen Video Track
  peerConnection.ontrack = (event) => {
    console.log('🎥 Received Remote Media Track:', event.streams);
    if (event.streams && event.streams[0]) {
      remoteVideo.srcObject = event.streams[0];
      screenPlaceholder.classList.add('hidden');
      startStatsMonitor();
    }
  };

  // ICE Candidate exchange
  peerConnection.onicecandidate = (event) => {
    if (event.candidate) {
      sendSignaling({
        type: 'ice-candidate',
        targetId: currentTargetHostId,
        candidate: event.candidate
      });
    }
  };

  // Create SDP Offer if host expects controller to initiate
  peerConnection.createOffer({
    offerToReceiveVideo: true,
    offerToReceiveAudio: true
  }).then((offer) => {
    return peerConnection.setLocalDescription(offer);
  }).then(() => {
    sendSignaling({
      type: 'offer',
      targetId: currentTargetHostId,
      sdp: peerConnection.localDescription
    });
  }).catch((err) => {
    console.error('Error creating offer:', err);
  });
}

// 5. Remote Touch & Mouse Event Dispatcher
function getNormalizedCoordinates(e) {
  const rect = remoteVideo.getBoundingClientRect();
  const videoWidth = remoteVideo.videoWidth || rect.width;
  const videoHeight = remoteVideo.videoHeight || rect.height;

  // Compute actual rendered video dimensions inside contain mode
  const videoRatio = videoWidth / videoHeight;
  const elemRatio = rect.width / rect.height;

  let renderW, renderH, offsetX, offsetY;
  if (elemRatio > videoRatio) {
    renderH = rect.height;
    renderW = rect.height * videoRatio;
    offsetX = (rect.width - renderW) / 2;
    offsetY = 0;
  } else {
    renderW = rect.width;
    renderH = rect.width / videoRatio;
    offsetX = 0;
    offsetY = (rect.height - renderH) / 2;
  }

  const clientX = e.clientX - rect.left - offsetX;
  const clientY = e.clientY - rect.top - offsetY;

  // Normalized relative coords (0.0 to 1.0)
  const normX = Math.max(0, Math.min(1, clientX / renderW));
  const normY = Math.max(0, Math.min(1, clientY / renderH));

  return { x: normX, y: normY, valid: clientX >= 0 && clientX <= renderW && clientY >= 0 && clientY <= renderH };
}

function sendRemoteInput(action, x, y) {
  const packet = {
    t: 'input',
    a: action, // 'DOWN', 'MOVE', 'UP', 'CLICK'
    x: parseFloat(x.toFixed(4)),
    y: parseFloat(y.toFixed(4)),
    ts: Date.now()
  };

  if (inputChannel && inputChannel.readyState === 'open') {
    inputChannel.send(JSON.stringify(packet));
  } else {
    // Fallback over WebSocket signaling
    sendSignaling({
      type: 'remote-input',
      targetId: currentTargetHostId,
      ...packet
    });
  }
}

function sendRemoteKeyCommand(command) {
  const packet = {
    t: 'nav',
    cmd: command // 'BACK', 'HOME', 'RECENTS', 'NOTIFICATIONS', 'LOCK'
  };

  if (inputChannel && inputChannel.readyState === 'open') {
    inputChannel.send(JSON.stringify(packet));
  } else {
    sendSignaling({
      type: 'remote-command',
      targetId: currentTargetHostId,
      ...packet
    });
  }
}

// Mouse Touch Simulation on Video
remoteVideo.addEventListener('mousedown', (e) => {
  if (e.button === 2) return; // Right click handled by contextmenu
  const coords = getNormalizedCoordinates(e);
  if (!coords.valid) return;

  isDragging = true;
  showTouchPointer(e.clientX, e.clientY);
  sendRemoteInput('DOWN', coords.x, coords.y);
});

// Mobile Phone Touchscreen Support (Phone-to-Phone direct control in mobile browser)
remoteVideo.addEventListener('touchstart', (e) => {
  if (e.touches.length > 0) {
    const touch = e.touches[0];
    const coords = getNormalizedCoordinates(touch);
    if (!coords.valid) return;
    isDragging = true;
    showTouchPointer(touch.clientX, touch.clientY);
    sendRemoteInput('DOWN', coords.x, coords.y);
  }
}, { passive: false });

remoteVideo.addEventListener('touchmove', (e) => {
  if (!isDragging || e.touches.length === 0) return;
  e.preventDefault();
  const touch = e.touches[0];
  const now = performance.now();
  if (now - lastInputSendTime < 16) return;
  lastInputSendTime = now;

  const coords = getNormalizedCoordinates(touch);
  if (!coords.valid) return;

  showTouchPointer(touch.clientX, touch.clientY);
  sendRemoteInput('MOVE', coords.x, coords.y);
}, { passive: false });

remoteVideo.addEventListener('touchend', (e) => {
  if (!isDragging) return;
  isDragging = false;
  hideTouchPointer();
  if (e.changedTouches.length > 0) {
    const touch = e.changedTouches[0];
    const coords = getNormalizedCoordinates(touch);
    sendRemoteInput('UP', coords.x, coords.y);
  }
});

window.addEventListener('mousemove', (e) => {
  if (!isDragging) return;
  const now = performance.now();
  if (now - lastInputSendTime < 16) return; // 60 FPS throttle to eliminate lag
  lastInputSendTime = now;

  const coords = getNormalizedCoordinates(e);
  if (!coords.valid) return;

  showTouchPointer(e.clientX, e.clientY);
  sendRemoteInput('MOVE', coords.x, coords.y);
});

window.addEventListener('mouseup', (e) => {
  if (!isDragging) return;
  isDragging = false;
  hideTouchPointer();
  const coords = getNormalizedCoordinates(e);
  sendRemoteInput('UP', coords.x, coords.y);
});

// Right click sends remote Android BACK action
remoteVideo.addEventListener('contextmenu', (e) => {
  e.preventDefault();
  sendRemoteKeyCommand('BACK');
});

// Wheel scroll sends scroll gesture
remoteVideo.addEventListener('wheel', (e) => {
  e.preventDefault();
  const coords = getNormalizedCoordinates(e);
  if (!coords.valid) return;
  const direction = e.deltaY > 0 ? 'SCROLL_DOWN' : 'SCROLL_UP';
  sendRemoteInput(direction, coords.x, coords.y);
}, { passive: false });

function showTouchPointer(clientX, clientY) {
  touchPointer.style.left = `${clientX}px`;
  touchPointer.style.top = `${clientY}px`;
  touchPointer.classList.remove('hidden');
}

function hideTouchPointer() {
  touchPointer.classList.add('hidden');
}

// 6. Navigation Toolbar Handlers
btnRemoteBack.addEventListener('click', () => sendRemoteKeyCommand('BACK'));
btnRemoteHome.addEventListener('click', () => sendRemoteKeyCommand('HOME'));
btnRemoteRecents.addEventListener('click', () => sendRemoteKeyCommand('RECENTS'));
btnRemoteNotifications.addEventListener('click', () => sendRemoteKeyCommand('NOTIFICATIONS'));
btnRemoteLock.addEventListener('click', () => sendRemoteKeyCommand('LOCK'));

// 7. Direct P2P File Transfer Engine (RTCDataChannel)
btnToggleFileTransfer.addEventListener('click', () => {
  fileTransferModal.classList.toggle('hidden');
});
btnCloseFileTransfer.addEventListener('click', () => {
  fileTransferModal.classList.add('hidden');
});

function setupFileChannel(channel) {
  channel.binaryType = 'arraybuffer';
  channel.onmessage = (event) => {
    // Handle incoming file packets from phone
    console.log('Incoming file chunk from phone');
  };
}

filePicker.addEventListener('change', (e) => {
  const files = e.target.files;
  if (!files.length) return;

  for (let file of files) {
    sendFileP2P(file);
  }
});

function sendFileP2P(file) {
  const CHUNK_SIZE = 16384; // 16 KB chunks
  const fileId = `f_${Date.now()}_${Math.random()}`;

  // Add UI item in queue
  const queueItem = document.createElement('div');
  queueItem.className = 'file-queue-item';
  queueItem.id = fileId;
  queueItem.innerHTML = `
    <div style="display:flex; justify-content:space-between;">
      <span><strong>${file.name}</strong> (${(file.size / 1024 / 1024).toFixed(2)} MB)</span>
      <span class="file-percent">0%</span>
    </div>
    <div class="progress-bar-bg"><div class="progress-bar-fill"></div></div>
  `;
  fileQueue.prepend(queueItem);

  const fillEl = queueItem.querySelector('.progress-bar-fill');
  const percentEl = queueItem.querySelector('.file-percent');

  // Send metadata
  const meta = {
    type: 'file-meta',
    name: file.name,
    size: file.size,
    mime: file.type
  };

  if (fileChannel && fileChannel.readyState === 'open') {
    fileChannel.send(JSON.stringify(meta));
  }

  // Read and stream file in chunks
  const reader = new FileReader();
  let offset = 0;

  function readNextChunk() {
    const slice = file.slice(offset, offset + CHUNK_SIZE);
    reader.readAsArrayBuffer(slice);
  }

  reader.onload = (e) => {
    if (fileChannel && fileChannel.readyState === 'open') {
      fileChannel.send(e.target.result);
    }
    offset += e.target.result.byteLength;
    const progress = Math.min(100, Math.round((offset / file.size) * 100));
    fillEl.style.width = `${progress}%`;
    percentEl.innerText = `${progress}%`;

    if (offset < file.size) {
      // Buffer flow control
      if (fileChannel.bufferedAmount > 8 * 1024 * 1024) {
        setTimeout(readNextChunk, 20);
      } else {
        readNextChunk();
      }
    } else {
      percentEl.innerText = 'Completed';
      percentEl.style.color = 'var(--accent-green)';
    }
  };

  readNextChunk();
}

// 8. Session Live Chat
btnToggleChat.addEventListener('click', () => {
  chatDrawer.classList.toggle('hidden');
});
btnCloseChat.addEventListener('click', () => {
  chatDrawer.classList.add('hidden');
});

btnSendChat.addEventListener('click', sendChat);
chatInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') sendChat();
});

function sendChat() {
  const text = chatInput.value.trim();
  if (!text) return;

  appendChatMessage(text, 'mine');
  chatInput.value = '';

  sendSignaling({
    type: 'chat-message',
    targetId: currentTargetHostId,
    text: text
  });
}

function appendChatMessage(text, type) {
  const msgEl = document.createElement('div');
  msgEl.className = `chat-msg ${type}`;
  msgEl.innerText = text;
  chatMessages.appendChild(msgEl);
  chatMessages.scrollTop = chatMessages.scrollHeight;
}

// 9. Low Latency & FPS Monitor
function startStatsMonitor() {
  setInterval(async () => {
    if (!peerConnection) return;
    try {
      const stats = await peerConnection.getStats();
      stats.forEach((report) => {
        if (report.type === 'inbound-rtp' && report.kind === 'video') {
          if (report.framesPerSecond) {
            fpsDisplay.innerText = `${Math.round(report.framesPerSecond)} FPS`;
          }
        }
        if (report.type === 'candidate-pair' && report.state === 'succeeded') {
          if (report.currentRoundTripTime) {
            latencyDisplay.innerText = `${Math.round(report.currentRoundTripTime * 1000)} ms`;
          }
        }
      });
    } catch (e) {}
  }, 1000);
}

// 10. Disconnect Session
btnDisconnect.addEventListener('click', () => {
  if (confirm('Are you sure you want to end this remote session?')) {
    if (peerConnection) {
      peerConnection.close();
      peerConnection = null;
    }
    sessionSection.classList.add('hidden');
    connectSection.classList.remove('hidden');
    document.getElementById('btnConnect').disabled = false;
    document.getElementById('btnConnect').innerHTML = '<i class="fa-solid fa-link"></i> Connect Now';
  }
});

// APK Modal Toggle
const btnShowApkModal = document.getElementById('btnShowApkModal');
const apkDownloadModal = document.getElementById('apkDownloadModal');
const btnCloseApkModal = document.getElementById('btnCloseApkModal');

if (btnShowApkModal && apkDownloadModal && btnCloseApkModal) {
  btnShowApkModal.addEventListener('click', (e) => {
    e.preventDefault();
    apkDownloadModal.classList.remove('hidden');
  });
  btnCloseApkModal.addEventListener('click', () => {
    apkDownloadModal.classList.add('hidden');
  });
}

// Start WebSocket on Load
initWebSocket();
