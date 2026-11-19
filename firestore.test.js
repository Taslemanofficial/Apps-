const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_creator_1";
const BOB_UID = "bob_creator_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

// --- DESTINATION SECURITY & PAYLOAD PARITY TESTS ---

test("Unauthenticated user: cannot read or write destinations or sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("destinations").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("destinations")
      .doc("yt_1")
      .set({
        userId: ALICE_UID,
        platform: "YOUTUBE",
        label: "YouTube Live",
        rtmpUrl: "rtmp://a.rtmp.youtube.com/live2",
        streamKey: "abcd-1234",
        enabled: true,
        createdAt: past,
        updatedAt: past,
      })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );
});

test("Authenticated owner: valid Kotlin destination create, partial update, toggle, list, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const destRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("destinations")
    .doc("dest_youtube");

  // Create matching StreamRepository.upsertDestination payload (no @DocumentId `id` field)
  await assertSucceeds(
    destRef.set({
      userId: ALICE_UID,
      platform: "YOUTUBE",
      label: "YouTube Main",
      rtmpUrl: "rtmp://a.rtmp.youtube.com/live2",
      streamKey: "",
      enabled: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List subcollection (mirrors observeDestinations)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("destinations").get()
  );

  // Partial edit (Action 1)
  await assertSucceeds(
    destRef.update({
      label: "YouTube Studio 4K",
      rtmpUrl: "rtmps://a.rtmps.youtube.com/live2",
      streamKey: "yt-live-key-9988",
      enabled: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Toggle switch (Action 2)
  await assertSucceeds(
    destRef.update({
      enabled: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject shadow field injection
  await assertFails(
    destRef.update({
      enabled: true,
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject non-rtmp URL protocol
  await assertFails(
    destRef.update({
      rtmpUrl: "https://evil.example.com/live",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    destRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject extra @DocumentId field on fresh create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("destinations")
      .doc("dest_fb")
      .set({
        id: "dest_fb",
        userId: ALICE_UID,
        platform: "FACEBOOK",
        label: "Facebook Page",
        rtmpUrl: "rtmps://live-api-s.facebook.com:443/rtmp/",
        streamKey: "FB-123",
        enabled: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Delete succeeds
  await assertSucceeds(destRef.delete());
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's destinations", async () => {
  const past = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("destinations")
      .doc("dest_ig")
      .set({
        userId: ALICE_UID,
        platform: "INSTAGRAM",
        label: "Instagram Live",
        rtmpUrl: "rtmps://live-upload.instagram.com:443/rtmp/",
        streamKey: "ig-secret-key",
        enabled: true,
        createdAt: past,
        updatedAt: past,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDestRef = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("destinations")
    .doc("dest_ig");

  await assertFails(aliceDestRef.get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("destinations").get()
  );
  await assertFails(
    aliceDestRef.update({ enabled: false, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(aliceDestRef.delete());
});

// --- BROADCAST SESSION LIFECYCLE & TERMINAL LOCK TESTS ---

test("Broadcast sessions: create LIVE, update telemetry, complete with terminal lock, reject post-completion edits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const sessionRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sessions")
    .doc("sess_001");

  // Create LIVE session
  await assertSucceeds(
    sessionRef.set({
      userId: ALICE_UID,
      title: "Evening Multi-Stream",
      videoCount: 2,
      platforms: ["YOUTUBE", "FACEBOOK", "INSTAGRAM"],
      status: "LIVE",
      durationSec: 0,
      avgBitrateKbps: 0,
      totalBytesSent: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Periodic live telemetry update
  await assertSucceeds(
    sessionRef.update({
      durationSec: 45,
      avgBitrateKbps: 2800,
      totalBytesSent: 15750000,
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  // Transition to terminal COMPLETED state
  await assertSucceeds(
    sessionRef.update({
      status: "COMPLETED",
      durationSec: 120,
      avgBitrateKbps: 2950,
      totalBytesSent: 44250000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: subsequent update MUST fail
  await assertFails(
    sessionRef.update({
      durationSec: 999,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp tampering on new session
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_future")
      .set({
        userId: ALICE_UID,
        title: "Future Tamper",
        videoCount: 1,
        platforms: ["YOUTUBE"],
        status: "LIVE",
        durationSec: 0,
        avgBitrateKbps: 0,
        totalBytesSent: 0,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // List sessions succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );

  // Owner delete succeeds
  await assertSucceeds(sessionRef.delete());
});

// --- NEWS DESK PRESETS SECURITY & PAYLOAD PARITY TESTS ---

test("NewsDeskPreset: owner create, live partial update, list, cross-user denial, and bounds validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 8000);
  const presetRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("newsPresets")
    .doc("active_desk");

  await assertSucceeds(
    presetRef.set({
      userId: ALICE_UID,
      channelName: "StreamNews 24",
      locationTag: "ঢাকা, বাংলাদেশ",
      breakingTag: "ব্রেকিং নিউজ",
      headline: "দেশজুড়ে সরাসরি সংবাদ সম্প্রচার শুরু হয়েছে",
      tickerText: "সর্বশেষ সংবাদ: একসাথে ইউটিউব, ফেসবুক ও ইনস্টাগ্রামে লাইভ সম্প্রচার চলছে।",
      newsScript: "সুপ্রিয় দর্শক, স্বাগতম আমাদের সরাসরি সংবাদ বুলেটিনে।",
      ttsLanguage: "BN",
      ttsSpeed: 1.0,
      ttsPitch: 1.0,
      tickerSpeed: "NORMAL",
      newsModeEnabled: true,
      ttsEnabled: true,
      showLogo: true,
      showTicker: true,
      showHeadline: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Quick live bulletin update (Action 2)
  await assertSucceeds(
    presetRef.update({
      breakingTag: "সর্বশেষ",
      headline: "নতুন আপডেট যুক্ত হয়েছে",
      tickerText: "নতুন টিকার আপডেট চলছে",
      newsScript: "এইমাত্র পাওয়া সর্বশেষ সংবাদ।",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // List presets succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("newsPresets").get()
  );

  // Reject out-of-range TTS speed
  await assertFails(
    presetRef.update({
      ttsSpeed: 5.0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject shadow field injection
  await assertFails(
    presetRef.update({
      headline: "Injected",
      isVerified: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user isolation: Bob cannot read or update Alice's news presets
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("newsPresets").doc("active_desk").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("newsPresets").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("newsPresets").doc("active_desk").update({
      headline: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Owner delete succeeds
  await assertSucceeds(presetRef.delete());
});

