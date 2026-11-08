const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = "demo-no-project";
let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8085
    }
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("unauthenticated user cannot read or write projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("projects").doc("proj1").get());
  await assertFails(unauthDb.collection("projects").doc("proj1").set({
    ownerId: "user1",
    title: "Project 1"
  }));
});

test("authenticated user can create own project and read it", async () => {
  const user1Db = testEnv.authenticatedContext("user1", { email: "user1@example.com" }).firestore();
  await assertSucceeds(user1Db.collection("projects").doc("proj1").set({
    ownerId: "user1",
    title: "Project 1",
    memberEmails: ["user2@example.com"],
    sharedWith: []
  }));

  const doc = await assertSucceeds(user1Db.collection("projects").doc("proj1").get());
  // user2 can read since user2's email is in memberEmails
  const user2Db = testEnv.authenticatedContext("user2", { email: "user2@example.com" }).firestore();
  await assertSucceeds(user2Db.collection("projects").doc("proj1").get());

  // user3 cannot read
  const user3Db = testEnv.authenticatedContext("user3", { email: "user3@example.com" }).firestore();
  await assertFails(user3Db.collection("projects").doc("proj1").get());
});

test("user cannot create project with invalid schema", async () => {
  const user1Db = testEnv.authenticatedContext("user1", { email: "user1@example.com" }).firestore();
  // missing title
  await assertFails(user1Db.collection("projects").doc("proj1").set({
    ownerId: "user1"
  }));
});
