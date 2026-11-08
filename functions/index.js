'use strict';

const { onDocumentWritten, onDocumentUpdated, onDocumentDeleted } = require('firebase-functions/v2/firestore');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const logger = require('firebase-functions/logger');
const events = require('./lib/events');

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

// Same region as the Firestore database (nam5); a small instance cap keeps the cost predictable.
const OPTIONS = { region: 'us-central1', maxInstances: 5 };

const lower = (s) => String(s || '').trim().toLowerCase();

const chunks = (items, size) => {
  const out = [];
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
  return out;
};

/** email -> uid for the given emails. The project owner's uid is known without a lookup. */
async function uidsForEmails(emails, project) {
  const result = new Map();
  if (project.ownerEmail && project.ownerId) result.set(lower(project.ownerEmail), project.ownerId);
  const missing = [...new Set(emails.map(lower))].filter((e) => e && !result.has(e));
  for (const group of chunks(missing, 30)) {
    const snap = await db.collection('users').where('email', 'in', group).get();
    snap.forEach((doc) => {
      const email = lower(doc.get('email'));
      if (!result.has(email)) result.set(email, doc.id);
    });
  }
  return result;
}

/** First name, else last name, else the email itself (the same rule the app uses). */
const nameCache = new Map();
async function displayNameFor(email) {
  const key = lower(email);
  if (!key) return 'Someone';
  if (nameCache.has(key)) return nameCache.get(key);
  let name = key;
  try {
    const snap = await db.collection('users').where('email', '==', key).limit(1).get();
    if (!snap.empty) {
      const data = snap.docs[0].data();
      name = String(data.firstName || '').trim() || String(data.lastName || '').trim() || key;
    }
  } catch (err) {
    logger.warn('Could not look up a display name', { err: String(err) });
  }
  nameCache.set(key, name);
  return name;
}

/** Sends every event to the devices of the project's members, except the device that made the change. */
async function deliver(projectEvents, project) {
  if (!projectEvents.length) return;

  const everyone = events.memberEmailsOf(project);
  const wanted = new Set(everyone);
  projectEvents.forEach((e) => (e.only || []).forEach((m) => wanted.add(lower(m))));
  const uids = await uidsForEmails([...wanted], project);

  const deviceCache = new Map();
  const devicesOf = async (uid) => {
    if (!deviceCache.has(uid)) {
      const snap = await db.collection('users').doc(uid).collection('devices').get();
      deviceCache.set(uid, snap.docs);
    }
    return deviceCache.get(uid);
  };

  const outgoing = []; // { message, ref }
  for (const event of projectEvents) {
    const targetEmails = event.only ? event.only.map(lower) : everyone;
    const who = await displayNameFor(event.actorEmail);
    const text = event.text.replace('{who}', who);
    for (const email of new Set(targetEmails)) {
      const uid = uids.get(email);
      if (!uid) continue;
      for (const device of await devicesOf(uid)) {
        const { token, deviceId } = device.data();
        if (!token) continue;
        // The device that made the change already knows about it.
        if (event.actorDevice && deviceId === event.actorDevice) continue;
        outgoing.push({
          ref: device.ref,
          message: {
            token,
            data: { key: event.key, title: event.title, text, recipientUid: uid },
            // High priority wakes the app even when it is not running; an hour is plenty for a
            // "just now" notification, an old one is not worth showing.
            android: { priority: 'high', ttl: 60 * 60 * 1000 },
          },
        });
      }
    }
  }
  if (!outgoing.length) return;

  for (const group of chunks(outgoing, 500)) {
    const response = await messaging.sendEach(group.map((o) => o.message));
    await Promise.all(response.responses.map(async (r, i) => {
      if (r.success) return;
      const code = r.error && r.error.code;
      if (code === 'messaging/registration-token-not-registered' || code === 'messaging/invalid-registration-token') {
        // The app was uninstalled or the token rotated: forget it.
        await group[i].ref.delete().catch(() => {});
      } else {
        logger.warn('Push failed', { code, message: r.error && r.error.message });
      }
    }));
  }
  logger.info('Pushes sent', { events: projectEvents.map((e) => e.key), messages: outgoing.length });
}

/** A list changed: it was created, renamed, deleted, or one of its tasks was added/edited/completed/deleted. */
exports.onListWritten = onDocumentWritten(
  { ...OPTIONS, document: 'projects/{projectId}/lists/{listId}' },
  async (event) => {
    const before = event.data.before.exists ? event.data.before.data() : null;
    const after = event.data.after.exists ? event.data.after.data() : null;

    const projectSnap = await db.collection('projects').doc(event.params.projectId).get();
    // The project is gone, or is being deleted as a whole ("project deleted" covers it).
    if (!projectSnap.exists) return;
    const project = projectSnap.data();
    if (project.deleting || !events.isShared(project)) return;

    await deliver(events.listEvents({ listId: event.params.listId, before, after, project }), project);
  }
);

/** A project changed: somebody was invited to it or removed from it. */
exports.onProjectUpdated = onDocumentUpdated(
  { ...OPTIONS, document: 'projects/{projectId}' },
  async (event) => {
    const before = event.data.before.data();
    const after = event.data.after.data();
    await deliver(events.projectUpdateEvents({ projectId: event.params.projectId, before, after }), after);
  }
);

/** A shared project was deleted. */
exports.onProjectDeleted = onDocumentDeleted(
  { ...OPTIONS, document: 'projects/{projectId}' },
  async (event) => {
    const before = event.data.data();
    await deliver(events.projectDeleteEvents({ projectId: event.params.projectId, before }), before);
  }
);
