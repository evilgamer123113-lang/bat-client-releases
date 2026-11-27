'use strict';

/* =====================================================================
 * Ludo client — canvas board renderer + socket wiring
 * ===================================================================== */

const R = window.LudoRules;

const CELL = 40;

const COLORS = {
  red: '#e53935',
  yellow: '#fdd835',
  green: '#43a047',
  blue: '#1e88e5'
};
const TINTS = {
  red: '#ef5350',
  yellow: '#ffee58',
  green: '#66bb6a',
  blue: '#42a5f5'
};

// Each player owns 13 consecutive path cells (52 / 4) — the full colored loop.
const SEGMENT = {};
R.COLORS.forEach((color) => {
  SEGMENT[color] = [];
  for (let i = 0; i < 13; i++) SEGMENT[color].push((R.START_INDEX[color] + i) % 52);
});

const BASE_RECT = {
  red: [9, 9],
  yellow: [0, 0],
  green: [0, 9],
  blue: [9, 0]
};

// ---------------------------------------------------------------- state

let socket = null;
let myColor = null;
let state = null;          // latest server snapshot
let roomCode = null;
let waiting = false;
let gameActive = false;
let lastRollTs = 0;
let dieAnim = null;

const ui = {
  landing: document.getElementById('landing'),
  waiting: document.getElementById('waiting'),
  game: document.getElementById('game'),
  nameInput: document.getElementById('nameInput'),
  joinCode: document.getElementById('joinCode'),
  createBtn: document.getElementById('createBtn'),
  joinBtn: document.getElementById('joinBtn'),
  landingError: document.getElementById('landingError'),
  connStatus: document.getElementById('connStatus'),
  roomCode: document.getElementById('roomCode'),
  copyLinkBtn: document.getElementById('copyLinkBtn'),
  cancelRoomBtn: document.getElementById('cancelRoomBtn'),
  waitStatus: document.getElementById('waitStatus'),
  meTag: document.getElementById('meTag'),
  oppTag: document.getElementById('oppTag'),
  turnBanner: document.getElementById('turnBanner'),
  rollBtn: document.getElementById('rollBtn'),
  lastMove: document.getElementById('lastMove'),
  log: document.getElementById('log'),
  chatInput: document.getElementById('chatInput'),
  rematchBtn: document.getElementById('rematchBtn'),
  leaveBtn: document.getElementById('leaveBtn'),
  die: document.getElementById('die'),
  board: document.getElementById('board'),
  toast: document.getElementById('toast')
};

const dieCtx = ui.die.getContext('2d');
const boardCtx = ui.board.getContext('2d');

// ---------------------------------------------------------------- socket

function connect() {
  socket = io({ transports: ['websocket', 'polling'] });

  socket.on('connect', () => { setConn(''); });
  socket.on('disconnect', () => setConn('Connection to the server lost — reconnecting…'));
  socket.on('connect_error', () => setConn('Cannot reach the game server. Is it running? (cd server; npm start)'));
  socket.on('reconnect', () => setConn(''));

  socket.on('roomUpdate', (data) => {
    if (waiting) {
      ui.waitStatus.textContent = 'Waiting for opponent…';
    }
  });

  socket.on('gameState', (data) => {
    state = data.state;
    myColor = data.yourColor;

    if (state.lastRoll && state.lastRoll.ts !== lastRollTs) {
      lastRollTs = state.lastRoll.ts;
      stopDieAnim();
      renderDie(state.lastRoll.value);
    }
    gameActive = true;
    waiting = false;
    showScreen('game');
    renderAll();
  });

  socket.on('chat', (msg) => renderChatMsg(msg));
  socket.on('gameOver', (data) => { /* turn banner handles winner */ });

  socket.on('opponentPaused', () => {
    toast('Opponent disconnected — waiting for them to return…');
  });

  socket.on('opponentLeft', (data) => {
    toast(`Opponent (${data ? data.name : ''}) left the game`);
    clearSavedRoom();
    setTimeout(() => {
      gameActive = false;
      showScreen('landing');
      state = null;
    }, 1800);
  });
}

function currentName() {
  return ui.nameInput.value.trim() || 'Anonymous';
}

function saveRoom() {
  try {
    localStorage.setItem('ludo_room', JSON.stringify({ code: roomCode, name: currentName() }));
  } catch (e) { /* ignore */ }
}

function clearSavedRoom() {
  try { localStorage.removeItem('ludo_room'); } catch (e) { /* ignore */ }
}

// ---------------------------------------------------------------- screens

function showScreen(name) {
  ui.landing.classList.toggle('hidden', name !== 'landing');
  ui.waiting.classList.toggle('hidden', name !== 'waiting');
  ui.game.classList.toggle('hidden', name !== 'game');
}

// ---------------------------------------------------------------- board render

function renderAll() {
  if (!state) return;
  renderBoard();
  renderDie(false);
  updateUi();
}

function updateUi() {
  const me = state.players[myColor];
  const oppColor = myColor === 'red' ? 'yellow' : 'red';
  const opp = state.players[oppColor];

  ui.meTag.innerHTML = `<span class="dot" style="background:${COLORS[myColor]}"></span>${esc(me.name)} (you)`;
  ui.oppTag.innerHTML = `<span class="dot" style="background:${COLORS[oppColor]}"></span>${esc(opp.name)}`;

  let banner;
  const yourTurn = state.winner ? false : state.turn === myColor;
  if (state.winner) {
    const w = state.players[state.winner];
    const you = state.winner === myColor ? 'You' : w.name;
    banner = `🏆 ${you} win${state.winner === myColor ? '' : 's'}!`;
  } else if (yourTurn) {
    banner = state.pendingMove ? 'Pick a piece to move' : 'Your turn — roll!';
  } else {
    banner = `${opp.name}'s turn…`;
  }
  ui.turnBanner.textContent = banner;

  const canRoll = yourTurn && !state.pendingMove;
  ui.rollBtn.classList.toggle('hidden', !canRoll);
  ui.rollBtn.disabled = gameActive && !canRoll;
  ui.rollBtn.classList.toggle('disabled-rolling', false);

  ui.lastMove.textContent = state.message || '';

  ui.rematchBtn.classList.toggle('hidden', !state.winner);
  ui.log.innerHTML = '';
  (state.log || []).forEach((entry) => appendLogLine(entry));
}

function renderBoard() {
  var ctx = boardCtx;
  ctx.clearRect(0, 0, 600, 600);

  // cream board background
  ctx.fillStyle = '#f5f0e0';
  ctx.fillRect(0, 0, 600, 600);

  // cross arms background cells
  for (var r = 0; r < 15; r++) {
    for (var c = 0; c < 15; c++) {
      var inBase = (r < 6 && c < 6) || (r < 6 && c > 8) || (r > 8 && c < 6) || (r > 8 && c > 8);
      if (!inBase) cellFill(r, c, '#ffffff', '#d8cfa8', 1);
    }
  }

  // 4 bases
  ['yellow', 'green', 'blue', 'red'].forEach(function (c) { drawBase(ctx, c); });

  // full colored loop — each player owns 13 consecutive path cells
  R.PATH.forEach(function (cell, idx) {
    cellFill(cell[0], cell[1], TINTS[segmentOwner(idx)], '#ffffff', 1.2);
  });

  // start squares — full color + white star
  R.COLORS.forEach(function (color) {
    var s = R.PATH[R.START_INDEX[color]];
    cellFill(s[0], s[1], COLORS[color], '#ffffff', 1.2);
    drawStar(ctx, s[1] * CELL + CELL / 2, s[0] * CELL + CELL / 2, 10);
    ctx.fillStyle = '#fff';
    ctx.fill();
  });

  // home-entry cells — full player color (entrance to the home column)
  R.COLORS.forEach(function (color) {
    var e = R.PLAYER[color].entryCell;
    cellFill(e[0], e[1], COLORS[color], '#ffffff', 1.2);
  });

  // home columns — full color cells with a white arrow toward the center
  R.COLORS.forEach(function (color) {
    R.PLAYER[color].homeCells.forEach(function (cell) {
      cellFill(cell[0], cell[1], COLORS[color], '#ffffff', 1.2);
      drawHomeArrow(ctx, cell[1] * CELL + CELL / 2, cell[0] * CELL + CELL / 2);
    });
  });

  // center — 4 large colored triangles filling the entire 3×3 block
  var ccx = 7 * CELL + CELL / 2;
  var ccy = 7 * CELL + CELL / 2;
  var cL = 6 * CELL;
  var cR = 9 * CELL;
  var cT = 6 * CELL;
  var cB = 9 * CELL;

  ctx.fillStyle = COLORS.yellow;
  ctx.beginPath(); ctx.moveTo(cL, cT); ctx.lineTo(cL, cB); ctx.lineTo(ccx, ccy); ctx.closePath(); ctx.fill();

  ctx.fillStyle = COLORS.green;
  ctx.beginPath(); ctx.moveTo(cL, cT); ctx.lineTo(cR, cT); ctx.lineTo(ccx, ccy); ctx.closePath(); ctx.fill();

  ctx.fillStyle = COLORS.red;
  ctx.beginPath(); ctx.moveTo(cR, cT); ctx.lineTo(cR, cB); ctx.lineTo(ccx, ccy); ctx.closePath(); ctx.fill();

  ctx.fillStyle = COLORS.blue;
  ctx.beginPath(); ctx.moveTo(cL, cB); ctx.lineTo(cR, cB); ctx.lineTo(ccx, ccy); ctx.closePath(); ctx.fill();

  // white divider lines
  ctx.strokeStyle = '#ffffff';
  ctx.lineWidth = 3;
  ctx.beginPath(); ctx.moveTo(cL, ccy); ctx.lineTo(cR, ccy); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(ccx, cT); ctx.lineTo(ccx, cB); ctx.stroke();

  // pieces
  drawPieces(ctx);
}

function segmentOwner(idx) {
  for (var i = 0; i < R.COLORS.length; i++) {
    var color = R.COLORS[i];
    if (SEGMENT[color].indexOf(idx) !== -1) return color;
  }
  return 'red';
}

function drawHomeArrow(ctx, cx, cy) {
  var tx = 7 * CELL + CELL / 2;
  var ty = 7 * CELL + CELL / 2;
  var ang = Math.atan2(ty - cy, tx - cx);
  ctx.save();
  ctx.translate(cx, cy);
  ctx.rotate(ang);
  ctx.beginPath();
  ctx.moveTo(11, 0);
  ctx.lineTo(-6, 7);
  ctx.lineTo(-6, -7);
  ctx.closePath();
  ctx.fillStyle = 'rgba(255,255,255,.95)';
  ctx.fill();
  ctx.restore();
}

function cellFill(r, c, fill, stroke, lw) {
  var ctx = boardCtx;
  var x = c * CELL;
  var y = r * CELL;
  ctx.fillStyle = fill;
  roundRect(ctx, x + 2, y + 2, CELL - 4, CELL - 4, 6);
  ctx.fill();
  if (stroke) {
    ctx.strokeStyle = stroke;
    ctx.lineWidth = lw || 1.5;
    ctx.stroke();
  }
}

function drawBase(ctx, color) {
  var br = BASE_RECT[color][0];
  var bc = BASE_RECT[color][1];
  var x = bc * CELL;
  var y = br * CELL;
  var clr = COLORS[color];

  // colored base square
  ctx.fillStyle = clr;
  roundRect(ctx, x + 2, y + 2, 6 * CELL - 4, 6 * CELL - 4, 14);
  ctx.fill();
  ctx.strokeStyle = 'rgba(0,0,0,.12)';
  ctx.lineWidth = 2;
  ctx.stroke();

  // big white circle in the middle
  var ccx = x + 3 * CELL;
  var ccy = y + 3 * CELL;
  ctx.beginPath();
  ctx.arc(ccx, ccy, 76, 0, Math.PI * 2);
  ctx.fillStyle = '#ffffff';
  ctx.fill();
  ctx.strokeStyle = 'rgba(0,0,0,.15)';
  ctx.lineWidth = 2;
  ctx.stroke();

  // 4 yard circles
  R.PLAYER[color].yard.forEach(function (cell) {
    var cx = cell[1] * CELL + CELL / 2;
    var cy = cell[0] * CELL + CELL / 2;
    ctx.beginPath();
    ctx.arc(cx, cy, 17, 0, Math.PI * 2);
    ctx.fillStyle = clr;
    ctx.fill();
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 2.5;
    ctx.stroke();
  });
}

// ---------------------------------------------------------------- pieces

var clickablePieces = [];

function drawPieces(ctx) {
  clickablePieces.length = 0;
  var placements = new Map();

  var myTurn = state.turn === myColor && state.pendingMove;
  var movable = new Set();
  if (myTurn) {
    R.movablePieces(state.players[myColor].pieces, state.dice, myColor).forEach(function (i) { movable.add(i); });
  }

  ['red', 'yellow'].forEach(function (color) {
    state.players[color].pieces.forEach(function (piece, slot) {
      var cell = null;
      if (piece.zone === 'yard') {
        cell = R.PLAYER[color].yard[piece.pos];
      } else if (piece.zone === 'track') {
        cell = R.PATH[piece.idx];
      } else if (piece.zone === 'home') {
        if (piece.pos === 0) cell = R.PLAYER[color].entryCell;
        else if (piece.pos >= 6) cell = R.PLAYER[color].goal;
        else cell = R.PLAYER[color].homeCells[piece.pos - 1];
      }
      if (!cell) return;
      var key = cell.join(',');
      if (!placements.has(key)) placements.set(key, []);
      placements.get(key).push({ color: color, slot: slot });
    });
  });

  var OFFSETS = [[0,0],[13,0],[-13,0],[0,13],[0,-13],[13,13],[-13,13],[-13,-13],[13,-13]];

  placements.forEach(function (list, key) {
    var parts = key.split(',');
    var r = Number(parts[0]);
    var c = Number(parts[1]);
    var cx = c * CELL + CELL / 2;
    var cy = r * CELL + CELL / 2;
    list.forEach(function (item, i) {
      var off = OFFSETS[i % OFFSETS.length];
      var px = cx + off[0];
      var py = cy + off[1];
      var canMoveThis = myTurn && item.color === myColor && movable.has(item.slot);
      drawPiece(ctx, item.color, item.slot, px, py, canMoveThis);
      if (canMoveThis) clickablePieces.push({ slot: item.slot, x: px, y: py });
    });
  });
}

function drawPiece(ctx, color, slot, x, y, highlight) {
  if (highlight) {
    var t = Date.now() / 200;
    ctx.beginPath();
    ctx.arc(x, y, 17 + Math.sin(t) * 2.4, 0, Math.PI * 2);
    ctx.strokeStyle = 'rgba(255,255,255,.95)';
    ctx.lineWidth = 4;
    ctx.stroke();
    ctx.lineWidth = 2.4;
    ctx.strokeStyle = COLORS[color];
    ctx.stroke();
  }
  ctx.beginPath();
  ctx.arc(x, y, 14, 0, Math.PI * 2);
  ctx.fillStyle = COLORS[color];
  ctx.fill();
  ctx.strokeStyle = '#fff';
  ctx.lineWidth = 3;
  ctx.stroke();
  // shine
  ctx.beginPath();
  ctx.arc(x - 4, y - 4, 4.5, 0, Math.PI * 2);
  ctx.fillStyle = 'rgba(255,255,255,.65)';
  ctx.fill();
}

// ---------------------------------------------------------------- die

function pipScale(v) {
  return 0.16;
}

function renderDie(rolling) {
  const ctx = dieCtx;
  const s = 96;
  ctx.clearRect(0, 0, s, s);
  ctx.fillStyle = '#fff';
  roundRect(ctx, 2, 2, s - 4, s - 4, 16);
  ctx.fill();
  ctx.strokeStyle = '#cfd8dc';
  ctx.lineWidth = 2;
  ctx.stroke();

  let value = null;
  if (state && state.lastRoll) value = state.lastRoll.value;
  if (rolling) value = 1 + Math.floor(Math.random() * 6);

  if (value === null) {
    ctx.fillStyle = '#b0bec5';
    ctx.font = 'bold 44px Segoe UI';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('?', s / 2, s / 2 + 2);
    return;
  }

  drawPips(ctx, s, value);
}

function drawPips(ctx, s, v) {
  const d = s / 3;
  const pos = (i) => {
    const col = i % 3;
    const row = Math.floor(i / 3);
    return [d * (col + 0.5), d * (row + 0.5)];
  };
  const is = (arr) => arr.includes(v);
  const dots = [];
  if (is([1, 3, 5])) dots.push(pos(0));
  if (is([4, 5, 6])) dots.push(pos(1));
  if (is([2, 3, 4, 5, 6])) dots.push(pos(2));
  if (is([4, 5, 6])) dots.push(pos(3));
  if (is([1, 3, 5])) dots.push(pos(4));
  if (is([2, 3, 4, 5, 6])) dots.push(pos(5));
  if (is([6])) dots.push(pos(6));
  if (is([2, 3, 4, 5, 6])) dots.push(pos(7));
  if (is([4, 5, 6])) dots.push(pos(8));

  ctx.fillStyle = '#37474f';
  dots.forEach(([x, y]) => {
    ctx.beginPath();
    ctx.arc(x, y, 8.5, 0, Math.PI * 2);
    ctx.fill();
  });
}

// ---------------------------------------------------------------- log / chat / toast

function appendLogLine(entry) {
  if (!entry) return;
  if (entry.type === 'system') {
    const div = document.createElement('div');
    div.className = 'sys';
    div.textContent = entry.text;
    ui.log.appendChild(div);
  } else {
    const div = document.createElement('div');
    const who = document.createElement('span');
    who.className = 'who';
    who.style.color = entry.color ? COLORS[entry.color] : '#37474f';
    who.textContent = entry.name ? entry.name + ': ' : '';
    div.appendChild(who);
    div.appendChild(document.createTextNode(entry.text));
    ui.log.appendChild(div);
  }
  ui.log.scrollTop = ui.log.scrollHeight;
}

function renderChatMsg(msg) {
  const div = document.createElement('div');
  div.innerHTML = `<b>${esc(msg.name)}:</b> ${esc(msg.text)}`;
  const box = ui.log;
  box.appendChild(div);
  box.scrollTop = box.scrollHeight;
}

let toastTimer = null;
function toast(text) {
  ui.toast.textContent = text;
  ui.toast.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => ui.toast.classList.add('hidden'), 2600);
}

// ---------------------------------------------------------------- interactions

let rolling = false;
function startDieAnim() {
  rolling = true;
  ui.rollBtn.disabled = true;
  ui.rollBtn.textContent = 'Rolling…';
  renderDie(true);
  if (dieAnim) clearInterval(dieAnim);
  dieAnim = setInterval(() => renderDie(true), 90);
}

function stopDieAnim() {
  rolling = false;
  if (dieAnim) clearInterval(dieAnim);
  dieAnim = null;
}

function rollNow() {
  if (rollBtnBlocked()) return;
  startDieAnim();
  socket.emit('rollDice', {}, (res) => {
    if (res && res.ok === false) {
      stopDieAnim();
      toast(res.reason || 'Cannot roll');
    }
  });
}

function rollBtnBlocked() {
  return !state || !gameActive || state.winner || state.turn !== myColor || state.pendingMove;
}

function onBoardClick(ev) {
  const rect = ui.board.getBoundingClientRect();
  const scale = 600 / rect.width;
  const mx = (ev.clientX - rect.left) * scale;
  const my = (ev.clientY - rect.top) * scale;

  let best = null;
  clickablePieces.forEach((p) => {
    const d = Math.hypot(mx - p.x, my - p.y);
    if (d <= 22 && (!best || d < best.d)) best = p;
  });
  if (best) {
    socket.emit('movePiece', { slot: best.slot }, (res) => {
      if (res && res.ok === false) toast(res.reason || 'Cannot move there');
    });
  }
}

function onBoardMove(ev) {
  if (!clickablePieces.length) { ui.board.style.cursor = 'default'; return; }
  const rect = ui.board.getBoundingClientRect();
  const scale = 600 / rect.width;
  const mx = (ev.clientX - rect.left) * scale;
  const my = (ev.clientY - rect.top) * scale;
  const hit = clickablePieces.some((p) => Math.hypot(mx - p.x, my - p.y) <= 22);
  ui.board.style.cursor = hit ? 'pointer' : 'default';
}

// ---------------------------------------------------------------- dom wiring

ui.createBtn.addEventListener('click', () => {
  ui.landingError.textContent = '';
  if (!socket.connected) return serverDown();
  socket.emit('createRoom', { name: currentName() }, (res) => {
    if (res && res.ok === false) { ui.landingError.textContent = res.error || 'Failed to create room'; return; }
    roomCode = res.code;
    myColor = res.color;
    saveRoom();
    gameActive = false;
    waiting = true;
    showScreen('waiting');
    ui.roomCode.textContent = res.code;
    ui.waitStatus.textContent = 'Waiting for opponent…';
  });
});

ui.joinBtn.addEventListener('click', () => {
  const code = (ui.joinCode.value || '').trim().toUpperCase();
  if (code.length < 4) { ui.landingError.textContent = 'Enter the 4-letter room code'; return; }
  if (!socket.connected) return serverDown();
  ui.landingError.textContent = '';
  socket.emit('joinRoom', { code, name: currentName() }, (res) => {
    if (res && res.ok === false) { ui.landingError.textContent = res.error || 'Could not join room'; return; }
    roomCode = res.code;
    myColor = res.color;
    saveRoom();
    gameActive = true;
    waiting = false;
    showScreen('game');
    ui.waitStatus.textContent = '';
  });
});

function serverDown() {
  ui.landingError.textContent = 'Not connected to the game server. Run "cd server; npm start" first, then reload this page.';
}

ui.joinCode.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') ui.joinBtn.click();
});
ui.nameInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') ui.createBtn.click();
});

ui.rollBtn.addEventListener('click', rollNow);

ui.board.addEventListener('click', onBoardClick);
ui.board.addEventListener('mousemove', onBoardMove);

ui.copyLinkBtn.addEventListener('click', () => {
  const url = location.href.split('?')[0] + `?room=${roomCode}`;
  let done = false;
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(url).then(() => {
      toast('Invite link copied!');
      done = true;
    }).catch(() => {});
  }
  setTimeout(() => {
    if (!done) toast('Room: ' + roomCode);
  }, 300);
});

ui.cancelRoomBtn.addEventListener('click', leaveGame);
ui.leaveBtn.addEventListener('click', leaveGame);
ui.rematchBtn.addEventListener('click', () => socket.emit('rematch', () => {}));

function leaveGame() {
  socket.emit('leaveRoom', () => {});
  clearSavedRoom();
  gameActive = false;
  state = null;
  myColor = null;
  roomCode = null;
  showScreen('landing');
}

ui.chatInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && ui.chatInput.value.trim()) {
    socket.emit('chat', { text: ui.chatInput.value.trim() });
    ui.chatInput.value = '';
  }
});

// auto-rejoin after a reload
(function autoRejoin() {
  try {
    const saved = localStorage.getItem('ludo_room');
    if (!saved) return;
    const { code, name } = JSON.parse(saved);
    if (code) {
      setTimeout(() => {
        socket.emit('joinRoom', { code, name: name || 'Anonymous' }, (res) => {
          if (res && res.ok) {
            myColor = res.color;
            roomCode = res.code;
            saveRoom();
            gameActive = true;
            waiting = false;
            showScreen('game');
          } else {
            clearSavedRoom();
          }
        });
      }, 300);
    }
  } catch (e) { /* ignore */ }
})();

// ---------------------------------------------------------------- helpers

function roundRect(ctx, x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + w - r, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + r);
  ctx.lineTo(x + w, y + h - r);
  ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  ctx.lineTo(x + r, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - r);
  ctx.lineTo(x, y + r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.closePath();
}

function drawStar(ctx, cx, cy, r) {
  ctx.beginPath();
  for (let i = 0; i < 10; i++) {
    const rad = i % 2 === 0 ? r : r * 0.45;
    const a = (Math.PI / 5) * i - Math.PI / 2;
    const x = cx + Math.cos(a) * rad;
    const y = cy + Math.sin(a) * rad;
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
  }
  ctx.closePath();
}

function esc(s) {
  return String(s).replace(/[&<>"']/g, (m) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]
  ));
}

function setConn(status) {
  if (!ui.connStatus) return;
  ui.connStatus.textContent = status;
  ui.connStatus.classList.toggle('hidden', !status);
}

// pulse for highlighted pieces (re-render board a few times a second)
setInterval(() => {
  if (gameActive && state && state.turn === myColor && state.pendingMove) renderBoard();
}, 100);

connect();