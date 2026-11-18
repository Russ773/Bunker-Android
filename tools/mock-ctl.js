#!/usr/bin/env node
// Sends one command to the mock Rust server and prints its reply. Handy for the mock's test controls:
//
//   node tools/mock-ctl.js "mock.fps 10"            force server FPS (mock.fps off to go back to random)
//   node tools/mock-ctl.js "mock.drop"              60% of the players vanish at once
//   node tools/mock-ctl.js "mock.restart"           the server's uptime starts over
//   node tools/mock-ctl.js "mock.leave Ashen"       a player leaves
//   node tools/mock-ctl.js "mock.join Ashen"        ...and comes back with the same Steam ID
//
// Options: --port 28016  --password test   (needs Node 22+ for the built-in WebSocket)

const arg = (name, def) => { const i = process.argv.indexOf('--' + name); return i > 0 ? process.argv[i + 1] : def; };
const port = arg('port', '28016');
const password = arg('password', 'test');
const skip = new Set(['--port', port, '--password', password]);
const command = process.argv.slice(2).filter(a => !skip.has(a)).join(' ');
if (!command) { console.error('Usage: node tools/mock-ctl.js [--port 28016] [--password test] "<command>"'); process.exit(2); }

const ws = new WebSocket(`ws://127.0.0.1:${port}/${encodeURIComponent(password)}`);
const timer = setTimeout(() => { console.error('No answer'); process.exit(1); }, 5000);
ws.onopen = () => ws.send(JSON.stringify({ Identifier: 4242, Message: command, Name: 'mock-ctl' }));
ws.onmessage = (e) => {
  const m = JSON.parse(e.data);
  if (m.Identifier === 4242) { console.log(m.Message); clearTimeout(timer); ws.close(); }
};
ws.onerror = () => { console.error('Could not reach the mock server on port ' + port); process.exit(1); };
