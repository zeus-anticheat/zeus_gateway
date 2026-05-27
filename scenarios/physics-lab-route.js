#!/usr/bin/env node
/**
 * Mineflayer route sampler for ZeusPhysicsLab.
 *
 * The Python smoke runner owns server-console commands. This bot requests each
 * station teleport through stdout, then performs real client controls/actions.
 */

const mineflayer = require('mineflayer');

const args = process.argv.slice(2);
function arg(name, fallback) {
  const idx = args.indexOf('--' + name);
  return idx >= 0 && args[idx + 1] ? args[idx + 1] : fallback;
}

const HOST = arg('host', '127.0.0.1');
const PORT = parseInt(arg('port', '25577'), 10);
const VERSION = arg('version', null);
const TIMEOUT = parseInt(arg('timeout', '180'), 10) * 1000;

const SAMPLES = [
  { number: 1, id: 'MV_FLAT_WALK', category: 'MOVEMENT' },
  { number: 9, id: 'MV_SINGLE_JUMP', category: 'VERTICAL' },
  { number: 22, id: 'MV_ICE', category: 'ENVIRONMENT' },
  { number: 43, id: 'MV_WATER_SURFACE', category: 'LIQUID_CLIMB_SPECIAL' },
  { number: 58, id: 'VH_BOAT_WATER_STRAIGHT', category: 'VEHICLE' },
  { number: 68, id: 'IN_BREAK_SLOW', category: 'INTERACT' },
  { number: 81, id: 'CB_STATIC_TARGET', category: 'COMBAT' },
  { number: 104, id: 'EF_KNOCKBACK_PLAYER', category: 'EXTERNAL_FORCE' },
  { number: 113, id: 'TX_HOTBAR_SWITCH', category: 'TRANSACTION' },
  { number: 123, id: 'NW_IDLE_BASELINE', category: 'NETWORK' },
  { number: 131, id: 'XR_MOVEMENT_INTERACT', category: 'CROSS_FEATURE' },
];

let done = false;
let completed = [];

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

async function move(bot, ms, opts = {}) {
  clearControls(bot);
  bot.setControlState('forward', opts.forward !== false);
  bot.setControlState('sprint', Boolean(opts.sprint));
  bot.setControlState('sneak', Boolean(opts.sneak));
  bot.setControlState('left', Boolean(opts.left));
  bot.setControlState('right', Boolean(opts.right));

  const started = Date.now();
  let ticks = 0;
  while (Date.now() - started < ms) {
    const yaw = ((ticks % 8) - 4) * 0.12;
    try {
      await bot.look(yaw, opts.pitch || 0, true);
    } catch (_) {}
    if (opts.jumpEvery && ticks % opts.jumpEvery === 0) {
      bot.setControlState('jump', true);
      await sleep(180);
      bot.setControlState('jump', false);
    }
    await sleep(250);
    ticks++;
  }
  clearControls(bot);
}

function nearestEntity(bot, matcher) {
  return bot.nearestEntity(entity => {
    if (!entity || entity === bot.entity) return false;
    if (bot.entity.position.distanceTo(entity.position) > 18) return false;
    return matcher(entity);
  });
}

async function attackNearest(bot) {
  const target = nearestEntity(bot, entity => {
    const name = (entity.name || entity.type || '').toLowerCase();
    return name.includes('armor_stand') ||
      name.includes('zombie') ||
      name.includes('skeleton') ||
      name.includes('horse');
  });
  if (!target) {
    for (let i = 0; i < 5; i++) {
      bot.swingArm();
      await sleep(350);
    }
    return false;
  }
  console.log('[scenario] attacking entity', target.name || target.type);
  for (let i = 0; i < 5; i++) {
    try {
      bot.attack(target);
    } catch (_) {
      bot.swingArm();
    }
    await sleep(650);
  }
  return true;
}

async function mountNearest(bot) {
  const vehicle = nearestEntity(bot, entity => {
    const name = (entity.name || entity.type || '').toLowerCase();
    return name.includes('boat') || name.includes('minecart') || name.includes('horse');
  });
  if (!vehicle) return false;
  console.log('[scenario] mounting vehicle', vehicle.name || vehicle.type);
  try {
    await bot.mount(vehicle);
    await move(bot, 3500, { forward: true, sprint: true, left: true });
    if (bot.vehicle) {
      try {
        bot.dismount();
      } catch (_) {}
    }
    await sleep(700);
    return true;
  } catch (err) {
    console.log('[scenario] mount failed:', err.message);
    return false;
  }
}

function findNearbyBlock(bot, names, radius = 5) {
  const base = bot.entity.position.floored();
  for (let dy = -2; dy <= 2; dy++) {
    for (let dz = -radius; dz <= radius + 10; dz++) {
      for (let dx = -radius; dx <= radius; dx++) {
        const block = bot.blockAt(base.offset(dx, dy, dz));
        if (!block || !names.includes(block.name)) continue;
        const center = block.position.offset(0.5, 0.5, 0.5);
        if (bot.entity.position.distanceTo(center) <= radius + 0.5) return block;
      }
    }
  }
  return null;
}

async function activateNearby(bot, names) {
  const block = findNearbyBlock(bot, names, 6);
  if (!block) return false;
  console.log('[scenario] activating block', block.name, block.position.toString());
  try {
    await bot.activateBlock(block);
    await sleep(1800);
    return true;
  } catch (err) {
    console.log('[scenario] activate failed:', err.message);
    return false;
  }
}

async function useContainer(bot) {
  const block = findNearbyBlock(bot, ['chest', 'barrel', 'shulker_box'], 5);
  if (!block) return false;
  console.log('[scenario] opening container', block.name, block.position.toString());
  try {
    await bot.lookAt(block.position.offset(0.5, 0.5, 0.5), true);
    await sleep(300);
    const container = await bot.openContainer(block);
    await sleep(700);
    const window = bot.currentWindow || container;
    let clicks = 0;
    const topEnd = Math.min(window.inventoryStart || 27, window.slots.length);
    const slots = [];
    for (let slot = 0; slot < topEnd; slot++) {
      if (window.slots[slot]) slots.push(slot);
    }
    if (slots.length === 0) {
      for (let slot = 0; slot < Math.min(5, topEnd); slot++) slots.push(slot);
    }
    for (const slot of slots.slice(0, 4)) {
      try {
        await bot.clickWindow(slot, 0, 0);
        await sleep(250);
        await bot.clickWindow(slot, 0, 0);
        await sleep(250);
        clicks++;
      } catch (err) {
        console.log('[scenario] container click failed:', slot, err.message);
      }
    }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    console.log('[scenario] container clicks', clicks);
    await sleep(500);
    return clicks > 0;
  } catch (err) {
    console.log('[scenario] container failed:', err.message);
    return false;
  }
}

async function performSample(bot, sample) {
  console.log('[scenario] sample start', sample.number, sample.id, sample.category);
  switch (sample.category) {
    case 'MOVEMENT':
      await move(bot, 4200, { forward: true, sprint: false });
      break;
    case 'VERTICAL':
      await move(bot, 5200, { forward: true, sprint: true, jumpEvery: 2 });
      break;
    case 'ENVIRONMENT':
      await move(bot, 5200, { forward: true, sprint: true, left: true });
      await move(bot, 1800, { forward: true, right: true });
      break;
    case 'LIQUID_CLIMB_SPECIAL':
      await move(bot, 6500, { forward: true, sprint: true, jumpEvery: 3, pitch: -0.15 });
      break;
    case 'VEHICLE':
      await move(bot, 1800, { forward: true });
      await mountNearest(bot);
      await move(bot, 1800, { forward: true });
      break;
    case 'INTERACT':
      await move(bot, 2400, { forward: true });
      await activateNearby(bot, ['stone_button', 'lever', 'oak_trapdoor']);
      for (let i = 0; i < 6; i++) {
        bot.swingArm();
        await sleep(300);
      }
      await move(bot, 2200, { forward: true, sprint: true });
      break;
    case 'COMBAT':
      await move(bot, 4200, { forward: true, sprint: true });
      await attackNearest(bot);
      break;
    case 'EXTERNAL_FORCE':
      await move(bot, 2200, { forward: true });
      await activateNearby(bot, ['stone_button']);
      await move(bot, 4200, { forward: true, jumpEvery: 4 });
      break;
    case 'TRANSACTION':
      if (!await useContainer(bot)) {
        await move(bot, 1200, { forward: true });
        await useContainer(bot);
      }
      await move(bot, 1800, { forward: true });
      break;
    case 'NETWORK':
      await move(bot, 3000, { forward: true });
      await move(bot, 2000, { forward: false, sneak: true });
      await move(bot, 3000, { forward: true, sprint: true });
      break;
    case 'CROSS_FEATURE':
      await move(bot, 3200, { forward: true, sprint: true, jumpEvery: 3 });
      await attackNearest(bot);
      await activateNearby(bot, ['stone_button', 'lever', 'oak_trapdoor']);
      break;
    default:
      await move(bot, 4000, { forward: true });
  }
  completed.push(sample.id);
  console.log('[scenario] sample complete', sample.number, sample.id);
}

const botOpts = {
  host: HOST,
  port: PORT,
  username: 'ZeusLabBot',
  auth: 'offline',
  hideErrors: false,
};
if (VERSION) botOpts.version = VERSION;

const bot = mineflayer.createBot(botOpts);

const timer = setTimeout(() => {
  finish(1, 'TIMEOUT: physics lab route did not complete within ' + (TIMEOUT / 1000) + 's');
}, TIMEOUT);

bot.on('error', err => {
  console.error('Bot error:', err.message);
  finish(1, 'ERROR: ' + err.message);
});

bot.on('kicked', reason => {
  console.log('Kicked:', reason);
  finish(1, 'KICKED');
});

bot.on('spawn', async () => {
  console.log('[scenario] Bot spawned at', bot.entity.position.toString());
  console.log('[scenario] Server version:', bot.version || 'unknown');
  console.log('ZEUSLAB_READY');
  try {
    await sleep(3000);
    for (const sample of SAMPLES) {
      console.log('ZEUSLAB_REQUEST_TP ' + JSON.stringify(sample));
      await sleep(1800);
      await performSample(bot, sample);
      await sleep(600);
    }
    console.log('ZEUSLAB_SUMMARY ' + JSON.stringify({ completed, count: completed.length }));
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
