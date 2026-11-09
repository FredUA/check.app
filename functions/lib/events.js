'use strict';

/**
 * Pure rules for "what happened, and what should we tell people". No Firebase calls in here, so it can be
 * unit tested. It mirrors what the app itself announces (keys match the app's, so a push and an in-app
 * tray notification about the same thing replace each other instead of piling up).
 *
 * An event looks like:
 *   { key, title, text, actorEmail, actorDevice, only? }
 * - text may contain "{who}", replaced by the actor's name when sending
 * - only: restrict the recipients to these emails (default: everybody in the project)
 */

const lower = (s) => String(s || '').trim().toLowerCase();

const nameOf = (o) => (o && (o.name || o.title)) || '';

/** A project counts as shared when somebody besides the owner is a member. */
function isShared(project) {
  return !!project && Array.isArray(project.memberEmails) && project.memberEmails.length > 0;
}

/** Everybody who should hear about changes in the project: the owner and the invited members. */
function memberEmailsOf(project) {
  const all = [project && project.ownerEmail, ...((project && project.memberEmails) || [])]
    .map(lower)
    .filter(Boolean);
  return [...new Set(all)];
}

// A description quoted in an "edited" notification is cut to this many characters (same as in the app).
const MAX_DESCRIPTION = 120;

/**
 * ", <new description>" to append to an "edited" text when the description is what changed, else "".
 * Shown on one line, so line breaks are collapsed and a long description is cut.
 */
function descriptionSuffix(before, after) {
  const now = String(after || '').trim();
  if (String(before || '').trim() === now || !now) return '';
  const oneLine = now.replace(/\s+/g, ' ');
  return `, ${oneLine.length > MAX_DESCRIPTION ? `${oneLine.slice(0, MAX_DESCRIPTION).trimEnd()}…` : oneLine}`;
}

function tasksOf(list) {
  return list && list.tasks && typeof list.tasks === 'object' ? list.tasks : {};
}

/**
 * Events for a write to /projects/{id}/lists/{id}.
 * @param before list data before the write, or null if it was created
 * @param after  list data after the write, or null if it was deleted
 * @param project the project document (its lastUpdatedBy*, set before a list is deleted, tell who deleted it)
 */
function listEvents({ listId, before, after, project }) {
  const events = [];

  if (!before && after) {
    events.push({
      key: `list:${listId}`,
      title: 'List added',
      text: `{who} added '${nameOf(after)}'`,
      actorEmail: after.lastUpdatedBy,
      actorDevice: after.lastUpdatedByDevice,
    });
    return events;
  }

  if (before && !after) {
    events.push({
      key: `list:${listId}`,
      title: 'List deleted',
      text: `{who} deleted '${nameOf(before)}'`,
      actorEmail: project && project.lastUpdatedBy,
      actorDevice: project && project.lastUpdatedByDevice,
    });
    return events;
  }

  if (!before || !after) return events;

  const actorDevice = after.lastUpdatedByDevice;

  if (nameOf(before) !== nameOf(after)) {
    events.push({
      key: `list:${listId}`,
      title: 'List renamed',
      text: `{who} renamed '${nameOf(before)}' to '${nameOf(after)}'`,
      actorEmail: after.lastUpdatedBy,
      actorDevice,
    });
  }

  const beforeTasks = tasksOf(before);
  const afterTasks = tasksOf(after);

  for (const [id, task] of Object.entries(afterTasks)) {
    const previous = beforeTasks[id];
    const actorEmail = task.lastUpdatedBy || after.lastUpdatedBy;
    if (!previous) {
      events.push({
        key: `task:${id}`,
        title: 'Task added',
        text: `{who} added '${task.title || ''}'`,
        actorEmail,
        actorDevice,
      });
      continue;
    }
    const wasDone = !!(previous.isCompleted ?? previous.completed);
    const isDone = !!(task.isCompleted ?? task.completed);
    if (wasDone !== isDone) {
      events.push({
        key: `task:${id}`,
        title: isDone ? 'Task done' : 'Task reopened',
        text: `{who} ${isDone ? 'marked done' : 'reopened'} '${task.title || ''}'`,
        actorEmail,
        actorDevice,
      });
    }
    if (String(previous.title || '').trim() !== String(task.title || '').trim() ||
        String(previous.notes || '').trim() !== String(task.notes || '').trim()) {
      events.push({
        key: `task:${id}`,
        title: 'Task edited',
        text: `{who} edited '${task.title || ''}'${descriptionSuffix(previous.notes, task.notes)}`,
        actorEmail,
        actorDevice,
      });
    }
  }

  for (const [id, task] of Object.entries(beforeTasks)) {
    if (afterTasks[id]) continue;
    // The task is gone, so it can't say who removed it; the list records the last editor.
    events.push({
      key: `task:${id}`,
      title: 'Task deleted',
      text: `{who} deleted '${task.title || ''}'`,
      actorEmail: after.lastUpdatedBy,
      actorDevice,
    });
  }

  return events;
}

/** Events for an update of /projects/{id}: its name or description edited, people being invited or removed. */
function projectUpdateEvents({ projectId, before, after }) {
  const events = [];
  const was = new Set((before.memberEmails || []).map(lower));
  const now = new Set((after.memberEmails || []).map(lower));
  const title = nameOf(after) || 'Untitled project';
  const actorEmail = after.lastUpdatedBy || after.ownerEmail;

  // (Not while it is being deleted: "project deleted" covers that.)
  if (isShared(after) && !after.deleting &&
      (nameOf(before).trim() !== nameOf(after).trim() ||
       String(before.description || '').trim() !== String(after.description || '').trim())) {
    events.push({
      key: `project:${projectId}`,
      title: 'Project edited',
      text: `{who} edited '${title}'${descriptionSuffix(before.description, after.description)}`,
      actorEmail,
      actorDevice: after.lastUpdatedByDevice,
    });
  }

  const invited = [...now].filter((e) => !was.has(e));
  if (invited.length) {
    events.push({
      key: `project-shared:${projectId}`,
      title: 'Project shared with you',
      text: `{who} shared '${title}' with you`,
      actorEmail,
      actorDevice: after.lastUpdatedByDevice,
      only: invited,
    });
  }

  const removed = [...was].filter((e) => !now.has(e));
  if (removed.length) {
    events.push({
      key: `project:${projectId}`,
      title: 'Project removed',
      text: `{who} removed you from '${title}'`,
      actorEmail,
      actorDevice: after.lastUpdatedByDevice,
      only: removed,
    });
  }
  return events;
}

/** Events for the deletion of /projects/{id}. `before` is the project as it was. */
function projectDeleteEvents({ projectId, before }) {
  if (!isShared(before)) return [];
  return [{
    key: `project:${projectId}`,
    title: 'Project deleted',
    text: `{who} deleted '${nameOf(before) || 'Untitled project'}'`,
    actorEmail: before.lastUpdatedBy || before.ownerEmail,
    actorDevice: before.lastUpdatedByDevice,
  }];
}

module.exports = {
  isShared,
  memberEmailsOf,
  listEvents,
  projectUpdateEvents,
  projectDeleteEvents,
};
