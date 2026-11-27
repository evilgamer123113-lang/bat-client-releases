(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.LudoRules = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // Standard 15x15 Ludo board. Track cells are [row, col] 0-indexed,
  // listed in clockwise travel order. 52 cells total.
  // Index 0 = Yellow start, 13 = Green, 26 = Red, 39 = Blue.
  var PATH = [
    [6,1],[6,2],[6,3],[6,4],[6,5],[5,6],[4,6],[3,6],[2,6],[1,6],[0,6],[0,7],[0,8],
    [1,8],[2,8],[3,8],[4,8],[5,8],[6,9],[6,10],[6,11],[6,12],[6,13],[6,14],[7,14],[8,14],
    [8,13],[8,12],[8,11],[8,10],[8,9],[9,8],[10,8],[11,8],[12,8],[13,8],[14,8],[14,7],[14,6],
    [13,6],[12,6],[11,6],[10,6],[9,6],[8,5],[8,4],[8,3],[8,2],[8,1],[8,0],[7,0],[6,0]
  ];

  var COLORS = ['red', 'green', 'yellow', 'blue'];

  // Path index where each color enters the shared track.
  var START_INDEX = { red: 26, yellow: 0, green: 13, blue: 39 };

  // Start squares are safe (no capture) for everyone.
  var SAFE_INDEX = [0, 13, 26, 39];

  // Per-color board data.
  //   startIdx    -> path index of the colored start (star) square
  //   homeEntryIdx-> path index where a piece turns into its home column
  //   homeCells   -> the 5 colored home cells that lead to the goal
  //   goal        -> [row, col] of the center goal cell
  //   entryCell   -> [row, col] of the home-column entry cell (== path cell)
  //   yard        -> the 4 [row, col] yard slots inside the base quadrants
  var PLAYER = {
    red: {
      startIdx: 26,
      homeEntryIdx: 25,
      homeCells: [[7,13],[7,12],[7,11],[7,10],[7,9]],
      goal: [7,8],
      entryCell: [7,14],
      yard: [[12,12],[12,13],[11,13],[11,12]]
    },
    yellow: {
      startIdx: 0,
      homeEntryIdx: 50,
      homeCells: [[7,1],[7,2],[7,3],[7,4],[7,5]],
      goal: [7,6],
      entryCell: [7,0],
      yard: [[2,1],[2,2],[3,1],[3,2]]
    },
    green: {
      startIdx: 13,
      homeEntryIdx: 12,
      homeCells: [[1,7],[2,7],[3,7],[4,7],[5,7]],
      goal: [6,7],
      entryCell: [0,7],
      yard: [[2,12],[2,13],[3,12],[3,13]]
    },
    blue: {
      startIdx: 39,
      homeEntryIdx: 38,
      homeCells: [[13,7],[12,7],[11,7],[10,7],[9,7]],
      goal: [8,7],
      entryCell: [14,7],
      yard: [[12,1],[12,2],[11,1],[11,2]]
    }
  };

  // First 6 path cells after each start cell get tinted with the color
  // for the classic "own trail" look. Used by the renderer only.
  var TRAIL = {};
  COLORS.forEach(function (color) {
    TRAIL[color] = [];
    for (var i = 0; i < 6; i++) {
      TRAIL[color].push((START_INDEX[color] + i) % 52);
    }
  });

  // Colors that sit opposite each other on the board.
  var OPPOSITE = { red: 'yellow', yellow: 'red', green: 'blue', blue: 'green' };

  // ------------------------------------------------------------------
  // Movement rules
  // ------------------------------------------------------------------

  // A piece: { zone: 'yard'|'track'|'home', pos or idx, slot }
  //   yard  -> pos is the yard slot index (0-3)
  //   track -> idx is the path index (0-51); pos unused
  //   home  -> pos is the home-column position:
  //             0 = sitting on the entry cell (turned in, safe)
  //             1-5 = moving through the colored home cells
  //             6 = reached the goal (finished)
  //
  // Returns the destination: { zone:'track', idx } | { zone:'home', pos } | null.
  function legalDestination(piece, roll, color) {
    if (!piece) return null;
    if (piece.zone === 'yard') {
      if (roll === 6) return { zone: 'track', idx: PLAYER[color].startIdx };
      return null;
    }
    if (piece.zone === 'home') {
      if (piece.pos >= 6) return null;            // already finished
      if (piece.pos === 0) return { zone: 'home', pos: roll }; // 1-6, <= goal
      if (piece.pos + roll === 6) return { zone: 'home', pos: 6 };
      return null;                                 // would overshoot the goal
    }
    // on the track
    var entry = PLAYER[color].homeEntryIdx;
    if (piece.idx === entry) {
      // sitting right on the entry cell -> next roll walks up the home column
      return { zone: 'home', pos: roll };
    }
    var distToEntry = (entry - piece.idx + 52) % 52;
    if (roll < distToEntry) {
      return { zone: 'track', idx: (piece.idx + roll) % 52 };
    }
    var homePos = roll - distToEntry;              // 0..5
    if (homePos > 6) return null;                  // overshoot (never happens, roll<=6)
    return { zone: 'home', pos: homePos };
  }

  function movablePieces(pieces, roll, color) {
    var list = [];
    if (!roll) return list;
    pieces.forEach(function (piece, i) {
      if (legalDestination(piece, roll, color)) list.push(i);
    });
    return list;
  }

  function hasAnyMove(pieces, roll, color) {
    return movablePieces(pieces, roll, color).length > 0;
  }

  function nextColor(current, order) {
    var idx = order.indexOf(current);
    return order[(idx + 1) % order.length];
  }

  return {
    PATH: PATH,
    COLORS: COLORS,
    START_INDEX: START_INDEX,
    SAFE_INDEX: SAFE_INDEX,
    PLAYER: PLAYER,
    TRAIL: TRAIL,
    OPPOSITE: OPPOSITE,
    legalDestination: legalDestination,
    movablePieces: movablePieces,
    hasAnyMove: hasAnyMove,
    nextColor: nextColor
  };
});