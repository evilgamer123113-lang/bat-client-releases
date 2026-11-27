'use strict';

const rules = require('../client/js/shared/rules');

const TURN_ORDER = ['red', 'yellow'];

function makePieces() {
  const pieces = [];
  for (let i = 0; i < 4; i++) {
    pieces.push({ zone: 'yard', pos: i, slot: i });
  }
  return pieces;
}

function newGame(redName, yellowName, startingColor) {
  const starting = startingColor || 'red';
  return {
    players: {
      red: { name: redName || 'Player 1', finished: 0, pieces: makePieces() },
      yellow: { name: yellowName || 'Player 2', finished: 0, pieces: makePieces() }
    },
    turn: starting,
    dice: null,
    pendingMove: false,
    winner: null,
    lastRoll: null,
    message: null,
    chat: [],
    log: []
  };
}

function clone(x) {
  return JSON.parse(JSON.stringify(x));
}

function snapshot(game) {
  return {
    turn: game.turn,
    dice: game.dice,
    pendingMove: game.pendingMove,
    winner: game.winner,
    message: game.message,
    lastRoll: game.lastRoll,
    log: clone(game.log),
    chat: game.chat,
    players: {
      red: {
        name: game.players.red.name,
        finished: game.players.red.finished,
        pieces: clone(game.players.red.pieces)
      },
      yellow: {
        name: game.players.yellow.name,
        finished: game.players.yellow.finished,
        pieces: clone(game.players.yellow.pieces)
      }
    }
  };
}

function otherPlayer(color) {
  return color === 'red' ? 'yellow' : 'red';
}

function countFinished(player) {
  let n = 0;
  player.pieces.forEach((p) => { if (p.zone === 'home' && p.pos >= 6) n++; });
  player.finished = n;
  return n;
}

function rollDice(game, color) {
  if (game.winner) return { ok: false, reason: 'Game already finished' };
  if (game.turn !== color) return { ok: false, reason: 'Not your turn' };
  if (game.pendingMove) return { ok: false, reason: 'Move a piece first' };

  const dice = 1 + Math.floor(Math.random() * 6);
  game.lastRoll = { color: color, value: dice, ts: Date.now() };
  game.dice = dice;
  game.pendingMove = true;

  const movable = rules.movablePieces(game.players[color].pieces, dice, color);
  if (movable.length === 0) {
    // No piece can move -> turn passes automatically.
    game.dice = null;
    game.pendingMove = false;
    game.turn = rules.nextColor(color, TURN_ORDER);
    game.message = `${cap(color)} rolled ${dice} — no legal moves, turn passes`;
    pushLog(game, 'system', null, `${cap(color)} rolled ${dice} but has no legal moves — turn passes`);
    return { ok: true, autoSkip: true, dice };
  }
  game.message = `${cap(color)} rolled ${dice}`;
  pushLog(game, 'turn', color, `rolled ${dice}`);
  return { ok: true, dice };
}

function movePiece(game, color, slot) {
  if (game.winner) return { ok: false, reason: 'Game already finished' };
  if (game.turn !== color) return { ok: false, reason: 'Not your turn' };
  if (!game.pendingMove || !game.dice) return { ok: false, reason: 'Roll the dice first' };

  const player = game.players[color];
  const piece = player.pieces[slot];
  if (!piece) return { ok: false, reason: 'Invalid piece' };

  const dest = rules.legalDestination(piece, game.dice, color);
  if (!dest) return { ok: false, reason: 'That piece cannot move' };

  const roll = game.dice;
  const from = describePosition(piece);
  const to = describeDest(dest);

  // Apply the move.
  applyDestination(piece, dest);

  let logLine = `${cap(color)} moved a piece (${from} → ${to})`;

  // Captures: landing on a non-safe track cell occupied by an opponent piece.
  if (dest.zone === 'track' && rules.SAFE_INDEX.indexOf(dest.idx) === -1) {
    const opp = game.players[otherPlayer(color)];
    const captured = [];
    opp.pieces.forEach((op) => {
      if (op.zone === 'track' && op.idx === dest.idx) {
        op.zone = 'yard';
        op.pos = op.slot;
        delete op.idx;
        captured.push(cap(opp.name));
      }
    });
    if (captured.length) {
      game.message = `${cap(color)} captured ${captured.join(', ')}!`;
      logLine += ` and captured ${captured.join(', ')}`;
    }
  }

  if (countFinished(player) === 4) {
    game.winner = color;
    game.message = `${cap(color)} WINS the game! 🎉`;
    logLine += ' — WINS the game! 🎉';
  }
  pushLog(game, 'move', color, logLine);

  // Advance turn.
  game.pendingMove = false;
  game.dice = null;
  if (!game.winner) {
    if (roll === 6) {
      game.message = game.message + ' — rolled a 6, roll again!';
    } else {
      game.turn = rules.nextColor(color, TURN_ORDER);
    }
  }

  return { ok: true, from, to: describePosition(piece) };
}

function applyDestination(piece, dest) {
  piece.zone = dest.zone;
  if (dest.zone === 'track') {
    piece.idx = dest.idx;
    delete piece.pos;
  } else {
    piece.pos = dest.pos;
    delete piece.idx;
  }
}

function describePosition(piece) {
  if (!piece) return '?';
  if (piece.zone === 'yard') return `yard slot ${piece.pos + 1}`;
  if (piece.zone === 'home') {
    return piece.pos >= 6 ? 'the goal' : `home step ${piece.pos}`;
  }
  return `square ${piece.idx + 1}`;
}

function describeDest(dest) {
  if (dest.zone === 'track') return `square ${dest.idx + 1}`;
  return dest.pos >= 6 ? 'the goal' : `home step ${dest.pos}`;
}

function cap(s) {
  return String(s).charAt(0).toUpperCase() + String(s).slice(1);
}

function pushLog(game, type, color, text) {
  game.log.push({ type, color, text });
  if (game.log.length > 60) game.log.shift();
}

module.exports = {
  newGame,
  snapshot,
  rollDice,
  movePiece,
  TURN_ORDER,
  rules
};