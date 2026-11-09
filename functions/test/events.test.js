'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const ev = require('../lib/events');

const task = (over = {}) => ({
  id: 't1', title: 'Milk', notes: '', isCompleted: false, lastUpdatedBy: 'anna@x.com', ...over,
});
const list = (tasks = {}, over = {}) => ({
  name: 'Groceries', lastUpdatedBy: 'anna@x.com', lastUpdatedByDevice: 'dev-anna', tasks, ...over,
});

test('isShared / memberEmailsOf', () => {
  assert.equal(ev.isShared({ memberEmails: [] }), false);
  assert.equal(ev.isShared({ memberEmails: ['b@x.com'] }), true);
  assert.deepEqual(
    ev.memberEmailsOf({ ownerEmail: 'A@x.com', memberEmails: ['b@x.com', 'a@x.com'] }),
    ['a@x.com', 'b@x.com'],
  );
});

test('list created', () => {
  const out = ev.listEvents({ listId: 'l1', before: null, after: list(), project: {} });
  assert.equal(out.length, 1);
  assert.equal(out[0].title, 'List added');
  assert.equal(out[0].key, 'list:l1');
  assert.equal(out[0].actorDevice, 'dev-anna');
});

test('list deleted: the actor comes from the project', () => {
  const out = ev.listEvents({
    listId: 'l1', before: list(), after: null,
    project: { lastUpdatedBy: 'bob@x.com', lastUpdatedByDevice: 'dev-bob' },
  });
  assert.equal(out[0].title, 'List deleted');
  assert.equal(out[0].actorEmail, 'bob@x.com');
  assert.equal(out[0].actorDevice, 'dev-bob');
});

test('list renamed', () => {
  const out = ev.listEvents({ listId: 'l1', before: list(), after: list({}, { name: 'Shopping' }), project: {} });
  assert.equal(out.length, 1);
  assert.equal(out[0].title, 'List renamed');
  assert.match(out[0].text, /'Groceries' to 'Shopping'/);
});

test('task added, edited, completed, reopened, deleted', () => {
  const base = list({ t1: task() });

  let out = ev.listEvents({ listId: 'l1', before: list(), after: base, project: {} });
  assert.deepEqual(out.map((e) => e.title), ['Task added']);

  out = ev.listEvents({ listId: 'l1', before: base, after: list({ t1: task({ title: 'Oat milk' }) }), project: {} });
  assert.deepEqual(out.map((e) => e.title), ['Task edited']);

  out = ev.listEvents({ listId: 'l1', before: base, after: list({ t1: task({ isCompleted: true }) }), project: {} });
  assert.deepEqual(out.map((e) => e.title), ['Task done']);
  assert.equal(out[0].text, "{who} marked done 'Milk'");

  out = ev.listEvents({
    listId: 'l1',
    before: list({ t1: task({ isCompleted: true }) }),
    after: list({ t1: task({ isCompleted: false }) }),
    project: {},
  });
  assert.deepEqual(out.map((e) => e.title), ['Task reopened']);

  out = ev.listEvents({ listId: 'l1', before: base, after: list({}, { lastUpdatedBy: 'bob@x.com' }), project: {} });
  assert.deepEqual(out.map((e) => e.title), ['Task deleted']);
  assert.equal(out[0].actorEmail, 'bob@x.com');
  assert.equal(out[0].key, 'task:t1');
});

test('legacy "completed" field is understood', () => {
  const out = ev.listEvents({
    listId: 'l1',
    before: list({ t1: { title: 'A', completed: false } }),
    after: list({ t1: { title: 'A', completed: true } }),
    project: {},
  });
  assert.deepEqual(out.map((e) => e.title), ['Task done']);
});

test('a write that changes nothing visible is silent', () => {
  const same = list({ t1: task() });
  const out = ev.listEvents({ listId: 'l1', before: same, after: list({ t1: task({ notes: '  ' }) }), project: {} });
  assert.equal(out.length, 0);
});

test('project: invited and removed members', () => {
  const before = { name: 'Home', ownerEmail: 'anna@x.com', memberEmails: ['bob@x.com'] };
  const after = { ...before, memberEmails: ['carol@x.com'], lastUpdatedBy: 'anna@x.com', lastUpdatedByDevice: 'dev-anna' };
  const out = ev.projectUpdateEvents({ projectId: 'p1', before, after });
  assert.equal(out.length, 2);
  const invited = out.find((e) => e.title === 'Project shared with you');
  const removed = out.find((e) => e.title === 'Project removed');
  assert.deepEqual(invited.only, ['carol@x.com']);
  assert.deepEqual(removed.only, ['bob@x.com']);
  assert.equal(invited.key, 'project-shared:p1');
});

test('project: unrelated updates are silent', () => {
  const p = { name: 'Home', description: 'Chores', memberEmails: ['bob@x.com'] };
  assert.equal(ev.projectUpdateEvents({ projectId: 'p1', before: p, after: { ...p, colorHex: '#000000' } }).length, 0);
  // Not shared with anybody: nobody to tell.
  const mine = { name: 'Mine', memberEmails: [] };
  assert.equal(ev.projectUpdateEvents({ projectId: 'p1', before: mine, after: { ...mine, name: 'Still mine' } }).length, 0);
});

test('project edited: the description is quoted only when it changed', () => {
  const p = { name: 'Home', description: 'Chores', ownerEmail: 'anna@x.com', memberEmails: ['bob@x.com'] };
  const edit = (over) => ev.projectUpdateEvents({
    projectId: 'p1', before: p, after: { ...p, lastUpdatedBy: 'bob@x.com', lastUpdatedByDevice: 'dev-bob', ...over },
  });

  let out = edit({ name: 'Our home' });
  assert.deepEqual(out.map((e) => e.title), ['Project edited']);
  assert.equal(out[0].text, "{who} edited 'Our home'");
  assert.equal(out[0].key, 'project:p1');
  assert.equal(out[0].actorEmail, 'bob@x.com');
  assert.equal(out[0].actorDevice, 'dev-bob');
  assert.equal(out[0].only, undefined);

  out = edit({ description: 'Chores\nand  repairs' });
  assert.equal(out[0].text, "{who} edited 'Home', Chores and repairs");

  out = edit({ description: '' });
  assert.equal(out[0].text, "{who} edited 'Home'");
});

test('task edited: the description is quoted only when it changed', () => {
  const base = list({ t1: task({ notes: 'Two litres' }) });
  const edit = (over) => ev.listEvents({
    listId: 'l1', before: base, after: list({ t1: task({ notes: 'Two litres', ...over }) }), project: {},
  });

  assert.equal(edit({ title: 'Oat milk' })[0].text, "{who} edited 'Oat milk'");
  assert.equal(edit({ notes: 'Three litres,\nlactose free' })[0].text, "{who} edited 'Milk', Three litres, lactose free");
  assert.equal(edit({ notes: '' })[0].text, "{who} edited 'Milk'");

  const long = edit({ notes: 'x'.repeat(300) })[0].text;
  assert.equal(long, `{who} edited 'Milk', ${'x'.repeat(120)}…`);
});

test('project deleted: only shared projects are announced', () => {
  assert.equal(ev.projectDeleteEvents({ projectId: 'p1', before: { name: 'Mine', memberEmails: [] } }).length, 0);
  const out = ev.projectDeleteEvents({
    projectId: 'p1',
    before: { name: 'Home', ownerEmail: 'anna@x.com', memberEmails: ['bob@x.com'], lastUpdatedByDevice: 'dev-anna' },
  });
  assert.equal(out.length, 1);
  assert.equal(out[0].title, 'Project deleted');
  assert.equal(out[0].actorEmail, 'anna@x.com');
  assert.equal(out[0].actorDevice, 'dev-anna');
});
