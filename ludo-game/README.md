# Ludo — 2-player online board game

A full Ludo game for exactly **2 players** played over the internet on
**two different devices / networks**, with the board state kept identical on
both screens (the server is authoritative and broadcasts every change).

- Server: Node.js + Express + Socket.io
- Client: HTML5 Canvas + vanilla JS (no build step) — works on any modern browser

## Quick start

```bash
cd server
npm install
npm start
```

Then open **http://localhost:3000** in Chrome/Edge/Firefox:

1. Player 1: enter a name → **Create a room** → share the invite link (or the 4-letter code).
2. Player 2: open the link (or enter the code) → click **Join**.
3. The game starts automatically the moment both players are connected.

Player 1 is **red**, player 2 is **yellow** — they sit on opposite corners.

## Playing across DIFFERENT networks

The server must be reachable from the internet. Pick one:

### Option A — VPS (recommended)
Run `node server.js` on any cheap VPS/VPS that has a public IP, then both
players open `http://<your-vps-ip>:3000`.

### Option B — ngrok tunnel from home (no VPS)
```bash
ngrok http 3000
```
Both players open the `https://…ngrok.io` URL that ngrok prints.

### Option C — Port forwarding on a home router
Forward TCP port 3000 to the machine running the server, then both players
open `http://<your-public-ip>:3000`.

> The server prints its local + LAN addresses on startup, so you can also try
> it on your home Wi-Fi without any of the above: just open the `Network` URL.

## How to play

- **Roll a 6** to bring a piece out of your yard onto the start square.
- Rolling a **6 gives you another roll**.
- Pieces move **clockwise** along the shared 52-square track.
- Landing on an opponent's piece sends it **back to its yard** — except on
  the four **star/start squares**, which are safe.
- After a full lap a piece turns into your **home column** and must reach the
  middle **goal square** with the exact roll.
- Move pieces by clicking them (their highlight ring shows which ones can move).
- First player to get **all four pieces home wins** — then use **Rematch**
  (starter alternates).

## Project layout

```
ludo-game/
├── client/                 # static site served to every player
│   ├── index.html          # landing / waiting / game screens
│   ├── style.css
│   ├── js/
│   │   ├── main.js         # canvas renderer + socket wiring
│   │   └── shared/rules.js # board constants + movement rules (shared file)
└── server/
    ├── server.js           # Express + Socket.io, rooms & events
    ├── game.js             # authoritative game state (roll/move/capture/win)
    └── test/
        ├── game.test.js    # unit tests for the rules engine
        └── socket.test.js  # E2E: two real socket clients play a few turns
```

## Tests

```bash
cd server
node test/game.test.js     # 15 rule/engine assertions
node test/socket.test.js   # boots the server + 2 clients, plays real turns
```

## Rules notes

- Board: standard 15×15 Ludo board; the 52-cell shared track is the classic
  perimeter loop (the layout was verified against the reference `Board.java`
  from the [schweryjonas/ludo-game](https://github.com/schweryjonas/ludo-game)
  project and the `flutter_ludo` package constants).
- Home column: entry square + 5 colored cells + goal. Overshooting the goal
  is an illegal move.
- No blocking rule: friendly pieces may stack on the same square.
- Three-in-a-row sixes: not enforced (every 6 simply grants another roll).
- If a roll has no legal move, the turn passes automatically.