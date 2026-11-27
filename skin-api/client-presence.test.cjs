const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { createPresenceHandler } = require('./client-presence');

test('cross-client discovery, expiration, session isolation, privacy and invalid requests', async () => {
  let time = 1000;
  const handler = createPresenceHandler({ now: () => time, ttl: 60000 });
  const server = http.createServer((req, res) => {
    const send = (value, status = 200) => { res.writeHead(status, {'Content-Type': 'application/json'}); res.end(JSON.stringify(value)); };
    if (!handler(req, send, req.url)) send({}, 404);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = 'http://127.0.0.1:' + server.address().port;
  const a = 'a'.repeat(32), b = 'b'.repeat(32), nonBat = 'c'.repeat(32);
  const s1 = '1'.repeat(64), s2 = '2'.repeat(64), s3 = '3'.repeat(64);
  const post = async (body, path = '') => {
    const res = await fetch(base + '/api/client-presence' + path, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
    return {status: res.status, body: await res.json()};
  };
  try {
    await post({uuid: a, session: s1, share: true, peers: [b]});
    let result = await post({uuid: b, session: s2, share: true, peers: [a, nonBat]});
    assert.equal(result.status, 200); assert.equal(result.body.active[a], 60000); assert.equal(result.body.active[nonBat], undefined);
    result = await post({uuid: a, session: s1, share: true, peers: [b]});
    assert.equal(result.body.active[b], 60000);
    await post({uuid: a, session: s3, share: true, peers: []});
    await post({uuid: a, session: s1}, '/leave');
    result = await post({uuid: b, session: s2, share: true, peers: [a]});
    assert.equal(result.body.active[a], 60000, 'one session leaving cannot remove another');
    await post({uuid: a, session: s3, share: false, peers: []});
    result = await post({uuid: b, session: s2, share: true, peers: [a]});
    assert.equal(result.body.active[a], undefined, 'sharing disabled removes badge');
    await post({uuid: a, session: s1, share: true, peers: []});
    time += 60001;
    result = await post({uuid: b, session: s2, share: true, peers: [a]});
    assert.equal(result.body.active[a], undefined, 'abandoned session expires');
    assert.equal((await post({uuid: '../invalid', session: s1, share: true, peers: []})).status, 400);
    assert.equal((await post({uuid: a, session: 'short', share: true, peers: []})).status, 400);
    assert.equal((await post({uuid: a, session: s1, share: true, peers: Array(129).fill(b)})).status, 400);
    assert.equal((await post({uuid: a, session: s1, share: true, peers: ['bad']})).status, 400);
    assert.equal((await post({uuid: a, session: s1, share: true, peers: [], padding: 'x'.repeat(9000)})).status, 413);
    assert.equal((await fetch(base + '/api/client-presence')).status, 405);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
