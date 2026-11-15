"use strict";

// Integer pixel coordinates keep each pose crisp on the same transparent grid.
const BatSprite = (() => {
  const width = 44, height = 30;
  const neutral = 12;
  const colors = {
    outline: '#202127', rim: '#767983', seam: '#60626c',
    membrane: '#42444e', inset: '#343640', underside: '#2a2c34',
    body: '#41434d', face: '#575a65', cheek: '#3c3e48',
    muzzle: '#6b6e79', eye: '#dedfe5'
  };
  // Shoulder, elbow, outer tip, then the scalloped trailing edge.
  const profile = [
    [0, 13, 22], [3, 11, 19], [6, 9, 20], [9, 8, 16],
    [12, 9, 17], [15, 10, 14], [18, 12, 12]
  ];

  function pose(createCanvas, lift, fold, blink = false) {
    const frame = createCanvas();
    frame.width = width;
    frame.height = height;
    const context = frame.getContext('2d');
    if (!context) throw Error('Sprite canvas unavailable');
    function pixel(color, x, y, w = 1, h = 1) {
      context.fillStyle = color;
      context.fillRect(x, y, w, h);
    }

    const span = Math.round(18 - 10 * fold - 5 * Math.abs(lift));
    for (let column = 0; column <= span; column++) {
      const u = column / span;
      const index = u * 18;
      const segment = Math.min(profile.length - 2, Math.floor(index / 3));
      const [from, fromTop, fromBottom] = profile[segment];
      const [, toTop, toBottom] = profile[segment + 1];
      const blend = (index - from) / 3;
      const raise = lift * (u + 10 * u * u);
      const top = Math.round(fromTop + (toTop - fromTop) * blend + raise + fold * 7 * u);
      const bottom = Math.round(fromBottom + (toBottom - fromBottom) * blend + raise + fold * (4 + 5 * u));
      const depth = Math.max(1, bottom - top + 1);
      const shaded = lift > .35 ? colors.underside : colors.membrane;
      for (const side of [-1, 1]) {
        const x = side < 0 ? 18 - column : 25 + column;
        pixel(colors.outline, x, top, 1, depth);
        pixel(depth > 3 ? colors.rim : colors.seam, x, top);
        if (depth < 3) continue;
        const rib = column === Math.round(span * .33) || column === Math.round(span * .66);
        pixel(rib ? colors.seam : column < span / 2 ? shaded : colors.inset, x, top + 1, 1, depth - 2);
        if (depth >= 5 && !rib) pixel(colors.underside, x, bottom - 1);
      }
    }

    pixel(colors.outline, 18, 13, 8, 12);
    pixel(colors.body, 19, 14, 6, 10);
    pixel(colors.underside, 19, 20, 6, 3);
    pixel(colors.seam, 19, 23, 2, 2);
    pixel(colors.seam, 23, 23, 2, 2);
    pixel(colors.outline, 17, 7, 10, 10);
    pixel(colors.rim, 17, 6, 3, 1);
    pixel(colors.rim, 24, 6, 3, 1);
    pixel(colors.cheek, 18, 7, 2, 3);
    pixel(colors.cheek, 24, 7, 2, 3);
    pixel(colors.face, 18, 10, 8, 5);
    pixel(colors.cheek, 18, 14, 8, 2);
    pixel(colors.outline, 18, 11, 2, 2);
    pixel(colors.outline, 24, 11, 2, 2);
    if (!blink) {
      pixel(colors.eye, 19, 11);
      pixel(colors.eye, 24, 11);
    }
    pixel(colors.muzzle, 20, 13, 4, 2);
    pixel(colors.outline, 21, 13, 2, 1);
    return frame;
  }

  function create(createCanvas) {
    const stroke = (blink) => Array.from({length: neutral * 2 + 1},
      (_, i) => pose(createCanvas, (i - neutral) / neutral, 0, blink));
    const flaps = stroke(false), happy = stroke(true);
    const folds = Array.from({length: 5}, (_, i) => pose(createCanvas, 0, (i + 1) / 5));
    const blink = pose(createCanvas, 0, 1, true);
    return (lift, fold, eyesClosed) => {
      if (eyesClosed && fold > .9) return blink;
      const folded = Math.round(fold * folds.length);
      return folded ? folds[folded - 1] : (eyesClosed ? happy : flaps)[Math.round(neutral + neutral * lift)];
    };
  }
  return {width, height, create};
})();
