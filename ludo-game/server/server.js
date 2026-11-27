'use strict';

const path = require('path');
const os = require('os');
const http = require('http');
const express = require('express');
const { Server } = require('socket.io');
const game = require('./game');

const PORT = process.env.PORT || 3000;

const app = express();
app.use(express.static(path.join(__dirname, '..', 'client')));

const server = http.createServer(app);
const io = new Server(server);

// ---------------------------------------------------------------- rooms

const rooms = new Map(); // code -> room

function cleanCode(code) {
  return String(code || '').trim().toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 4);
}

function genCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code;
  do {
    code = '';
    for (let i = 0; i < 4; i++) code += chars[Math.floor(Math.random() * chars.length)];
  } while (rooms.has(code));
  return code;
}

function roomPlayers(room) {
  return room.players.map((p) => ({ color: p.color, name: p.name, connected: p.connected }));
}

function emitToRoom(room, event, payload) {
  room.players.forEach((p) => {
    const sock = io.sockets.sockets.get(p.socketId);
    if (sock) sock.emit(event, payload);
  });
}

function sendState(room) {
  if (!room.game) return;
  room.players.forEach((p) => {
    const sock = io.sockets.sockets.get(p.socketId);
    if (sock) {
      sock.emit('gameState', {
        state: game.snapshot(room.game),
        yourColor: p.color
      });
    }
  });
}

function startGame(room) {
  const colors = ['red', 'yellow'];
  const names = {};
  room.players.forEach((p, i) => {
    names[colors[i]] = p.name;
  });
  const starter = room.rematches % 2 === 0 ? 'red' : 'yellow';
  room.game = game.newGame(names.red, names.yellow, starter);
  room.abandoned = false;
  sendState(room);
}

// ---------------------------------------------------------------- socket

io.on('connection', (socket) => {
  socket.data.roomCode = null;

  socket.on('createRoom', (payload, cb) => {
    const name = String((payload && payload.name) || '').slice(0, 16) || 'Player 1';
    const code = genCode();
    const room = {
      code,
      players: [{ color: 'red', name, socketId: socket.id, connected: true }],
      game: null,
      rematches: 0,
      abandoned: false,
      lastActive: Date.now()
    };
    rooms.set(code, room);
    socket.data.roomCode = code;
    socket.join(code);
    if (typeof cb === 'function') cb({ ok: true, code, color: 'red' });
  });

  socket.on('joinRoom', (payload, cb) => {
    const code = cleanCode(payload && payload.code);
    const name = String((payload && payload.name) || '').slice(0, 16) || 'Player 2';
    const room = rooms.get(code);

    if (!room) return reply(cb, { ok: false, error: 'Room not found' });
    if (room.players.length >= 2) {
      // Allow a same-name player to re-attach (reload / reconnect).
      const existing = room.players.find((p) => p.name === name && !p.connected);
      if (!existing) return reply(cb, { ok: false, error: 'Room is full' });
      existing.socketId = socket.id;
      existing.connected = true;
      socket.data.roomCode = code;
      socket.join(code);
      room.lastActive = Date.now();
      if (room.game) {
        reply(cb, { ok: true, code, color: existing.color });
        emitToRoom(room, 'roomUpdate', { players: roomPlayers(room), started: !!room.game });
        sendState(room);
      } else {
        reply(cb, { ok: true, code, color: existing.color });
        emitToRoom(room, 'roomUpdate', { players: roomPlayers(room), started: !!room.game });
      }
      return;
    }

    room.players.push({ color: 'yellow', name, socketId: socket.id, connected: true });
    socket.data.roomCode = code;
    socket.join(code);
    room.lastActive = Date.now();
    reply(cb, { ok: true, code, color: room.players.length === 1 ? 'red' : 'yellow' });

    if (room.players.length === 2) {
      startGame(room);
    } else {
      emitToRoom(room, 'roomUpdate', { players: roomPlayers(room), started: false });
    }
  });

  socket.on('rollDice', (payload, cb) => {
    const room = currentRoom(socket);
    if (!room || !room.game) return reply(cb, { ok: false, error: 'No game' });
    const color = colorFor(socket, room);
    const res = game.rollDice(room.game, color);
    room.lastActive = Date.now();
    if (typeof cb === 'function') cb(res);
    sendState(room);
  });

  socket.on('movePiece', (payload, cb) => {
    const room = currentRoom(socket);
    if (!room || !room.game) return reply(cb, { ok: false, error: 'No game' });
    const color = colorFor(socket, room);
    const slot = Number((payload && payload.slot) !== undefined ? payload.slot : -1);
    const res = game.movePiece(room.game, color, slot);
    room.lastActive = Date.now();
    if (typeof cb === 'function') cb(res);
    sendState(room);
    if (room.game.winner) emitToRoom(room, 'gameOver', { winner: room.game.winner });
  });

  socket.on('chat', (payload) => {
    const room = currentRoom(socket);
    if (!room) return;
    const color = colorFor(socket, room);
    const text = String((payload && payload.text) || '').slice(0, 120);
    if (!text.trim()) return;
    const msg = { name: room.players.find((p) => p.socketId === socket.id).name, text: text.trim(), ts: Date.now() };
    if (room.game) room.game.chat.push(msg);
    emitToRoom(room, 'chat', msg);
  });

  socket.on('rematch', (payload, cb) => {
    const room = currentRoom(socket);
    if (!room) return reply(cb, { ok: false });
    room.rematches += 1;
    room.lastActive = Date.now();
    startGame(room);
    if (typeof cb === 'function') cb({ ok: true });
  });

  socket.on('leaveRoom', () => {
    leaveRoom(socket);
  });

  socket.on('disconnect', () => {
    scheduleLeave(socket);
  });
});

// ---------------------------------------------------------------- utils

function reply(cb, data) {
  if (typeof cb === 'function') cb(data);
}

function currentRoom(socket) {
  const code = socket.data.roomCode;
  return code ? rooms.get(code) : null;
}

function colorFor(socket, room) {
  const p = room.players.find((x) => x.socketId === socket.id);
  return p ? p.color : null;
}

function leaveRoom(socket) {
  const room = currentRoom(socket);
  if (!room) return;
  const idx = room.players.findIndex((p) => p.socketId === socket.id);
  if (idx === -1) return;
  const [leaver] = room.players.splice(idx, 1);
  socket.leave(room.code);
  socket.data.roomCode = null;

  room.lastActive = Date.now();
  if (room.players.length === 0) {
    rooms.delete(room.code);
    return;
  }
  if (room.game) {
    room.abandoned = true;
    emitToRoom(room, 'opponentLeft', { name: leaver.name, code: room.code });
  } else {
    emitToRoom(room, 'roomUpdate', { players: roomPlayers(room), started: false });
  }
}

// Give a brief grace period on transport drop so page reloads can re-join.
function scheduleLeave(socket) {
  const room = currentRoom(socket);
  if (!room) return;
  const me = room.players.find((p) => p.socketId === socket.id);
  if (me) me.connected = false;

  setTimeout(() => {
    const still = currentRoom(socket);
    if (!still) return;
    const p = still.players.find((x) => x.socketId === socket.id);
    if (p && !p.connected) leaveRoom(socket);
  }, 20000);

  if (room.game) emitToRoom(room, 'opponentPaused', {});
}

// ---------------------------------------------------------------- gc

setInterval(() => {
  const now = Date.now();
  rooms.forEach((room, code) => {
    const empty = room.players.length === 0;
    const stale = now - room.lastActive > 30 * 60 * 1000;
    if (empty || stale) rooms.delete(code);
  });
}, 10 * 60 * 1000).unref();

// ---------------------------------------------------------------- start

server.listen(PORT, '0.0.0.0', () => {
  console.log('  Ludo multiplayer server running');
  console.log(`  Local:    http://localhost:${PORT}`);
  const nets = os.networkInterfaces();
  Object.keys(nets).forEach((name) => {
    nets[name].forEach((n) => {
      if (n.family === 'IPv4' && !n.internal) {
        console.log(`  Network:  http://${n.address}:${PORT}`);
      }
    });
  });
  console.log('');
  console.log('  For play across different networks (the internet):');
  console.log('    - Run this on a VPS, or forward port ' + PORT + ' on your router, or');
  console.log('    - Tunnel with ngrok:  ngrok http ' + PORT + '  (each player opens the ngrok URL)');
  console.log('');
});