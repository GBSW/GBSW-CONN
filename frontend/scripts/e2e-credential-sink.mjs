import { randomUUID } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { join } from "node:path";

const port = Number(required("E2E_CREDENTIAL_DELIVERY_PORT"));
const token = required("E2E_CREDENTIAL_DELIVERY_TOKEN");
const deliveryDirectory = required("E2E_CREDENTIAL_DELIVERY_DIR");
await mkdir(deliveryDirectory, { recursive: true, mode: 0o700 });

const server = createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    response.writeHead(200, { "Content-Type": "text/plain" });
    response.end("ok");
    return;
  }
  if (request.method !== "POST" || request.url !== "/deliver") {
    response.writeHead(404).end();
    return;
  }
  if (request.headers.authorization !== `Bearer ${token}`) {
    response.writeHead(401).end();
    return;
  }

  try {
    const payload = JSON.parse(await readBody(request));
    if (!payload.recipientReference || !payload.oneTimeCode || !payload.userPublicId) {
      response.writeHead(400).end();
      return;
    }
    const file = join(deliveryDirectory, `${encodeURIComponent(payload.recipientReference)}.json`);
    await writeFile(file, `${JSON.stringify(payload)}\n`, { mode: 0o600 });
    response.writeHead(204, { "X-Delivery-Id": randomUUID() });
    response.end();
  } catch {
    response.writeHead(400).end();
  }
});

server.listen(port, "127.0.0.1");
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)));
}

async function readBody(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 65_536) throw new Error("Payload too large");
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString("utf8");
}

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}
