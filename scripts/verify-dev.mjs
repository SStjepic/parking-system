import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { writeFile, unlink } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { setTimeout as delay } from "node:timers/promises";

// Run with Node 24 after starting the `all` Compose profile.
const systems = [
  { name: "bank", directory: "bank", digit: 1 },
  { name: "parking", directory: "parking_system", digit: 2 },
  { name: "payment-processor", directory: "payment_processor", digit: 3 },
];

async function request(url) {
  return fetch(url, { signal: AbortSignal.timeout(10_000) });
}

async function verifyHmr(system, origin) {
  const filename = `__compose_hmr_probe_${randomUUID()}.js`;
  const modulePath = `/src/${filename}`;
  const file = fileURLToPath(new URL(`../${system.directory}/frontend/src/${filename}`, import.meta.url));
  const source = (value) => `export const value = ${value};\nif (import.meta.hot) import.meta.hot.accept();\n`;
  let socket;
  let timer;

  try {
    await writeFile(file, source(1), { flag: "wx" });
    const module = await request(`${origin}${modulePath}`);
    assert.equal(module.status, 200);
    assert.match(await module.text(), /value = 1/);
    const client = await (await request(`${origin}/@vite/client`)).text();
    const token = client.match(/const wsToken = "([^"]+)"/)?.[1];
    assert.ok(token, "Vite HMR token is present");

    socket = new WebSocket(`${origin.replace("http:", "ws:")}/?token=${token}`, "vite-hmr");
    await new Promise((resolve, reject) => {
      timer = setTimeout(() => reject(new Error(`${system.name}: HMR connection timed out`)), 15_000);
      socket.addEventListener("message", (event) => {
        if (JSON.parse(event.data).type === "connected") resolve();
      });
      socket.addEventListener("error", () => reject(new Error(`${system.name}: HMR connection failed`)));
    });
    clearTimeout(timer);
    // Let polling observe the new module before modifying it.
    await delay(500);

    await new Promise((resolve, reject) => {
      timer = setTimeout(() => reject(new Error(`${system.name}: HMR update timed out`)), 15_000);
      socket.addEventListener("message", (event) => {
        const message = JSON.parse(event.data);
        if (message.type === "update" && message.updates.some((update) => update.path === modulePath)) {
          resolve();
        }
      });
      writeFile(file, source(2)).catch(reject);
    });
    assert.match(await (await request(`${origin}${modulePath}`)).text(), /value = 2/);
  } finally {
    clearTimeout(timer);
    socket?.close();
    await unlink(file).catch((error) => {
      if (error.code !== "ENOENT") throw error;
    });
  }
}

for (const system of systems) {
  const frontend = `http://127.0.0.1:517${system.digit}`;
  const backend = `http://127.0.0.1:808${system.digit}`;
  const health = await request(`${backend}/actuator/health`);
  assert.equal(health.status, 200);
  assert.equal((await health.json()).status, "UP");
  assert.equal((await request(frontend)).status, 200);
  const proxy = await request(`${frontend}/api/__compose_probe__`);
  assert.equal(proxy.status, 404);
  assert.equal((await proxy.json()).code, 404, "Vite forwards API calls to Spring");
  await verifyHmr(system, frontend);
  console.log(`PASS ${system.name}: localhost frontend, backend/database health, API proxy, HMR WebSocket update`);
}
