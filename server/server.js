const express = require('express');
const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');
const path = require('path');
const cors = require('cors');

const PORT = process.env.PORT || 8080;
const app = express();
app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

// Store registered devices: peerId -> { ws, role, pin, deviceName, unattendedHash, createdAt, lastSeen }
const sessions = new Map();

// Helper to generate a 9-digit clean formatted ID: e.g. "842 190 341"
function generateCleanId() {
  let id;
  do {
    const raw = Math.floor(100000000 + Math.random() * 900000000).toString();
    id = `${raw.slice(0, 3)} ${raw.slice(3, 6)} ${raw.slice(6, 9)}`;
  } while (sessions.has(id.replace(/\s/g, '')));
  return id;
}

// Public WebRTC ICE Servers (Google Public STUN + optional TURN)
const defaultIceServers = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
  { urls: 'stun:stun2.l.google.com:19302' },
  { urls: 'stun:stun3.l.google.com:19302' },
  { urls: 'stun:stun4.l.google.com:19302' }
];

// Health Check API
app.get('/api/health', (req, res) => {
  res.json({
    status: 'online',
    activeSessions: sessions.size,
    timestamp: new Date().toISOString()
  });
});

// Get ICE Server configurations (can be overridden with TURN in production)
app.get('/api/ice-servers', (req, res) => {
  res.json({
    iceServers: defaultIceServers
  });
});

// WebSocket Signaling Handler
wss.on('connection', (ws, req) => {
  let boundId = null;
  let boundRole = null; // 'HOST' (screen sharer/target) or 'CONTROLLER' (viewer/controller)

  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', (messageText) => {
    try {
      const msg = JSON.parse(messageText);

      switch (msg.type) {
        // 1. Host registers its 9-digit ID and temporary PIN or unattended password
        case 'register-host': {
          const rawId = (msg.id || generateCleanId()).replace(/\s/g, '');
          boundId = rawId;
          boundRole = 'HOST';

          sessions.set(rawId, {
            ws,
            role: 'HOST',
            pin: msg.pin || Math.floor(1000 + Math.random() * 9000).toString(),
            deviceName: msg.deviceName || 'Android Device',
            unattendedPassword: msg.unattendedPassword || null,
            allowRemoteControl: msg.allowRemoteControl ?? true,
            allowFileTransfer: msg.allowFileTransfer ?? true,
            lastSeen: Date.now()
          });

          const current = sessions.get(rawId);
          ws.send(JSON.stringify({
            type: 'registered',
            id: rawId,
            pin: current.pin,
            iceServers: defaultIceServers
          }));
          console.log(`[Host Registered] ID: ${rawId} (${current.deviceName})`);
          break;
        }

        // 2. Controller requests to connect to a target Host ID
        case 'connect-request': {
          const targetId = (msg.targetId || '').replace(/\s/g, '');
          boundRole = 'CONTROLLER';

          const host = sessions.get(targetId);
          if (!host || host.ws.readyState !== WebSocket.OPEN) {
            ws.send(JSON.stringify({
              type: 'connect-error',
              reason: 'Device is offline or invalid ID'
            }));
            return;
          }

          // Check PIN or unattended password
          const isPinValid = msg.pin && msg.pin === host.pin;
          const isPasswordValid = msg.password && host.unattendedPassword && msg.password === host.unattendedPassword;

          if (!isPinValid && !isPasswordValid) {
            ws.send(JSON.stringify({
              type: 'connect-error',
              reason: 'Incorrect Security PIN or Master Password'
            }));
            return;
          }

          // Pair the session
          boundId = `controller_${Date.now()}`;
          sessions.set(boundId, { ws, role: 'CONTROLLER', targetHostId: targetId });

          // Notify Host that controller is asking for session
          host.ws.send(JSON.stringify({
            type: 'incoming-connection',
            controllerId: boundId,
            controllerName: msg.clientName || 'Remote Controller'
          }));

          // Notify Controller of success
          ws.send(JSON.stringify({
            type: 'connect-accepted',
            targetHostId: targetId,
            deviceName: host.deviceName,
            allowRemoteControl: host.allowRemoteControl,
            allowFileTransfer: host.allowFileTransfer,
            iceServers: defaultIceServers
          }));
          console.log(`[Session Established] Controller -> Host ${targetId}`);
          break;
        }

        // 3. WebRTC SDP Offer / Answer Relay
        case 'offer':
        case 'answer': {
          const target = sessions.get((msg.targetId || '').replace(/\s/g, ''));
          if (target && target.ws.readyState === WebSocket.OPEN) {
            target.ws.send(JSON.stringify({
              type: msg.type,
              sdp: msg.sdp,
              fromId: boundId
            }));
          }
          break;
        }

        // 4. WebRTC Trickle ICE Candidate Relay
        case 'ice-candidate': {
          const target = sessions.get((msg.targetId || '').replace(/\s/g, ''));
          if (target && target.ws.readyState === WebSocket.OPEN) {
            target.ws.send(JSON.stringify({
              type: 'ice-candidate',
              candidate: msg.candidate,
              fromId: boundId
            }));
          }
          break;
        }

        // 5. Fallback signaling channel for Commands (Touch, Key, Clipboard, File)
        case 'remote-input':
        case 'remote-command':
        case 'file-meta':
        case 'file-chunk': {
          const target = sessions.get((msg.targetId || '').replace(/\s/g, ''));
          if (target && target.ws.readyState === WebSocket.OPEN) {
            target.ws.send(JSON.stringify({
              ...msg,
              fromId: boundId
            }));
          }
          break;
        }

        // 6. Ping keep-alive
        case 'ping': {
          ws.send(JSON.stringify({ type: 'pong' }));
          break;
        }

        default:
          console.warn('Unknown message type:', msg.type);
      }
    } catch (err) {
      console.error('Error handling WebSocket message:', err);
    }
  });

  ws.on('close', () => {
    if (boundId && sessions.has(boundId)) {
      const sess = sessions.get(boundId);
      if (sess.role === 'HOST') {
        console.log(`[Host Disconnected] ID: ${boundId}`);
      }
      sessions.delete(boundId);
    }
  });

  ws.on('error', (err) => {
    console.error('WebSocket client error:', err);
  });
});

// Ping interval every 25 seconds to keep WebSockets alive through cloud NAT/firewalls
const interval = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (ws.isAlive === false) return ws.terminate();
    ws.isAlive = false;
    ws.ping();
  });
}, 25000);

wss.on('close', () => {
  clearInterval(interval);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`=======================================================`);
  console.log(`🚀 OmniLink Remote All-in-One Server Running!`);
  console.log(`📡 URL: http://localhost:${PORT}`);
  console.log(`🔌 WebSocket: ws://localhost:${PORT}/ws`);
  console.log(`🌐 Web Controller client is ready in /public`);
  console.log(`=======================================================`);
});
