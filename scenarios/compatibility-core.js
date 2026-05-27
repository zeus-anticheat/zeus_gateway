#!/usr/bin/env node
/**
 * Core scenario bot for ZeusGateway/ZeusFabric smoke tests.
 * Joins an offline-mode server and exercises movement, attack, inventory,
 * and velocity paths so ZeusGateway emits packets:
 *   0x09 (AttackEntity), 0x13 (Velocity), 0x22 (SurroundingBlocks),
 *   0x26 (InventoryTransaction), 0x27 (ExternalForce)
 *
 * Usage: node compatibility-core.js [--host HOST] [--port PORT] [--version VERSION] [--timeout SECONDS]
 */

const mineflayer = require('mineflayer');

const args = process.argv.slice(2);
function arg(name, fallback) {
  const idx = args.indexOf('--' + name);
  return idx >= 0 && args[idx + 1] ? args[idx + 1] : fallback;
}

const HOST = arg('host', '127.0.0.1');
const PORT = parseInt(arg('port', '25565'), 10);
const VERSION = arg('version', null);
const TIMEOUT = parseInt(arg('timeout', '60'), 10) * 1000;

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

const timer = setTimeout(() => {
  finish(1, 'TIMEOUT: scenario did not complete within ' + (TIMEOUT / 1000) + 's');
}, TIMEOUT);

bot.on('error', (err) => {
  console.error('Bot error:', err.message);
  finish(1, 'ERROR: ' + err.message);
});

bot.on('kicked', (reason) => {
  console.log('Kicked:', reason);
  finish(1, 'KICKED');
});

bot.on('spawn', async () => {
  console.log('[scenario] Bot spawned at', bot.entity.position.toString());
  const mcVersion = bot.version || '1.16';
  const major = mcVersion.split('.').map(Number);
  const isLegacy = major[0] === 1 && major[1] < 13; // 1.8-1.12
  console.log('[scenario] Server version:', mcVersion, 'isLegacy=' + isLegacy);

  // Version-specific command builders
  const summonZombieCmd = isLegacy
    ? '/summon Zombie ~ ~ ~'
    : '/summon minecraft:zombie ~ ~ ~';
  const summonTnt = (x, y, z) => isLegacy
    ? `/summon PrimedTnt ${x} ${y} ${z} {Fuse:20}`
    : `/summon tnt ${x} ${y} ${z} {fuse:20}`;
  const pistonBlock = isLegacy ? 'piston 1' : 'piston[facing=west]';

  try {
    // Wait for server-side delayed commands to summon entities/blocks near bot.
    // On Fabric servers where bot.chat('/command') doesn't work (signed chat),
    // the smoke runner sends delayed stdin commands ~12s after scenario starts.
    await sleep(15000);

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
    } else {
      console.log('[scenario] No entity found, swinging arm only');
      for (let i = 0; i < 5; i++) {
        bot.swingArm();
        await sleep(500);
      }
    }

    // Phase 2: Movement — walk around to trigger SurroundingBlocks (0x22)
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

    // Phase 3: Inventory click — triggers InventoryTransaction (0x26)
    // InventoryClickEvent fires when player clicks in an open inventory view.
    // We /give items, place a chest, teleport to it, then open and click inside it.
    console.log('[scenario] Phase 3: Inventory');
    try {
      bot.chat('/give @s minecraft:stone 64');
      await sleep(600);
      bot.chat('/give @s minecraft:dirt 32');
      await sleep(600);
      // Place a chest at a known absolute position near bot
      const pos = bot.entity.position;
      const cx = Math.floor(pos.x);
      const cy = Math.floor(pos.y);
      const cz = Math.floor(pos.z) + 2;
      bot.chat(`/setblock ${cx} ${cy} ${cz} chest`);
      await sleep(500);
      // Teleport bot right next to chest
      bot.chat(`/tp @s ${cx} ${cy} ${cz - 1}`);
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

    // Phase 4: Trigger ExternalForce (0x27) via piston (no TNT to keep bot alive)
    console.log('[scenario] Phase 4: ExternalForce triggers');
    try {
      const pos = bot.entity.position;
      const px = Math.floor(pos.x) + 3;
      const py = Math.floor(pos.y);
      const pz = Math.floor(pos.z);
      // Place piston facing player
      bot.chat(`/setblock ${px} ${py} ${pz} ${pistonBlock}`);
      await sleep(500);
      bot.chat(`/setblock ${px+1} ${py} ${pz} redstone_block`);
      await sleep(2000);
      // Cleanup
      bot.chat(`/setblock ${px} ${py} ${pz} air`);
      bot.chat(`/setblock ${px+1} ${py} ${pz} air`);
      // Brief explosion at distance to trigger force without killing bot
      const ex = Math.floor(pos.x) + 6;
      const ey = Math.floor(pos.y);
      const ez = Math.floor(pos.z);
      bot.chat(summonTnt(ex, ey, ez));
      await sleep(2000);
    } catch (e) {
      console.log('[scenario] ExternalForce command error (non-fatal):', e.message);
    }
    await sleep(2000);

    // Phase 5: Additional movement for more surrounding blocks
    console.log('[scenario] Phase 5: Extra movement');
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

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
