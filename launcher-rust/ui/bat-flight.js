"use strict";

// Flight uses CSS pixels and seconds. The renderer consumes the eased pose.
const BatFlight = (() => {
  const mix = (a, b, t) => a + (b - a) * t;
  const clamp = (value, low, high) => Math.max(low, Math.min(high, value));
  const ease = (a, b, rate, dt) => mix(a, b, 1 - Math.exp(-rate * dt));
  const tau = 2 * Math.PI;
  const motion = {
    Flying: {speed: 108, flap: 22, stroke: .85},
    Gliding: {speed: 92, flap: 9, stroke: .1},
    Hovering: {speed: 32, flap: 31, stroke: .76},
    Circling: {speed: 88, flap: 23, stroke: .68},
    Swooping: {speed: 133, flap: 19, stroke: .55},
    Landing: {speed: 104, flap: 28, stroke: .7},
    Hanging: {speed: 0, flap: 0, stroke: 0},
    TakingOff: {speed: 155, flap: 35, stroke: 1},
    Held: {speed: 0, flap: 38, stroke: .68}
  };

  function create(bounds, random = Math.random) {
    let area = bounds, perches = [], anchor = null, bend = 0, pace = 1;
    let centerX = 0, centerY = 0, orbitPhase = 0, orbitRadius = 0, orbitDirection = 1;
    let flapRate = 22, idleTime = 0, petTime = 0;
    const bat = {x: bounds.right - (bounds.right - bounds.left) * .12,
      y: mix(bounds.top, bounds.bottom, .44), vx: -48, vy: 0, facing: -1, flip: 0,
      bank: 0, phase: 0, stroke: .85, fold: 0, blink: false,
      state: 'Flying', age: 0, wait: 0, targetX: 0, targetY: 0};
    const range = (a, b) => mix(a, b, random());
    function aim(x, y) {
      bat.targetX = clamp(x, area.left, area.right);
      bat.targetY = clamp(y, area.top, area.bottom);
    }
    const distance = () => Math.hypot(bat.targetX - bat.x, bat.targetY - bat.y);

    function enter(state) {
      bat.state = state;
      bat.age = 0;
      bend = 0;
      pace = 1;
      switch (state) {
        case 'Flying': {
          pace = range(.88, 1.14);
          const middle = (area.left + area.right) / 2;
          const x = bat.x > middle ? range(.12, .43) : range(.57, .88);
          aim(mix(area.left, area.right, x), mix(area.top, area.bottom, range(.16, .83)));
          bend = range(-.67, .67);
          bat.wait = clamp(distance() / (100 * pace) + .9, 2.3, 5.5);
          break;
        }
        case 'Gliding':
        case 'Swooping': {
          const direction = Math.sign(bat.vx || bat.facing);
          aim(bat.x + direction * range(125, 195), bat.y + range(-45, 38));
          if (Math.abs(bat.targetX - bat.x) < 68) aim(bat.x - direction * 150, bat.targetY);
          bend = state === 'Swooping' ? Math.sign(bat.targetX - bat.x) * .95 : .16;
          bat.wait = state === 'Swooping' ? 1.8 : range(1.05, 1.7);
          break;
        }
        case 'Hovering':
          centerX = bat.x; centerY = bat.y;
          bat.wait = range(.75, 1.5);
          break;
        case 'Circling':
          orbitRadius = Math.min(43, (area.right - area.left) * .22, (area.bottom - area.top) * .38);
          orbitDirection = Math.sign(bat.vx || bat.facing);
          centerX = clamp(bat.x, area.left + orbitRadius, area.right - orbitRadius);
          centerY = clamp(bat.y + orbitRadius * .5, area.top + orbitRadius * .55, area.bottom - orbitRadius * .55);
          orbitPhase = Math.atan2((bat.y - centerY) / .55, bat.x - centerX);
          bat.wait = range(2.35, 2.8);
          break;
        case 'Landing': {
          const nearby = perches.map(perch => ({perch, distance: Math.hypot(perch.x - bat.x, perch.y - bat.y)}))
            .sort((a, b) => a.distance - b.distance).slice(0, 5);
          const choice = random();
          const ceiling = perches.find(perch => perch.id === 'ceiling');
          const selected = choice < .22 && ceiling ? ceiling : nearby[Math.floor(choice * nearby.length)]?.perch;
          anchor = selected?.id ?? null;
          aim(selected?.x ?? bat.x + range(-65, 65), selected?.y ?? area.top);
          bat.wait = 2.5 + distance() / 83;
          bend = range(-.35, .35);
          break;
        }
        case 'Hanging':
          bat.wait = range(3.4, 6.6);
          bat.vx = bat.vy = 0;
          break;
        case 'TakingOff': {
          anchor = null;
          const direction = bat.x < (area.left + area.right) / 2 ? 1 : -1;
          aim(bat.x + direction * range(73, 120), bat.y + range(76, 110));
          bat.wait = .78;
          break;
        }
        case 'Held':
          anchor = null;
          bat.vx = bat.vy = 0;
          bat.wait = Infinity;
          break;
      }
    }

    function resize(next) {
      // Keep the on-screen position when a window grows. Clamp only if it shrinks past the bat.
      area = next;
      bat.x = clamp(bat.x, area.left, area.right);
      bat.y = clamp(bat.y, area.top, area.bottom);
      aim(bat.targetX, bat.targetY);
      if (anchor === null && (bat.state === 'Landing' || bat.state === 'Hanging')) {
        bat.targetY = area.top;
        if (bat.state === 'Hanging') bat.y = area.top;
      }
    }

    function setPerches(next) {
      perches = next.filter(({x, y}) => x >= area.left && x <= area.right && y >= area.top && y <= area.bottom);
      if (anchor === null || (bat.state !== 'Hanging' && bat.state !== 'Landing')) return;
      const perch = perches.find(item => item.id === anchor);
      if (!perch || Math.hypot(perch.x - bat.targetX, perch.y - bat.targetY) > 28) {
        enter('TakingOff');
        return;
      }
      aim(perch.x, perch.y);
      if (bat.state === 'Hanging') {
        bat.x = perch.x;
        bat.y = perch.y;
      }
    }

    function hold() {
      enter('Held');
    }
    function drag(x, y) {
      if (bat.state !== 'Held') return;
      aim(x, y);
      bat.x = bat.targetX;
      bat.y = bat.targetY;
    }
    function release() {
      if (bat.state === 'Held') enter('Flying');
    }
    function pet() {
      petTime = .7;
    }

    function step(delta, settings) {
      const dt = clamp(delta, 0, .05) * settings.speed;
      bat.age += dt;
      idleTime += dt;
      petTime = Math.max(0, petTime - dt);
      if (!settings.hang && (bat.state === 'Hanging' || bat.state === 'Landing')) enter('TakingOff');
      if (bat.state === 'Landing' && distance() < 1.2) {
        bat.x = bat.targetX;
        bat.y = bat.targetY;
        enter('Hanging');
      } else if (bat.age >= bat.wait) {
        if (bat.state === 'Flying') {
          const choice = random();
          enter(choice < .23 && settings.hang ? 'Landing' : choice < .4 ? 'Gliding' :
            choice < .56 ? 'Hovering' : choice < .73 ? 'Circling' : 'Swooping');
        } else {
          enter(bat.state === 'Hanging' ? 'TakingOff' : 'Flying');
        }
      }

      const state = bat.state;
      const {speed, flap, stroke} = motion[state];
      if (state === 'Hovering') {
        aim(centerX + Math.sin(bat.age * 3.1) * 9, centerY + Math.sin(bat.age * 5) * 5);
      } else if (state === 'Circling') {
        const angle = orbitPhase + orbitDirection * bat.age * 2.55;
        aim(centerX + Math.cos(angle) * orbitRadius, centerY + Math.sin(angle) * orbitRadius * .55);
      }
      const remaining = distance();
      if (state !== 'Hanging' && state !== 'Held') {
        const dx = bat.targetX - bat.x, dy = bat.targetY - bat.y;
        const approach = Math.min(speed * pace, remaining * (state === 'Landing' ? 5 : 3.8));
        // A perpendicular component bends each leg and fades out before reaching its target.
        const curve = bend * Math.min(1, remaining / 72);
        const bob = state === 'Landing' ? 0 : Math.sin(bat.phase) * 4 + Math.sin(idleTime * 3.8) * 3;
        const vx = remaining ? (dx - dy * curve) * approach / remaining : 0;
        const vy = remaining ? (dy + dx * curve) * approach / remaining : 0;
        const response = state === 'TakingOff' ? 8 : 5.3;
        bat.vx = ease(bat.vx, vx, response, dt);
        bat.vy = ease(bat.vy, vy + bob, response, dt);
        bat.x = clamp(bat.x + bat.vx * dt, area.left, area.right);
        bat.y = clamp(bat.y + bat.vy * dt, area.top, area.bottom);
        if ((bat.x === area.left && bat.vx < 0) || (bat.x === area.right && bat.vx > 0)) bat.vx = 0;
        if ((bat.y === area.top && bat.vy < 0) || (bat.y === area.bottom && bat.vy > 0)) bat.vy = 0;
      }

      const rest = state === 'Hanging';
      const landing = state === 'Landing';
      const settle = rest ? 1 : landing ? 1 - clamp((remaining - 4) / 30, 0, 1) : 0;
      if (Math.abs(bat.vx) > 7) bat.facing = ease(bat.facing, Math.sign(bat.vx), 7, dt);
      bat.flip = ease(bat.flip, Math.PI * settle, 10, dt);
      bat.fold = ease(bat.fold, settle, 11, dt);
      const bank = state === 'Held' ? Math.sin(bat.age * 8) * .12 :
        rest ? 0 : clamp(bat.vy / 245, -.22, .22) * bat.facing;
      bat.bank = ease(bat.bank, bank, 7, dt);
      bat.stroke = ease(bat.stroke, stroke * (1 - settle), 10, dt);
      flapRate = ease(flapRate, flap, 8, dt);
      bat.phase = (bat.phase + dt * flapRate) % tau;
      bat.blink = petTime > .18 || (rest && bat.age % 2.8 > 2.64);
      return bat;
    }

    enter('Flying');
    return {bat, step, resize, setPerches, hold, drag, release, pet};
  }
  return {create};
})();

if (typeof module !== 'undefined') module.exports = BatFlight;
