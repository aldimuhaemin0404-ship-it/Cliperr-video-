const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(userId, now = new Date(Date.now() - 2000)) {
  return {
    userId,
    localProjectId: 10,
    title: "Podcast Kreator 2026",
    sourceUri: "content://media/external/video/media/10",
    durationMs: 65000,
    width: 1080,
    height: 1920,
    targetPlatform: "REELS_TIKTOK",
    focusTopic: "Strategi konten",
    sourcePlatformName: "YouTube",
    authorName: "Aldi Creator",
    hasPlayableStream: true,
    placeholderAi: false,
    createdAt: now,
    updatedAt: now,
  };
}

function validSegmentPayload(
  userId,
  projectCloudId,
  now = new Date(Date.now() - 2000)
) {
  return {
    userId,
    projectCloudId,
    localProjectId: 10,
    localSegmentId: 101,
    title: "Hook Pembuka Viral",
    hookLine: "Jangan lewatkan rahasia ini!",
    viralScore: 95,
    rationale: "Energi visual tinggi di 3 detik pertama.",
    captionText: "Strategi konten cepat untuk kreator.",
    hashtags: "#AldiAIClipper #Reels",
    startMs: 1000,
    endMs: 16000,
    aspectRatio: "VERTICAL_9_16",
    tone: "ENERGETIC",
    videoFilter: "VIVID",
    brightness: 0.15,
    showSubtitles: true,
    showIntroHook: true,
    hookDurationMs: 3000,
    hookOverlayStyle: "NEON_BADGE",
    transitionStyle: "FADE",
    transitionDurationMs: 600,
    includeAudio: true,
    selectedMusicTrackId: "rf_neon_pulse",
    selectedMusicTitle: "Neon Horizon Pulse",
    selectedMusicUrl: "synth://rf_neon_pulse",
    musicVolume: 0.35,
    audioMoodLabel: "Upbeat / Enerjik",
    audioBpm: 124,
    exportQuality: "HD_1080P",
    favorite: false,
    placeholderAi: false,
    createdAt: now,
    updatedAt: now,
  };
}

function validExportPayload(
  userId,
  projectCloudId,
  now = new Date(Date.now() - 2000)
) {
  return {
    userId,
    projectCloudId,
    localExportId: 501,
    localProjectId: 10,
    localSegmentId: 101,
    title: "Hook Pembuka Viral",
    filePath: "/data/user/0/com.aistudio.aiclipper.vkxmqz/files/exports/clip_501.mp4",
    durationMs: 15000,
    fileSizeBytes: 4250000,
    aspectRatio: "PORTRAIT_4_5",
    galleryUri: "content://media/external/video/media/999",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
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

test("Unauthenticated requests are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_10")
      .set(validProjectPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("segments").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("exports").get()
  );
});

test("Owner can create and update user profile, but cannot list /users or spoof uid", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Creator",
      email: "alice@example.com",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Pro",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Listing users collection must fail (PII isolation)
  await assertFails(aliceDb.collection("users").get());

  // Cannot create profile for Bob
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Spoof",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Owner can create/update/list projects, segments, and exports mirroring Kotlin payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Create project (no `id` field inside body because Kotlin uses @DocumentId)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_10")
      .set(validProjectPayload(ALICE_UID, now))
  );

  // Create segment referencing existing projectCloudId
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("segments")
      .doc("seg_101")
      .set(validSegmentPayload(ALICE_UID, "proj_10", now))
  );

  // Partial update on segment (favorite toggle + transition + quality)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("segments")
      .doc("seg_101")
      .update({
        favorite: true,
        transitionStyle: "SLIDE",
        aspectRatio: "PORTRAIT_4_5",
        exportQuality: "HD_720P",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Create exported clip
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("exports")
      .doc("exp_501")
      .set(validExportPayload(ALICE_UID, "proj_10", now))
  );

  // List queries matching Kotlin repository
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("segments")
      .where("projectCloudId", "==", "proj_10")
      .get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("exports").get()
  );
});

test("Security bounds reject cross-user access, @DocumentId id key, orphaned segments, immutable changes, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_10")
      .set(validProjectPayload(ALICE_UID, now))
  );

  // Cross-user read/list/write denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_10")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_10")
      .update({ title: "Hijacked", updatedAt: new Date(Date.now() - 1000) })
  );

  // Reject @DocumentId `id` field inside document body
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_bad_id")
      .set({
        id: "proj_bad_id",
        ...validProjectPayload(ALICE_UID, now),
      })
  );

  // Reject orphaned segment referencing missing parent project
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("segments")
      .doc("seg_orphan")
      .set(validSegmentPayload(ALICE_UID, "proj_missing", now))
  );

  // Reject tampered future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_future")
      .set(validProjectPayload(ALICE_UID, new Date(Date.now() + 60000)))
  );

  // Reject immutable createdAt mutation on update
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_10")
      .update({
        createdAt: new Date(Date.now() - 100),
        updatedAt: new Date(Date.now() - 100),
      })
  );

  // Reject oversized title (> 200 chars)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_oversize")
      .set({
        ...validProjectPayload(ALICE_UID, now),
        title: "A".repeat(250),
      })
  );
});
