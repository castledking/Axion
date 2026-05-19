import { chromium } from 'playwright';
import { readFileSync, existsSync, mkdirSync } from 'fs';
import { markdownToBbcode } from './converter.js';
import { releaseToModrinth } from './modrinth.js';

const BASE_URL = 'https://www.spigotmc.org';
const SCREENSHOTS_DIR = '/tmp/spigot-screenshots';
const RELEASE_NOTES_DIR = '/tmp/release-notes';

function log(...args) {
  console.log(`[${new Date().toISOString()}]`, ...args);
}

function die(...args) {
  console.error(`[${new Date().toISOString()}] FATAL:`, ...args);
  process.exit(1);
}

function env(key, fallback = undefined) {
  const val = process.env[key];
  if (val === undefined || val === '') {
    if (fallback !== undefined) return fallback;
    die(`Missing required env var: ${key}`);
  }
  return val;
}

function parseResourceIds(raw) {
  const map = {};
  for (const pair of raw.split(',')) {
    const [repo, id] = pair.trim().split(':');
    if (repo && id) map[repo.trim()] = id.trim();
  }
  return map;
}

function loadRepoConfig(repoName) {
  const configPath = new URL('spigot-resource-ids.json', import.meta.url);
  const config = JSON.parse(readFileSync(configPath, 'utf-8'));
  return config[repoName] || null;
}

async function screenshot(page, name) {
  if (!existsSync(SCREENSHOTS_DIR)) mkdirSync(SCREENSHOTS_DIR, { recursive: true });
  await page.screenshot({ path: `${SCREENSHOTS_DIR}/${name}.png`, fullPage: true });
  log(`Screenshot saved: ${SCREENSHOTS_DIR}/${name}.png`);
}

function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
}

async function loadCookies(page, cookiesB64) {
  try {
    const cookies = JSON.parse(Buffer.from(cookiesB64, 'base64').toString());
    await page.context().addCookies(cookies);
    log(`Loaded ${cookies.length} cookies`);
    return true;
  } catch (e) {
    log('Failed to load cookies:', e.message);
    return false;
  }
}

async function login(page, username, password) {
  log('Logging in...');
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });

  const radio = page.locator('input[name="register"][value="0"]');
  if (await radio.isVisible()) {
    await radio.check();
    await sleep(500);
  }

  await page.fill('input[name="login"]', username);
  await page.fill('input[name="password"]', password);
  await page.click('input[type="submit"][value="Log in"]');

  try {
    await page.waitForURL(url => !url.toString().includes('/login'), { timeout: 15000 });
    log('Login successful');
    return true;
  } catch {
    await screenshot(page, 'login-failed');
    die('Login failed - check credentials or CAPTCHA challenge');
  }
}

function parseReleaseNotes(filePath) {
  if (!existsSync(filePath)) return { title: '', body: '' };

  const content = readFileSync(filePath, 'utf-8').replace(/\r\n/g, '\n');
  const lines = content.split('\n');

  const title = lines[0] || '';
  const body = lines.slice(1).join('\n').trim();

  log(`Parsed release notes: title="${title.substring(0, 60)}..." (${body.length} chars body)`);
  return { title, body };
}

async function submitToSpigotmc(page, resourceId, version, title, bbcodeBody, downloadUrl, dryRun) {
  log('--- SpigotMC submission ---');
  await page.goto(`${BASE_URL}/resources/${resourceId}/add-update`, {
    waitUntil: 'networkidle',
    timeout: 30000,
  });

  if (page.url().includes('/login')) {
    log('Session expired, logging in...');
    return false;
  }

  log(`Page loaded: ${await page.title()}`);
  await sleep(2000);

  const versionInput = page.locator('input[name="version_string"]');
  if (await versionInput.isVisible().catch(() => false)) {
    await versionInput.fill(version);
    log(`Filled version: ${version}`);
  }

  const titleInput = page.locator('input[name="title"]');
  if (await titleInput.isVisible().catch(() => false) && title) {
    const cleanTitle = title.replace(/^#\s*/, '');
    await titleInput.fill(cleanTitle);
    log(`Filled title: ${cleanTitle}`);
  }

  const externalRadio = page.locator(
    'input[type="radio"][value*="external"], ' +
    'input[type="radio"][name*="download"][value*="url"], ' +
    'label:has-text("External") input[type="radio"]'
  );
  if (await externalRadio.isVisible().catch(() => false)) {
    await externalRadio.check();
    log('Selected external download link');
    await sleep(500);
  }

  const urlInput = page.locator(
    'input[name="external_url"], input[name*="download_url"], input[type="url"]'
  );
  if (await urlInput.isVisible().catch(() => false)) {
    await urlInput.fill(downloadUrl);
    log(`Filled download URL: ${downloadUrl}`);
  }

  const messageEditor = page.locator('textarea[name="message"]');
  if (await messageEditor.isVisible().catch(() => false) && bbcodeBody) {
    await messageEditor.fill(bbcodeBody);
    log('Filled BBCode description');
  }

  if (dryRun) {
    log('DRY RUN - skipping SpigotMC submission');
    await screenshot(page, 'spigot-dry-run');
    return true;
  }

  const submitBtn = page.locator(
    'input[type="submit"][value="Save"], input[type="submit"][value="Submit"], ' +
    'input[type="submit"][value="Add Update"], button:has-text("Save"), ' +
    'button:has-text("Submit"), button:has-text("Add Update")'
  );

  if (!await submitBtn.first().isVisible().catch(() => false)) {
    await screenshot(page, 'no-submit-button');
    throw new Error('Could not find submit button');
  }

  await submitBtn.first().click();
  log('Form submitted');

  try {
    await page.waitForURL(url => !url.toString().includes('/add-update'), { timeout: 15000 });
    log(`SpigotMC submission successful: ${page.url()}`);
  } catch {
    await screenshot(page, 'submission-result');
    log('SpigotMC submission completed');
  }
  return true;
}

async function release() {
  log('=== SpigotMC & Modrinth Release Automation ===');

  const username = env('SPIGOT_USERNAME');
  const password = env('SPIGOT_PASSWORD');
  const cookiesB64 = process.env.SPIGOT_COOKIES;
  const modrinthToken = process.env.MODRINTH_API_KEY;
  const repoName = env('REPO_NAME');
  const resourceIdsRaw = env('SPIGOT_RESOURCE_IDS');
  const dryRun = process.env.DRY_RUN === 'true';
  const version = env('VERSION');
  const repoOwner = process.env.REPO_OWNER || 'castledking';
  const jarPath = process.env.JAR_PATH || '';

  const resourceIds = parseResourceIds(resourceIdsRaw);
  const resourceId = resourceIds[repoName];
  const repoConfig = loadRepoConfig(repoName);

  if (!resourceId && (!repoConfig || !repoConfig.spigot)) {
    die(`No SpigotMC resource ID found for "${repoName}"`);
  }

  const finalResourceId = resourceId || String(repoConfig.spigot);
  const modrinthSlug = repoConfig?.modrinth || null;
  const downloadUrl = modrinthSlug
    ? `https://modrinth.com/plugin/${modrinthSlug}#download`
    : `https://github.com/${repoOwner}/${repoName}/releases/download/${version}/${repoName}.jar`;

  log(`Releasing ${repoName} v${version}`);
  log(`SpigotMC resource: ${finalResourceId}`);
  log(`Download URL: ${downloadUrl}`);

  // Parse release notes
  let title = '';
  let rawBody = '';
  const notesPath = `${RELEASE_NOTES_DIR}/v${version}.md`;
  if (existsSync(notesPath)) {
    const notes = parseReleaseNotes(notesPath);
    title = notes.title;
    rawBody = notes.body;
  } else {
    log(`No release notes at ${notesPath}`);
  }

  const bbcodeBody = rawBody ? markdownToBbcode(rawBody) : '';

  // -- SpigotMC --
  if (process.env.SKIP_SPIGOT !== 'true') {
    const browser = await chromium.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage', '--disable-gpu'],
    });
    const context = await browser.newContext({
      viewport: { width: 1280, height: 900 },
      userAgent: 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
      locale: 'en-US',
    });
    const page = await context.newPage();

    try {
      let loggedIn = false;
      if (cookiesB64) loggedIn = await loadCookies(page, cookiesB64);
      if (!loggedIn) await login(page, username, password);
      await submitToSpigotmc(page, finalResourceId, version, title, bbcodeBody, downloadUrl, dryRun);
    } catch (err) {
      await screenshot(page, 'error-spigot');
      log('SpigotMC error:', err.message);
      if (!dryRun) throw err;
    } finally {
      await browser.close();
    }
  }

  // -- Modrinth --
  if (modrinthToken && repoConfig?.modrinth && jarPath && existsSync(jarPath)) {
    log('--- Modrinth release ---');
    const projectId = repoConfig.modrinth_project_id || repoConfig.modrinth;

    if (dryRun) {
      log(`DRY RUN - would create Modrinth version ${version} for ${projectId}`);
    } else {
      try {
        const result = await releaseToModrinth({
          token: modrinthToken,
          projectId: projectId,
          version: version,
          changelogMd: rawBody || title || `Version ${version}`,
          gameVersions: repoConfig.game_versions || [],
          loaders: repoConfig.loaders || ['paper'],
          jarPath: jarPath,
          dependencies: repoConfig.dependencies || [],
          versionType: 'release',
          featured: false,
          status: 'listed',
        });
        log(`Modrinth release complete: ${result.id}`);
      } catch (err) {
        log('Modrinth error:', err.message);
        if (!dryRun) throw err;
      }
    }
  } else if (modrinthToken && repoConfig?.modrinth) {
    if (!jarPath) log('Skipping Modrinth: no JAR_PATH provided');
    else if (!existsSync(jarPath)) log(`Skipping Modrinth: JAR not found at ${jarPath}`);
    else log('Skipping Modrinth: no MODRINTH_API_KEY or modrinth slug configured');
  }

  log('=== Release automation complete ===');
}

release().catch(err => die(err.message));
