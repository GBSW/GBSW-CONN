import { randomBytes } from "node:crypto";
import { spawn, spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const frontendDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const rootDir = resolve(frontendDir, "..");
const backendDir = join(rootDir, "backend");
const composeFile = join(rootDir, "docker-compose.e2e.yml");
const nextEnvironmentFile = join(frontendDir, "next-env.d.ts");
const typescriptConfigFile = join(frontendDir, "tsconfig.json");
const [nextEnvironmentSnapshot, typescriptConfigSnapshot] = await Promise.all([
  readFile(nextEnvironmentFile),
  readFile(typescriptConfigFile),
]);
const temporaryDir = await mkdtemp(join(tmpdir(), "gbsw-e2e-"));
const bootstrapFile = join(temporaryDir, "bootstrap.txt");
const dataFile = join(temporaryDir, "data.json");
const stateDir = join(temporaryDir, "states");
const project = `gbsw-e2e-${process.pid}-${Date.now()}`;
const mysqlPort = await availablePort();
const backendPort = await availablePort();
const frontendPort = await availablePort();
const databasePassword = randomBytes(24).toString("hex");
const rootPassword = randomBytes(24).toString("hex");
const sharedEnvironment = {
  ...process.env,
  E2E_MYSQL_PORT: String(mysqlPort),
  E2E_MYSQL_PASSWORD: databasePassword,
  E2E_MYSQL_ROOT_PASSWORD: rootPassword,
};
const backendEnvironment = applyJavaHome({
  ...sharedEnvironment,
  DB_URL: `jdbc:mysql://127.0.0.1:${mysqlPort}/school_communication_e2e?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC`,
  DB_USERNAME: "school_e2e",
  DB_PASSWORD: databasePassword,
  BACKEND_PORT: String(backendPort),
  SESSION_COOKIE_SECURE: "false",
  OPENAPI_ENABLED: "false",
  SWAGGER_UI_ENABLED: "false",
  LOGIN_FAILURES_BEFORE_DELAY: "100",
  GENERAL_REQUESTS_PER_MINUTE: "1000",
  REAUTHENTICATION_TTL: "2h",
  THROTTLE_FINGERPRINT_SECRET: randomBytes(32).toString("hex"),
  PROPOSAL_IDENTITY_KEY_BASE64: randomBytes(32).toString("base64"),
  PROPOSAL_IDENTITY_KEY_VERSION: "1",
  PROPOSAL_SUPPORT_THRESHOLD: "50",
});
const children = [];
let composeStarted = false;

try {
  run("docker", ["compose", "-p", project, "-f", composeFile, "up", "-d", "--wait", "--wait-timeout", "120"], rootDir, sharedEnvironment);
  composeStarted = true;

  run("./gradlew", ["bootstrapSuperAdmin", "--no-daemon"], backendDir, {
    ...backendEnvironment,
    BOOTSTRAP_LOGIN_ID: "e2e.admin",
    BOOTSTRAP_DISPLAY_NAME: "E2E 관리자",
    BOOTSTRAP_OUTPUT_FILE: bootstrapFile,
  });

  const backend = start("./gradlew", ["bootRun", "--no-daemon"], backendDir, backendEnvironment, join(temporaryDir, "backend.log"));
  children.push(backend);
  await waitFor(`http://127.0.0.1:${backendPort}/actuator/health`, 120_000);

  const frontend = start("npm", ["run", "dev", "--", "--hostname", "127.0.0.1", "--port", String(frontendPort)], frontendDir, {
    ...process.env,
    BACKEND_INTERNAL_URL: `http://127.0.0.1:${backendPort}`,
  }, join(temporaryDir, "frontend.log"));
  children.push(frontend);
  await waitFor(`http://127.0.0.1:${frontendPort}`, 90_000);

  await writeFile(dataFile, "{}\n", { mode: 0o600 });
  const playwright = spawnSync("npx", ["playwright", "test"], {
    cwd: frontendDir,
    env: {
      ...process.env,
      E2E_BASE_URL: `http://127.0.0.1:${frontendPort}`,
      E2E_BOOTSTRAP_FILE: bootstrapFile,
      E2E_DATA_FILE: dataFile,
      E2E_STATE_DIR: stateDir,
    },
    stdio: "inherit",
  });
  if (playwright.status !== 0) process.exitCode = playwright.status ?? 1;
} finally {
  for (const child of children.reverse()) await stop(child);
  if (composeStarted) spawnSync("docker", ["compose", "-p", project, "-f", composeFile, "down", "--volumes", "--remove-orphans"], { cwd: rootDir, env: sharedEnvironment, stdio: "inherit" });
  await Promise.all([
    writeFile(nextEnvironmentFile, nextEnvironmentSnapshot),
    writeFile(typescriptConfigFile, typescriptConfigSnapshot),
  ]);
  await removeGeneratedDevOutput();
  process.stderr.write(`E2E diagnostic directory: ${temporaryDir}\n`);
}

/**
 * 백엔드 환경의 JAVA_HOME을 실제로 존재하는 경로로만 맞춘다.
 *
 * 존재하지 않는 경로가 들어 있으면 gradlew가 툴체인을 보기도 전에 중단된다.
 * 쓸 수 있는 후보가 없으면 상속된 값까지 지운다. JAVA_HOME이 없으면 gradlew는
 * PATH의 java로 실행되고 컴파일용 JDK는 Gradle 툴체인이 고른다.
 */
function applyJavaHome(environment) {
  const candidates = [
    process.env.JAVA_HOME,
    "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home",
  ];
  const javaHome = candidates.find((candidate) => candidate && existsSync(candidate));
  if (javaHome) environment.JAVA_HOME = javaHome;
  else delete environment.JAVA_HOME;
  return environment;
}

function run(command, args, cwd, env) {
  const result = spawnSync(command, args, { cwd, env, stdio: "inherit" });
  if (result.status !== 0) throw new Error(`${command} failed with exit code ${result.status}`);
}

function start(command, args, cwd, env, logFile) {
  const child = spawn(command, args, { cwd, env, detached: process.platform !== "win32", stdio: ["ignore", "pipe", "pipe"] });
  const chunks = [];
  child.stdout.on("data", (chunk) => chunks.push(chunk));
  child.stderr.on("data", (chunk) => chunks.push(chunk));
  child.on("exit", () => void writeFile(logFile, Buffer.concat(chunks)).catch(() => undefined));
  return child;
}

async function stop(child) {
  if (!child.pid || child.exitCode !== null) return;
  const exited = new Promise((resolveExit) => child.once("exit", () => resolveExit(true)));
  try {
    if (process.platform === "win32") child.kill("SIGTERM");
    else process.kill(-child.pid, "SIGTERM");
  } catch {
    return;
  }
  if (await Promise.race([exited, delay(5_000, false)])) return;
  try {
    if (process.platform === "win32") child.kill("SIGKILL");
    else process.kill(-child.pid, "SIGKILL");
  } catch {
    return;
  }
  await Promise.race([exited, delay(2_000, false)]);
}

async function removeGeneratedDevOutput() {
  const directory = join(frontendDir, ".next", "dev");
  for (let attempt = 0; attempt < 10; attempt += 1) {
    try {
      await rm(directory, { recursive: true, force: true });
      return;
    } catch (error) {
      if (error?.code !== "ENOTEMPTY" || attempt === 9) throw error;
      await delay(100);
    }
  }
}

function delay(milliseconds, value) {
  return new Promise((resolveDelay) => setTimeout(() => resolveDelay(value), milliseconds));
}

async function availablePort() {
  return await new Promise((resolvePort, reject) => {
    const server = createServer();
    server.unref();
    server.on("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      const port = typeof address === "object" && address ? address.port : 0;
      server.close(() => resolvePort(port));
    });
  });
}

async function waitFor(url, timeout) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
      if (response.ok) return;
    } catch {
      // Service is still starting.
    }
    await new Promise((resolveWait) => setTimeout(resolveWait, 500));
  }
  throw new Error(`Timed out waiting for ${url}`);
}
