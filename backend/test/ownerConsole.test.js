const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const { installOwnerConsole } = require('../src/ownerConsole');

test('private web console verifies an emailed code and rejects consumer-app bearer access', async () => {
  const issued = [];
  const rows = [];
  const emails = [];
  const pool = {
    async query(sql, params = []) {
      if (sql.includes('SELECT id FROM users WHERE lower(email)')) return { rows: params[0] === 'owner@example.com' ? [{ id: 'owner-id' }] : [] };
      if (sql.includes('SELECT created_at FROM owner_console_codes')) return { rows: rows.map(row => ({ created_at: row.created_at })) };
      if (sql.includes('INSERT INTO owner_console_codes')) {
        const record = { id: `code-${rows.length + 1}`, email: params[0], code_hash: params[1], created_at: new Date(), expires_at: new Date(Date.now() + 600000), attempts: 0 };
        rows.unshift(record); return { rows: [{ id: record.id }] };
      }
      if (sql.includes('SELECT id, code_hash, expires_at, attempts')) return { rows: rows.filter(row => !row.used_at).slice(0, 1) };
      if (sql.includes('UPDATE owner_console_codes SET attempts')) { rows[0].attempts++; return { rows: [] }; }
      if (sql.includes('SELECT google_sub FROM users')) return { rows: [{ google_sub: 'google-owner' }] };
      if (sql.includes('UPDATE owner_console_codes SET used_at')) { rows[0].used_at = new Date(); return { rows: [] }; }
      if (sql.includes('SELECT id FROM users WHERE google_sub')) return { rows: params[0] === 'google-owner' && params[1] === 'owner@example.com' ? [{ id: 'owner-id' }] : [] };
      if (sql.includes('FROM user_feedback')) return { rows: [{ id: 'f1', user_email: 'member@example.com', message: 'Add a widget', attachments: [], created_at: new Date() }] };
      if (sql.includes('FROM bug_reports')) return { rows: [] };
      if (sql.includes('DELETE FROM owner_console_codes')) return { rows: [] };
      if (sql === 'BEGIN' || sql === 'COMMIT' || sql === 'ROLLBACK') return { rows: [] };
      throw new Error('Unexpected SQL: ' + sql.slice(0, 100));
    },
    async connect() { return { query: this.query.bind(this), release() {} }; }
  };
  const app = express(); app.use(express.json());
  installOwnerConsole(app, { pool, jwtSecret: 'test-secret', adminEmails: ['owner@example.com'], r2: { signedImageUrl: async () => null },
    sendCode: async value => { emails.push(value); }, origin: 'https://owner.example.com' });
  const server = await new Promise(resolve => { const s = app.listen(0, '127.0.0.1', () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = (path, body, extra = {}) => fetch(base + path, { method: 'POST', headers: { Origin: 'https://owner.example.com', 'Content-Type': 'application/json', ...extra }, body: JSON.stringify(body) });
  try {
    const page = await fetch(base + '/owner'); assert.equal(page.status, 200);
    assert.match(page.headers.get('content-security-policy'), /frame-ancestors 'none'/);
    assert.equal((await fetch(base + '/api/owner/opinions', { headers: { Authorization: 'Bearer consumer-app-session' } })).status, 401);
    assert.equal((await fetch(base + '/api/admin/feedback')).status, 404);
    assert.equal((await post('/api/owner/request-code', { email: 'owner@example.com' }, { Origin: 'https://evil.example' })).status, 403);
    assert.equal((await post('/api/owner/request-code', { email: 'other@example.com' })).status, 200);
    assert.equal(emails.length, 0);
    assert.equal((await post('/api/owner/request-code', { email: 'owner@example.com' })).status, 200);
    assert.equal(emails.length, 1);
    assert.equal((await post('/api/owner/verify-code', { email: 'owner@example.com', code: '000000' })).status, 401);
    const response = await post('/api/owner/verify-code', { email: 'owner@example.com', code: emails[0].code });
    assert.equal(response.status, 200);
    const setCookie = response.headers.get('set-cookie');
    assert.match(setCookie, /HttpOnly; Secure; SameSite=Strict; Path=\/api\/owner/);
    const cookie = setCookie.split(';')[0];
    const inbox = await fetch(base + '/api/owner/opinions', { headers: { Cookie: cookie } });
    assert.equal(inbox.status, 200); assert.equal((await inbox.json()).feedback[0].message, 'Add a widget');
    assert.equal((await fetch(base + '/api/owner/bug-reports', { headers: { Cookie: cookie } })).status, 200);
    assert.equal((await post('/api/owner/verify-code', { email: 'owner@example.com', code: emails[0].code })).status, 401);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
