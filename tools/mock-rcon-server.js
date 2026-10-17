#!/usr/bin/env node
// A pretend Rust WebRCON server for testing Bunker without a real game server. No dependencies.
//
//   node tools/mock-rcon-server.js [--port 28016] [--password test] [--noplugin] [--fast]
//
// From the Android emulator the host PC is 10.0.2.2, so add a server with address 10.0.2.2, port 28016, password "test".
// It answers serverinfo / playerlist / banlistex and common admin commands, sends console noise and chat,
// and makes players come and go. Connecting with the wrong password gets the socket closed at once, like Rust does.

const http = require('http');
const crypto = require('crypto');

const arg = (name, def) => { const i = process.argv.indexOf('--' + name); return i > 0 ? process.argv[i + 1] : def; };
const flag = (name) => process.argv.includes('--' + name);
const PORT = parseInt(arg('port', '28016'), 10);
const PASSWORD = arg('password', 'test');
const NO_PLUGIN = flag('noplugin');
const FAST = flag('fast');

const started = Date.now();
const names = ['Scrapper', 'Nomad_77', 'LootGoblin', 'xX_Raider_Xx', 'Wolfie', 'BaseBuilder', 'Sulfur Sam', 'OilRigOllie', 'Medic', 'Zed', 'Pixel', 'Hatchet Harry'];
const colours = ['#5af', '#fa5', '#af5', '#f5a', '#a5f', '#5fa', '#ddd'];
let nextSteam = 76561198000000100n;
const players = [];
const bans = [{ id: '76561198012345678', name: 'Griefer9000', reason: 'Griefing', expiry: -1 }, { id: '76561198087654321', name: 'SpeedyG', reason: 'Speed hack', expiry: Math.floor(Date.now() / 1000) + 86400 * 3 }];
let maxPlayers = 100;
let timeHour = 12;
const clients = new Set();

function addPlayer(name) {
  const p = { SteamID: String(nextSteam++), DisplayName: name || names[Math.floor(Math.random() * names.length)] + (Math.random() < 0.5 ? '' : Math.floor(Math.random() * 99)), Ping: 20 + Math.floor(Math.random() * 120), Address: `82.${rand(255)}.${rand(255)}.${rand(255)}:${40000 + rand(20000)}`, ConnectedSeconds: 0, Health: 40 + Math.random() * 60, joined: Date.now(), colour: colours[rand(colours.length)] };
  players.push(p);
  return p;
}
function rand(n) { return Math.floor(Math.random() * n); }
for (let i = 0; i < 6; i++) { const p = addPlayer(); p.joined -= rand(7200) * 1000; }

function serverInfo() {
  return JSON.stringify({
    Hostname: 'Bunker Test Server [PVE x2]', MaxPlayers: maxPlayers, Players: players.length, Queued: 0, Joining: rand(3) === 0 ? 1 : 0,
    EntityCount: 180000 + rand(2000), GameTime: `${String(timeHour).padStart(2, '0')}:00:00`, Uptime: Math.floor((Date.now() - started) / 1000) + 86400 * 2 + 5000,
    Map: 'Procedural Map', Framerate: Math.round((30 + Math.random() * 28) * 10) / 10, Memory: 6200 + rand(300), MemoryUsageSystem: 8400, Collections: 12, NetworkIn: 100, NetworkOut: 200, Restarting: false, SaveCreatedTime: new Date().toISOString(),
  });
}
function playerList() {
  return JSON.stringify(players.map(p => ({ SteamID: p.SteamID, OwnerSteamID: '0', DisplayName: p.DisplayName, Ping: p.Ping + rand(10) - 5, Address: p.Address, ConnectedSeconds: Math.floor((Date.now() - p.joined) / 1000), VoiationLevel: 0, CurrentLevel: 0, UnspentXp: 0, Health: p.Health })));
}
const findPlayer = (id) => players.find(p => p.SteamID === id);

// ----------------------------------------------------------------------------------------------- commands
function tokenise(line) {
  const out = []; const re = /"([^"]*)"|(\S+)/g; let m;
  while ((m = re.exec(line))) out.push(m[1] !== undefined ? m[1] : m[2]);
  return out;
}
function chat(name, id, text, colour, channel = 0) {
  broadcast({ Identifier: -1, Type: 'Chat', Stacktrace: '', Message: JSON.stringify({ Channel: channel, Message: text, UserId: id, Username: name, Color: colour, Time: Math.floor(Date.now() / 1000) }) });
}
function log(text, type = 'Generic') { broadcast({ Identifier: -1, Type: type, Stacktrace: '', Message: text }); }

function run(line) {
  const [cmd, ...a] = tokenise(line);
  switch ((cmd || '').toLowerCase()) {
    case 'serverinfo': return serverInfo();
    case 'playerlist': return playerList();
    case 'say': chat('SERVER', '0', a.join(' '), '#eee'); return '';
    case 'bunker.say':
      if (NO_PLUGIN) return 'Unknown command: bunker.say';
      chat(a[1], a[0], a[3], a[2]); return 'sent';
    case 'kick': { const p = findPlayer(a[0]); if (!p) return `Player not found: ${a[0]}`; players.splice(players.indexOf(p), 1); log(`${p.SteamID}/${p.DisplayName} kicked: ${a[1] || ''}`); return `Kicked: ${p.DisplayName}`; }
    case 'banid': bans.push({ id: a[0], name: a[1] || '', reason: a[2] || '', expiry: -1 }); { const p = findPlayer(a[0]); if (p) players.splice(players.indexOf(p), 1); } return `Banned: ${a[0]}`;
    case 'unban': { const i = bans.findIndex(b => b.id === a[0]); if (i < 0) return `User ${a[0]} isn't banned`; bans.splice(i, 1); return `Unbanned User: ${a[0]}`; }
    case 'banlistex': return bans.map((b, i) => `${i + 1} ${b.id} "${b.name}" "${b.reason}" ${b.expiry}`).join('\n');
    case 'mute': return `Muted ${a[0]}`;
    case 'unmute': return `Unmuted ${a[0]}`;
    case 'teleport': return `Teleported ${a[0]} to ${a[1]}`;
    case 'inventory.giveto': return `Gave ${a[0]} ${a[2]} x ${a[1]}`;
    case 'inventory.giveall': return `Gave everyone ${a[1]} x ${a[0]}`;
    case 'server.save': log('Saving complete'); return 'Saved';
    case 'server.writecfg': return 'Config Saved';
    case 'env.time': timeHour = parseFloat(a[0]); return `env.time: "${a[0]}"`;
    case 'restart': log(`Restarting in ${a[0]} seconds`, 'Warning'); return `Restarting in ${a[0]}s`;
    case 'moderatorid': case 'ownerid': return `Added ${cmd}: ${a[0]}`;
    case 'removemoderator': case 'removeowner': return `Removed: ${a[0]}`;
    case 'status': return `hostname: Bunker Test Server\nversion : 2500 secure\nmap     : Procedural Map\nplayers : ${players.length} (${maxPlayers} max)`;
    case 'echo': return a.join(' ');
    default: return `Unknown command: ${cmd}`;
  }
}

// ----------------------------------------------------------------------------------------------- websocket plumbing
function frame(opcode, payload) {
  const len = payload.length; let header;
  if (len < 126) header = Buffer.from([0x80 | opcode, len]);
  else if (len < 65536) { header = Buffer.alloc(4); header[0] = 0x80 | opcode; header[1] = 126; header.writeUInt16BE(len, 2); }
  else { header = Buffer.alloc(10); header[0] = 0x80 | opcode; header[1] = 127; header.writeBigUInt64BE(BigInt(len), 2); }
  return Buffer.concat([header, payload]);
}
function send(socket, obj) { if (!socket.destroyed) socket.write(frame(1, Buffer.from(JSON.stringify(obj)))); }
function broadcast(obj) { for (const c of clients) send(c, obj); }

const server = http.createServer((req, res) => { res.writeHead(426); res.end('Upgrade required'); });
server.on('upgrade', (req, socket) => {
  const key = req.headers['sec-websocket-key'];
  const accept = crypto.createHash('sha1').update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
  socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' + accept + '\r\n\r\n');
  const pw = decodeURIComponent((req.url || '/').slice(1));
  if (pw !== PASSWORD) { console.log(`[${new Date().toLocaleTimeString()}] wrong password "${pw}" - closing`); socket.write(frame(8, Buffer.from([0x03, 0xe8]))); socket.end(); return; }
  console.log(`[${new Date().toLocaleTimeString()}] client connected (${clients.size + 1})`);
  clients.add(socket);
  send(socket, { Identifier: -1, Type: 'Generic', Stacktrace: '', Message: 'Welcome to the Bunker mock server' });

  let buf = Buffer.alloc(0);
  socket.on('data', (chunk) => {
    buf = Buffer.concat([buf, chunk]);
    for (;;) {
      if (buf.length < 2) return;
      const op = buf[0] & 0x0f; const masked = (buf[1] & 0x80) !== 0; let len = buf[1] & 0x7f; let off = 2;
      if (len === 126) { if (buf.length < 4) return; len = buf.readUInt16BE(2); off = 4; }
      else if (len === 127) { if (buf.length < 10) return; len = Number(buf.readBigUInt64BE(2)); off = 10; }
      if (buf.length < off + (masked ? 4 : 0) + len) return;
      let payload;
      if (masked) { const mask = buf.slice(off, off + 4); payload = Buffer.from(buf.slice(off + 4, off + 4 + len)); for (let i = 0; i < len; i++) payload[i] ^= mask[i % 4]; off += 4; }
      else payload = buf.slice(off, off + len);
      buf = buf.slice(off + len);
      if (op === 8) { socket.end(frame(8, Buffer.alloc(0))); return; }
      if (op === 9) { socket.write(frame(10, payload)); continue; }
      if (op === 1) {
        try {
          const msg = JSON.parse(payload.toString('utf8'));
          const reply = run(String(msg.Message || ''));
          console.log(`  > ${msg.Message}`);
          send(socket, { Identifier: msg.Identifier, Type: 'Generic', Stacktrace: '', Message: reply });
        } catch (e) { console.log('bad message', e.message); }
      }
    }
  });
  const gone = () => { if (clients.delete(socket)) console.log(`[${new Date().toLocaleTimeString()}] client left (${clients.size})`); };
  socket.on('close', gone); socket.on('error', gone);
});

// ----------------------------------------------------------------------------------------------- pretend life
const noise = [
  ['Generic', '[Economics] Saving balances for 214 players'],
  ['Generic', '[Economics] Saving balances for 214 players'],
  ['Generic', '[Steam Groups] Refreshing group membership'],
  ['Generic', 'Saving 183204 ents took 0.42 seconds'],
  ['Generic', '[ServerRewards] Daily reward claimed by a player'],
  ['Warning', '[Raidable Bases] Failed to find a spawn point, retrying'],
  ['Warning', 'Calling ent.ServerInit took 13ms'],
  ['Error', '[Kits] Failed to run hook OnPlayerRespawned: NullReferenceException: Object reference not set'],
  ['Generic', 'Network: client count changed'],
];
const every = (ms, fn) => setInterval(fn, FAST ? Math.max(500, ms / 5) : ms);
every(2500, () => { const [t, m] = noise[rand(noise.length)]; log(m, t); });
every(9000, () => {
  if (players.length < maxPlayers && (Math.random() < 0.55 || players.length < 3)) { const p = addPlayer(); log(`${p.Address}/${p.SteamID}/${p.DisplayName} joined [windows/${p.SteamID}]`); }
  else if (players.length > 2) { const p = players.splice(rand(players.length), 1)[0]; log(`${p.Address}/${p.SteamID}/${p.DisplayName} disconnecting: disconnect`); }
});
const lines = ['anyone want to trade?', 'who is raiding me', 'GG', 'free guns at outpost', 'how do I craft a tea?', 'lol', 'server is lagging a bit', 'any admins on?', 'nice base'];
every(7000, () => {
  if (!players.length) return; const p = players[rand(players.length)];
  const team = Math.random() < 0.15; const tag = Math.random() < 0.2 ? '[VIP] ' : '';
  chat(p.DisplayName, p.SteamID, tag ? `${tag}${p.DisplayName}: ${lines[rand(lines.length)]}` : lines[rand(lines.length)], p.colour, team ? 1 : 0);
});
every(4000, () => { for (const p of players) { p.Ping = Math.max(8, Math.min(320, p.Ping + rand(21) - 10)); p.Health = Math.max(5, Math.min(100, p.Health + rand(15) - 7)); } });

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Mock Rust WebRCON server on port ${PORT}, password "${PASSWORD}"${NO_PLUGIN ? ' (BunkerChat plugin missing)' : ''}`);
  console.log('From the Android emulator use address 10.0.2.2.');
});
