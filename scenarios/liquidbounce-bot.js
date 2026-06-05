#!/usr/bin/env node
const mineflayer = require('mineflayer');
const Vec3 = require('vec3');

const args = process.argv.slice(2);
function arg(name, fallback) {
  const idx = args.indexOf('--' + name);
  return idx >= 0 && args[idx + 1] ? args[idx + 1] : fallback;
}

const HOST = arg('host', '127.0.0.1');
const PORT = parseInt(arg('port', '25577'), 10);
const VERSION = arg('version', null);
const TIMEOUT = parseInt(arg('timeout', '900'), 10) * 1000;

let done = false;
function finish(code, msg) {
  if (done) return;
  done = true;
  if (msg) console.log(msg);
  setTimeout(() => process.exit(code), 500);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function clearControls(bot) {
  for (const key of ['forward', 'back', 'left', 'right', 'jump', 'sprint', 'sneak']) {
    bot.setControlState(key, false);
  }
}

let activeHack = null;
let hackPhase = 'LEGIT'; // LEGIT, CHEAT, LEGIT2
let tickCount = 0;
let packetBypassMode = null; // SPARTAN_MULTI_JUMP, INTAVE_TIMER

// This function simulates the Legit (5s) -> Cheat (5s) -> Legit (5s) rhythm
async function runRhythmHack(bot, hackName, durationMs) {
  console.log(`[bot] executing rhythm scenario for ${hackName}`);

  // Phase 1: Legit
  hackPhase = 'LEGIT';
  activeHack = null;
  packetBypassMode = null;
  bot.physicsEnabled = true; // Ensure standard physics is ON for legit phase
  bot.setControlState('forward', true);
  bot.setControlState('sprint', true);
  await sleep(4000);

  // Phase 2: Cheat
  console.log(`[bot]   -> turning ON cheat ${hackName}`);
  hackPhase = 'CHEAT';
  activeHack = hackName;
  tickCount = 0;
  // Keep physicsEnabled = true to prevent serialization issues and letting mineflayer synchronize
  // position state normally, but we tweak velocity and packets instead.
  bot.physicsEnabled = true;

  if (hackName.includes('Spartan')) {
    packetBypassMode = 'SPARTAN_MULTI_JUMP';
  } else if (hackName.includes('Intave')) {
    packetBypassMode = 'INTAVE_TIMER';
  } else if (hackName === 'FlyVanilla' || hackName === 'FlySpartan') {
    packetBypassMode = 'FLY';
  } else if (hackName.startsWith('AirJump')) {
    packetBypassMode = 'AIR_JUMP';
  } else if (hackName.startsWith('HighJump')) {
    packetBypassMode = 'HIGH_JUMP';
  } else if (hackName === 'Spider') {
    packetBypassMode = 'SPIDER';
  } else if (hackName.startsWith('Step')) {
    packetBypassMode = 'STEP';
  }

  await sleep(durationMs);

  // Phase 3: Legit
  console.log(`[bot]   -> turning OFF cheat ${hackName}`);
  hackPhase = 'LEGIT2';
  activeHack = null;
  packetBypassMode = null;
  bot.physicsEnabled = true; // Restore standard physics for legit verification
  // keep walking legit to see if false flag continues
  bot.setControlState('forward', true);
  bot.setControlState('sprint', true);
  await sleep(4000);

  clearControls(bot);
}

const SAMPLES = [
  { id: 'SpeedSpartanV4043', category: 'SPEED', duration: 4000 },
  { id: 'SpeedIntave14Fast_Var1', category: 'SPEED', duration: 4000 },
  { id: 'SpeedBlocksMC', category: 'SPEED', duration: 4000 },
  { id: 'SpeedVulcan286', category: 'SPEED', duration: 4000 },
  { id: 'FlyVanilla', category: 'FLY', duration: 5000 },
  { id: 'FlySpartan', category: 'FLY', duration: 5000 },
  { id: 'AirJumpFreely', category: 'AIR_JUMP', duration: 5000 },
  { id: 'AirJumpGhostBlock', category: 'AIR_JUMP', duration: 5000 },
  { id: 'HighJumpVanilla', category: 'HIGH_JUMP', duration: 3000 },
  { id: 'HighJumpVulcan', category: 'HIGH_JUMP', duration: 3000 },
  { id: 'LiquidWalk', category: 'LIQUID_WALK', duration: 5000 },
  { id: 'ReverseStepAccelerator', category: 'REVERSE_STEP', duration: 3000 },
  { id: 'ReverseStepInstant', category: 'REVERSE_STEP', duration: 3000 },
  { id: 'ReverseStepStrict', category: 'REVERSE_STEP', duration: 3000 },
  { id: 'Spider', category: 'SPIDER', duration: 5000 },
  { id: 'StepInstant', category: 'STEP', duration: 3000 },
  { id: 'StepVulcan286', category: 'STEP', duration: 3000 },
  { id: 'TerrainSpeed', category: 'TERRAIN_SPEED', duration: 5000 },
  { id: 'VehicleBoost', category: 'VEHICLE_BOOST', duration: 5000 },
  { id: 'VehicleControl', category: 'VEHICLE_CONTROL', duration: 5000 }
];

const botOpts = { host: HOST, port: PORT, username: 'LiquidCheatBot', auth: 'offline' };
if (VERSION) botOpts.version = VERSION;

const bot = mineflayer.createBot(botOpts);

// INTEGRATE PRISMARINE-VIEWER WITH PUPPETEER FOR MP4 RECORDING
let puppeteerProcess = null;
bot.once('spawn', () => {
  try {
    const viewer = require('prismarine-viewer').mineflayer;
    console.log(`[viewer] starting web viewer on port 3000...`);
    viewer(bot, { port: 3000, firstPerson: true, viewDistance: 6 });

    // Start manual cheat interval runner since mineflayer disables physics tick when physicsEnabled = false
    setInterval(() => {
      runCheatTick();
    }, 50);

    // Spawn record browser after a short delay to let viewer start
    setTimeout(async () => {
      try {
        const puppeteer = require('puppeteer');
        const path = require('path');
        const fs = require('fs');
        const { spawn } = require('child_process');

        const outputDir = path.resolve(__dirname, '../../records');
        if (!fs.existsSync(outputDir)) {
          fs.mkdirSync(outputDir, { recursive: true });
        }
        const timestamp = Date.now();
        const outputFile = path.join(outputDir, `LiquidBounce_Run_${timestamp}.mp4`);

        console.log(`[puppeteer] launching browser to capture web viewer...`);
        const browser = await puppeteer.launch({
          headless: 'new',
          args: ['--no-sandbox', '--disable-setuid-sandbox']
        });
        const page = await browser.newPage();
        await page.setViewport({ width: 1280, height: 720 });
        await page.goto('http://127.0.0.1:3000');

        console.log(`[puppeteer] recording stream using ffmpeg...`);
        // We can capture screen frames using page.screenshot periodically or stream it.
        // Let's use a simpler and highly compatible way: write screenshots to ffmpeg pipe.
        const ffmpeg = spawn('ffmpeg', [
          '-y',
          '-f', 'image2pipe',
          '-vcodec', 'png',
          '-r', '15', // 15 fps screenshotting
          '-i', '-',
          '-vcodec', 'libx264',
          '-pix_fmt', 'yuv420p',
          outputFile
        ]);

        ffmpeg.stderr.on('data', (data) => {
          // ignore or log ffmpeg output silently
        });

        const recordInterval = setInterval(async () => {
          if (done) {
            clearInterval(recordInterval);
            try {
              ffmpeg.stdin.end();
              await browser.close();
              console.log(`[puppeteer] browser closed. Video saved to: ${outputFile}`);
            } catch (e) {}
            return;
          }
          try {
            const screenshot = await page.screenshot({ type: 'png' });
            if (ffmpeg.stdin.writable) {
              ffmpeg.stdin.write(screenshot);
            }
          } catch (e) {
            // console.error('[puppeteer] screenshot error:', e.message);
          }
        }, 66); // ~15 FPS

      } catch (err) {
        console.error('[puppeteer] recording failed:', err.message);
      }
    }, 3000);

  } catch (err) {
    console.error('[viewer] Failed to initialize viewer:', err.message);
  }
});

const timer = setTimeout(() => finish(1, 'TIMEOUT'), TIMEOUT);

bot.on('error', err => { console.error('Bot error:', err.message); finish(1, 'ERROR: ' + err.message); });
bot.on('kicked', reason => { console.log('Kicked:', reason); finish(1, 'KICKED'); });

// Helper: clone position/position_look params for 1.21.11 to avoid serialization errors
let lastPosParams = null;

const oldWrite = bot._client.write.bind(bot._client);
bot._client.write = function (name, params) {
  if (name === 'position' || name === 'position_look') {
    if (activeHack && hackPhase === 'CHEAT') {
      // Discard mineflayer's default physics engine position packets while cheating
      // because we're running our own cheat loop
      return;
    }
    // Save valid params to use as a template for raw injections
    lastPosParams = Object.assign({}, params);
  }
  oldWrite(name, params);
};

// Helper: send a position packet directly using a valid mineflayer schema template
function sendPosition(entity, extraDx, extraDy, extraDz, onGround) {
  const p = entity.position;
  p.x += extraDx;
  p.y += extraDy;
  p.z += extraDz;

  if (lastPosParams) {
    const customParams = Object.assign({}, lastPosParams);
    customParams.x = p.x;
    customParams.y = p.y;
    customParams.z = p.z;
    customParams.onGround = onGround;
    try {
      oldWrite('position', customParams);
    } catch (e) {
      // Ignore serialization errors during hack if template is mismatched
    }
  }
}

// PhysicsTick handler: only active when physicsEnabled = true (LEGIT/LEGIT2 phases)
bot.on('physicsTick', () => {
  if (hackPhase === 'LEGIT' || hackPhase === 'LEGIT2') {
     if (tickCount++ % 40 === 0 && bot.entity.onGround) bot.setControlState('jump', true);
     else bot.setControlState('jump', false);
  }
});

// Manual cheat tick function: called by setInterval every 50ms when physicsEnabled = false
function runCheatTick() {
  if (hackPhase !== 'CHEAT' || !activeHack) return;
  tickCount++;
  const entity = bot.entity;
  if (!entity) return;

  const yaw = entity.yaw;
  const dx = -Math.sin(yaw);
  const dz = Math.cos(yaw);
  // Step size per tick (0.05s). Real vanilla walk ~0.218/tick, sprint ~0.255/tick
  const STEP = 0.4;   // "fast" baseline step per tick
  const FSTEP = 0.8;  // fly/spartan step per tick

  switch (activeHack) {
    // ─── SPEED ────────────────────────────────────────────────
    case 'SpeedSpartanV4043':
      // Multi-jump burst: 4 sub-packets simulated in one tick via sendPosition
      sendPosition(entity, dx*0.0, 0.42, dz*0.0, false);
      sendPosition(entity, dx*0.2, 0.28, dz*0.2, false);
      sendPosition(entity, dx*0.4, 0.20, dz*0.4, false);
      sendPosition(entity, dx*1.45, 0.10, dz*1.45, false);
      break;

    case 'SpeedIntave14Fast_Var1':
      // Strafe + timer micro-steps: ~2.5x normal speed
      if (tickCount % 2 === 0) {
        sendPosition(entity, dx*0.52, 0.42, dz*0.52, false); // hop
      } else {
        sendPosition(entity, dx*0.18, -0.0784, dz*0.18, false); // glide tick
      }
      break;

    case 'SpeedVulcan286':
      // Vulcan 2.8.6: low hop, fast ground glide
      if (tickCount % 8 < 2) {
        sendPosition(entity, dx*0.33, 0.42, dz*0.33, false);
      } else if (tickCount % 8 === 4) {
        sendPosition(entity, dx*0.3355, -0.376, dz*0.3355, false);
      } else {
        sendPosition(entity, dx*0.3355, -0.0784, dz*0.3355, false);
      }
      break;

    case 'SpeedBlocksMC':
      // BlocksMC: high hop, accelerate in air
      if (tickCount % 6 === 0) {
        sendPosition(entity, dx*0.6, 0.42, dz*0.6, false);
      } else {
        const boost = 1.0 + (tickCount % 6) * 0.01;
        sendPosition(entity, dx*0.6*boost, -0.0784, dz*0.6*boost, false);
      }
      break;

    // ─── FLY ─────────────────────────────────────────────────
    case 'FlyVanilla':
      // Pure fly: move horizontally at FSTEP, no gravity, constant altitude
      sendPosition(entity, dx*FSTEP, 0, dz*FSTEP, false);
      break;

    case 'FlySpartan':
      // Spartan fly: slight bobbing to simulate "jump fly"
      const bob = Math.sin(tickCount * 0.5) * 0.05;
      sendPosition(entity, dx*FSTEP, bob, dz*FSTEP, false);
      break;

    // ─── AIR JUMP ────────────────────────────────────────────
    case 'AirJumpFreely':
      // Air jump: periodically send onGround=true in mid-air then hop
      if (tickCount % 12 === 0) {
        sendPosition(entity, dx*0.1, 0.42, dz*0.1, true); // spoof ground=true while airborne
      } else {
        sendPosition(entity, dx*0.3, -0.0784, dz*0.3, false);
      }
      break;

    case 'AirJumpGhostBlock':
      // Ghost block air jump: send occasional upward Y spike
      if (tickCount % 15 === 0) {
        sendPosition(entity, dx*0.2, 0.9, dz*0.2, false);
      } else {
        sendPosition(entity, dx*0.3, -0.0784, dz*0.3, false);
      }
      break;

    // ─── HIGH JUMP ───────────────────────────────────────────
    case 'HighJumpVanilla':
      // Double-height jump: big Y spike then glide
      if (tickCount % 20 < 3) {
        sendPosition(entity, dx*0.2, 0.84, dz*0.2, false);
      } else {
        sendPosition(entity, dx*0.3, -0.0784, dz*0.3, false);
      }
      break;

    case 'HighJumpVulcan':
      // Vulcan high jump: fast upward burst
      if (tickCount % 20 < 2) {
        sendPosition(entity, dx*0.15, 1.2, dz*0.15, false);
      } else {
        sendPosition(entity, dx*0.25, -0.0784, dz*0.25, false);
      }
      break;

    // ─── LIQUID WALK ─────────────────────────────────────────
    case 'LiquidWalk':
      // Walk on water: zero gravity, constant horizontal
      sendPosition(entity, dx*0.3, 0, dz*0.3, false);
      break;

    // ─── SPIDER ──────────────────────────────────────────────
    case 'Spider':
      // Climb walls: alternate up + forward
      if (tickCount % 3 === 0) {
        sendPosition(entity, dx*0.1, 0.2, dz*0.1, false);
      } else {
        sendPosition(entity, dx*0.15, 0.15, dz*0.15, false);
      }
      break;

    // ─── STEP ────────────────────────────────────────────────
    case 'StepInstant':
      // Instant 1-block step: every few ticks teleport up 1 block then walk
      if (tickCount % 10 === 0) {
        sendPosition(entity, dx*0.1, 1.0, dz*0.1, true);
      } else {
        sendPosition(entity, dx*0.3, 0, dz*0.3, true);
      }
      break;

    case 'StepVulcan286':
      // Vulcan step: smooth 0.5-block steps
      if (tickCount % 6 === 0) {
        sendPosition(entity, dx*0.2, 0.5, dz*0.2, true);
      } else {
        sendPosition(entity, dx*0.3, 0, dz*0.3, true);
      }
      break;

    // ─── TERRAIN SPEED ──────────────────────────────────────
    case 'TerrainSpeed':
      // Fast terrain movement with periodic small hops
      if (tickCount % 5 === 0) {
        sendPosition(entity, dx*0.6, 0.42, dz*0.6, false);
      } else {
        sendPosition(entity, dx*0.6, -0.0784, dz*0.6, false);
      }
      break;

    // ─── VEHICLE / REVERSE STEP ─────────────────────────────
    case 'VehicleBoost':
    case 'VehicleControl':
    case 'ReverseStepAccelerator':
    case 'ReverseStepInstant':
    case 'ReverseStepStrict':
      // Modified speed with periodic hops
      if (tickCount % 5 === 0) {
        sendPosition(entity, dx*0.4, 0.42, dz*0.4, false);
      } else {
        sendPosition(entity, dx*0.4, -0.0784, dz*0.4, false);
      }
      break;
  }
}

async function buildEnvironment() {
   // Build a track of normal, ice, and soul_sand to test environment false flags and cheat speeds
   console.log("[bot] Building test environment (Ice / Soul Sand)...");
   bot.chat('/fill ~-2 ~-1 ~-2 ~50 ~-1 ~2 minecraft:stone');
   await sleep(500);
   bot.chat('/fill ~51 ~-1 ~-2 ~100 ~-1 ~2 minecraft:blue_ice');
   await sleep(500);
   bot.chat('/fill ~101 ~-1 ~-2 ~150 ~-1 ~2 minecraft:soul_sand');
   await sleep(1000);

   // Teleport back
   bot.chat('/tp @s ~ ~ ~');
}

bot.once('spawn', async () => {
  console.log('[bot] Bot spawned at', bot.entity.position.toString());

  // Prevent fall damage by modifying damage event locally, or requesting creative mode
  bot.chat('/gamemode creative');

  await sleep(2000);

  // Teleport to flat world surface level (-60 for 1.21 flat worlds with standard settings)
  bot.chat('/tp @s ~ -60 ~');
  await sleep(1000);

  await buildEnvironment();
  await sleep(2000);

  // Return to survival for true packet collection
  bot.chat('/gamemode survival');
  bot.chat('/effect give @s minecraft:resistance 9999 255 true');
  bot.chat('/effect give @s minecraft:saturation 9999 255 true');

  try {
    for (const sample of SAMPLES) {
      await runRhythmHack(bot, sample.id, sample.duration);
      await sleep(1000); // Rest
    }
    clearTimeout(timer);
    finish(0, 'SCENARIO_PASSED');
  } catch (err) {
    console.error('[scenario] Error:', err.stack || err.message);
    clearTimeout(timer);
    finish(1, 'SCENARIO_ERROR: ' + err.message);
  } finally {
    clearControls(bot);
  }
});
