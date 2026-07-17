#!/usr/bin/env node
/**
 * Core scenario bot for ZeusGateway/ZeusFabric smoke tests.
 * Joins an offline-mode server and exercises movement, attack, inventory,
 * and velocity paths so ZeusGateway emits packets:
 *   0x09 (AttackEntity), 0x22 (Velocity), 0x26 (InventoryTransaction),
 *   0x27 (ExternalForce)
 *
 * Usage: node compatibility-core.js [--host HOST] [--port PORT] [--version VERSION] [--timeout SECONDS]
 */

const assert = require('assert');
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');

const args = process.argv.slice(2);
function arg(name, fallback) {
  const idx = args.indexOf('--' + name);
  return idx >= 0 && args[idx + 1] ? args[idx + 1] : fallback;
}

const HOST = arg('host', '127.0.0.1');
const PORT = parseInt(arg('port', '25565'), 10);
const VERSION = arg('version', null);
const TIMEOUT = parseInt(arg('timeout', '90'), 10) * 1000;

if (args.includes('--self-check')) {
  runSelfCheck();
  process.exit(0);
}

let done = false;
function finish(code, msg) {
  if (done) return;
  done = true;
  if (msg) console.log(msg);
  setTimeout(() => process.exit(code), 500);
}

const botOpts = {
  host: HOST,
  port: PORT,
  username: 'ZeusSmokeBot',
  auth: 'offline',
  hideErrors: false,
};
if (VERSION) botOpts.version = VERSION;

const bot = mineflayer.createBot(botOpts);
let selfForceEvents = 0;
let healthDrops = 0;
let lastHealth;
let deaths = 0;
bot._client.on('entity_velocity', (packet) => {
  if (bot.entity && packet.entityId === bot.entity.id) selfForceEvents++;
});
bot._client.on('explosion', (packet) => {
  if (hasExplosionKnockback(packet)) selfForceEvents++;
});
bot.on('health', () => {
  if (Number.isFinite(lastHealth) && bot.health < lastHealth) healthDrops++;
  lastHealth = bot.health;
});
bot.on('death', () => {
  deaths++;
});

const timer = setTimeout(() => {
  finish(1, 'TIMEOUT: scenario did not complete within ' + (TIMEOUT / 1000) + 's');
}, TIMEOUT);
let scenarioStarted = false;

bot.on('error', (err) => {
  console.error('Bot error:', err.message);
  finish(1, 'ERROR: ' + err.message);
});

bot.on('kicked', (reason) => {
  console.log('Kicked:', reason);
  finish(1, 'KICKED');
});

bot.on('spawn', async () => {
  if (scenarioStarted) {
    console.log('[scenario] Respawn observed; active scenario continues');
    return;
  }
  scenarioStarted = true;
  console.log('[scenario] Bot spawned at', bot.entity.position.toString());
  const mcVersion = bot.version || '1.16';
  const isLegacy = isLegacyVersion(mcVersion);
  const commands = commandSet(isLegacy, bot.username);
  console.log('[scenario] Server version:', mcVersion, 'isLegacy=' + isLegacy);

  // Version-specific command builders
  const summonZombieCmd = commands.summonZombie;
  const summonTnt = commands.summonTnt;

  try {
    // Wait for server-side delayed commands to summon entities/blocks near bot.
    // On Fabric servers where bot.chat('/command') doesn't work (signed chat),
    // the smoke runner sends delayed stdin commands ~12s after scenario starts.
    await sleep(30000);

    // Phase 1: Attack FIRST while summoned entities are still alive
    console.log('[scenario] Phase 1: Attack');
    try {
      bot.chat(summonZombieCmd);
      await sleep(800);
      // Try fallback summon syntax in case the first failed
      bot.chat('/summon Zombie ~ ~ ~');
      await sleep(400);
      bot.chat('/summon zombie ~ ~ ~');
    } catch (e) {}
    await sleep(1500);
    let entity = bot.nearestEntity((e) =>
      e.type === 'mob' || e.type === 'hostile' ||
      e.type === 'animal' || e.type === 'passive' ||
      (e.name && (e.name === 'zombie' || e.name === 'sheep' || e.name === 'cow' || e.name === 'pig'))
    );
    if (entity) {
      const dist = bot.entity.position.distanceTo(entity.position);
      console.log('[scenario] Found entity:', entity.name || entity.type, 'dist=' + dist.toFixed(2));
      // Multiple attacks, with cooldown between to ensure each registers
      for (let i = 0; i < 5; i++) {
        if (entity && entity.isValid) {
          bot.attack(entity);
        }
        await sleep(700);
      }
      console.log('[scenario] Completed attack burst');
      bot.chat(commands.killEntities);
      if (!await waitFor(() => !entity.isValid, 3000)) {
        throw new Error('Attack target remained active between phases');
      }
    } else {
      console.log('[scenario] No entity found, swinging arm only');
      for (let i = 0; i < 5; i++) {
        bot.swingArm();
        await sleep(500);
      }
    }

    // Phase 2: Movement — walk around while velocity capture remains active (0x22)
    console.log('[scenario] Phase 2: Movement');
    bot.setControlState('forward', true);
    await sleep(2000);
    bot.setControlState('forward', false);
    bot.setControlState('jump', true);
    await sleep(500);
    bot.setControlState('jump', false);
    await sleep(1000);
    bot.setControlState('back', true);
    await sleep(1500);
    bot.setControlState('back', false);
    await sleep(500);
    if (bot.supportFeature('newPlayerInputPacket')) {
      bot.setControlState('sneak', true);
      await sleep(500);
      bot.setControlState('sneak', false);
      await sleep(500);
      console.log('[scenario] PLAYER_INPUT sneak toggle completed');
    }
    bot.clearControlStates();

    // Phase 3: Inventory click — triggers InventoryTransaction (0x26)
    // InventoryClickEvent fires when player clicks in an open inventory view.
    // We /give items, place a chest, teleport to it, then open and click inside it.
    console.log('[scenario] Phase 3: Inventory');
    try {
      bot.chat(commands.giveStone);
      await sleep(600);
      bot.chat(commands.giveDirt);
      await sleep(600);
      // Place a chest at a known absolute position near bot
      const pos = bot.entity.position;
      const cx = Math.floor(pos.x);
      const cy = Math.floor(pos.y);
      const cz = Math.floor(pos.z) + 2;
      bot.chat(`/setblock ${cx} ${cy} ${cz} chest`);
      await sleep(500);
      // Teleport bot right next to chest
      bot.chat(`/tp ${bot.username} ${cx} ${cy} ${cz - 1}`);
      await sleep(1000);
      // Try to open the chest
      const chestBlock = bot.blockAt(bot.entity.position.offset(0, 0, 1));
      if (chestBlock && chestBlock.name === 'chest') {
        console.log('[scenario] Opening chest at', chestBlock.position.toString());
        const chest = await bot.openContainer(chestBlock);
        await sleep(800);
        // Deposit items into chest via shift-click
        const playerSlots = chest.slots.slice(chest.inventoryStart, chest.inventoryEnd);
        let clicked = 0;
        for (const item of playerSlots) {
          if (item && clicked < 4) {
            try {
              await chest.deposit(item.type, null, item.count);
              clicked++;
              await sleep(400);
            } catch (e) {
              try {
                await chest.click(item.slot, 0, 1); // shift-click
                clicked++;
                await sleep(400);
              } catch (e2) {}
            }
          }
        }
        if (clicked === 0) {
          // Fallback: click any slot
          for (let s = 0; s < 5; s++) {
            try {
              await chest.click(s, 0, 0);
              await sleep(300);
              await chest.click(s, 0, 0);
              await sleep(300);
              clicked++;
            } catch (e) {}
          }
        }
        console.log('[scenario] Inventory clicks performed:', clicked);
        chest.close();
        await sleep(500);
      } else {
        console.log('[scenario] Could not find chest block, trying player inventory clicks');
        for (let slotId of [36, 37, 38]) {
          try {
            await bot.clickWindow(slotId, 0, 0);
            await sleep(400);
            await bot.clickWindow(slotId, 0, 0);
            await sleep(400);
          } catch (e) {}
        }
      }
      // Cleanup chest
      bot.chat(`/setblock ${cx} ${cy} ${cz} air`);
    } catch (invErr) {
      console.log('[scenario] Inventory error (non-fatal):', invErr.message);
    }
    await sleep(1500);

    console.log('[scenario] Phase 4: Piston external force');
    bot.clearControlStates();
    const geometry = pistonGeometry(bot.entity.position);
    await preparePiston(geometry, commands);
    console.log('[scenario] Piston moved block into', formatBlock(geometry.destination));
    await sleep(1500);
    await cleanupPiston(geometry);

    console.log('[scenario] Phase 5: Survival damage and velocity');
    bot.clearControlStates();
    await ensureSurvival(commands);
    const forceCount = selfForceEvents;
    const healthDropCount = healthDrops;
    const deathCount = deaths;
    const damageSite = await prepareDamageSite(bot.entity.position);
    bot.chat(summonTnt(damageSite.tnt.x + 0.5, damageSite.tnt.y, damageSite.tnt.z + 0.5));
    const damageSeen = await waitFor(() => selfForceEvents > forceCount && healthDrops > healthDropCount, 7000);
    setBlock(damageSite.support, 'air');
    if (!damageSeen) throw new Error('Damage/velocity was not observed');
    if (!bot.isAlive || bot.health <= 0 || deaths !== deathCount) throw new Error('Bot did not survive damage/velocity phase');
    console.log('[scenario] Damage/velocity verified; health=' + bot.health);
    await sleep(1000);

    console.log('[scenario] Phase 6: Extra movement');
    bot.setControlState('left', true);
    await sleep(1500);
    bot.setControlState('left', false);
    bot.setControlState('right', true);
    await sleep(1500);
    bot.setControlState('right', false);
    await sleep(2000);

    console.log('[scenario] All phases complete');
    clearTimeout(timer);
    finish(0, 'SCENARIO_PASSED');
  } catch (err) {
    console.error('[scenario] Error:', err.message);
    clearTimeout(timer);
    finish(1, 'SCENARIO_ERROR: ' + err.message);
  }
});

function isLegacyVersion(version) {
  const parts = version.split('.').map(Number);
  return parts[0] === 1 && parts[1] < 13;
}

function commandSet(isLegacy, username) {
  return {
    piston: isLegacy ? 'piston 5' : 'minecraft:piston[facing=east]',
    survival: isLegacy ? `/gamemode 0 ${username}` : `/gamemode survival ${username}`,
    giveDirt: isLegacy ? `/give ${username} dirt 32` : `/give ${username} minecraft:dirt 32`,
    giveStone: isLegacy ? `/give ${username} stone 64` : `/give ${username} minecraft:stone 64`,
    killEntities: isLegacy
      ? '/kill @e[type=!Player,r=16]'
      : '/kill @e[type=!minecraft:player,distance=..16]',
    summonZombie: isLegacy ? '/summon Zombie ~ ~ ~' : '/summon minecraft:zombie ~ ~ ~',
    summonTnt: isLegacy
      ? (x, y, z) => `/summon PrimedTnt ${x} ${y} ${z}`
      : (x, y, z) => `/summon minecraft:tnt ${x} ${y} ${z}`
  };
}

function pistonGeometry(position) {
  const destination = new Vec3(Math.floor(position.x), Math.floor(position.y), Math.floor(position.z));
  return {
    destination,
    moving: destination.offset(-1, 0, 0),
    piston: destination.offset(-2, 0, 0),
    power: destination.offset(-3, 0, 0)
  };
}

function formatBlock(point) {
  return `${point.x} ${point.y} ${point.z}`;
}

function setBlock(point, block) {
  bot.chat(`/setblock ${formatBlock(point)} ${block}`);
}

function blockIs(point, name) {
  const block = bot.blockAt(point);
  return block && block.name === name;
}

async function preparePiston(geometry, commands) {
  for (const point of Object.values(geometry)) setBlock(point, 'air');
  await sleep(500);
  setBlock(geometry.piston, commands.piston);
  setBlock(geometry.moving, 'stone');
  if (!await waitFor(() => blockIs(geometry.moving, 'stone'), 3000)) {
    throw new Error('Could not build movable piston geometry');
  }
  setBlock(geometry.power, 'redstone_block');
  if (!await waitFor(() => blockIs(geometry.destination, 'stone'), 3000)) {
    throw new Error('Piston did not move its block into the bot');
  }
}

async function cleanupPiston(geometry) {
  setBlock(geometry.power, 'air');
  await sleep(500);
  for (const point of Object.values(geometry)) setBlock(point, 'air');
  await sleep(500);
}

async function prepareDamageSite(position) {
  const tnt = new Vec3(Math.floor(position.x) + 7, Math.floor(position.y), Math.floor(position.z));
  const support = tnt.offset(0, -1, 0);
  setBlock(support, 'stone');
  setBlock(tnt, 'air');
  if (!await waitFor(() => blockIs(support, 'stone'), 3000)) {
    throw new Error('Could not prepare damage/velocity site');
  }
  return { support, tnt };
}

async function ensureSurvival(commands) {
  if (bot.game.gameMode !== 'survival') {
    bot.chat(commands.survival);
  }
  if (!await waitFor(() => bot.game.gameMode === 'survival', 3000)) {
    throw new Error('Survival mode could not be verified');
  }
  if (!await waitFor(() => bot.isAlive && Number.isFinite(bot.health) && bot.health >= 10, 10000)) {
    throw new Error('Bot lacks enough health for damage/velocity phase');
  }
  console.log('[scenario] Survival verified; health=' + bot.health);
}

function hasExplosionKnockback(packet) {
  const knockback = packet.playerKnockback;
  return Boolean(
    (knockback && (knockback.x || knockback.y || knockback.z)) ||
    packet.playerMotionX || packet.playerMotionY || packet.playerMotionZ
  );
}

async function waitFor(predicate, timeout) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (predicate()) return true;
    await sleep(50);
  }
  return predicate();
}

function runSelfCheck() {
  assert.strictEqual(isLegacyVersion('1.8.8'), true);
  assert.strictEqual(isLegacyVersion('1.12.2'), true);
  assert.strictEqual(isLegacyVersion('1.13.2'), false);
  assert.strictEqual(isLegacyVersion('26.2'), false);
  assert.strictEqual(commandSet(true, 'Bot').piston, 'piston 5');
  assert.strictEqual(commandSet(true, 'Bot').survival, '/gamemode 0 Bot');
  assert.strictEqual(commandSet(true, 'Bot').giveStone, '/give Bot stone 64');
  assert.strictEqual(commandSet(true, 'Bot').summonTnt(1, 2, 3), '/summon PrimedTnt 1 2 3');
  assert.strictEqual(commandSet(false, 'Bot').piston, 'minecraft:piston[facing=east]');
  assert.strictEqual(commandSet(false, 'Bot').survival, '/gamemode survival Bot');
  assert.strictEqual(commandSet(false, 'Bot').giveStone, '/give Bot minecraft:stone 64');
  assert.strictEqual(commandSet(false, 'Bot').summonTnt(1, 2, 3), '/summon minecraft:tnt 1 2 3');
  assert.strictEqual(mineflayer.supportFeature('newPlayerInputPacket', '1.8.8'), false);
  assert.strictEqual(mineflayer.supportFeature('newPlayerInputPacket', '1.21.11'), true);
  const geometry = pistonGeometry(new Vec3(10.75, 64, -2.25));
  assert.deepStrictEqual(geometry.destination, new Vec3(10, 64, -3));
  assert.deepStrictEqual(geometry.moving, new Vec3(9, 64, -3));
  assert.deepStrictEqual(geometry.piston, new Vec3(8, 64, -3));
  assert.deepStrictEqual(geometry.power, new Vec3(7, 64, -3));
  assert.strictEqual(mineflayer.oldestSupportedVersion, '1.8.8');
  console.log('SELF_CHECK_PASSED');
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
