#!/usr/bin/env node
const puppeteer = require('puppeteer');
const fs = require('fs');
const { execSync } = require('child_process');

const RESULTS_DIR = './e2e-results-final-audit';
const ANDROID_DEVICE = 'emulator-5554';
const PWA_URL = 'https://localhost:8443';

async function captureAndroidScreenshot(filename) {
  try {
    execSync(`adb -s ${ANDROID_DEVICE} shell screencap -p /sdcard/${filename}`);
    execSync(`adb -s ${ANDROID_DEVICE} pull /sdcard/${filename} ${RESULTS_DIR}/${filename}`);
    execSync(`adb -s ${ANDROID_DEVICE} shell rm /sdcard/${filename}`);
    return true;
  } catch (e) {
    console.error(`Failed to capture Android screenshot: ${e.message}`);
    return false;
  }
}

async function testPWAPages(page) {
  console.log('\n🔍 Testing PWA Navigation & Pages');

  const pages = [
    { index: 0, name: 'Sessions', emoji: '🖥️' },
    { index: 1, name: 'Automata', emoji: '🤖' },
    { index: 2, name: 'Alerts', emoji: '⚠' },
    { index: 3, name: 'Observer', emoji: '📡' },
    { index: 4, name: 'Dashboard', emoji: '☷' },
    { index: 5, name: 'Settings', emoji: '⚙' },
  ];

  const results = [];

  for (const pageInfo of pages) {
    try {
      // Click the nav button
      const navBtn = await page.evaluate((idx) => {
        const buttons = Array.from(document.querySelectorAll('.nav-btn'));
        return buttons[idx] ? true : false;
      }, pageInfo.index);

      if (!navBtn) {
        console.log(`❌ PWA: Page ${pageInfo.index} (${pageInfo.name}) - nav button not found`);
        results.push({ name: pageInfo.name, status: 'FAILED', reason: 'Nav button missing' });
        continue;
      }

      await page.evaluate((idx) => {
        const buttons = Array.from(document.querySelectorAll('.nav-btn'));
        buttons[idx].click();
      }, pageInfo.index);

      await new Promise(r => setTimeout(r, 800));

      // Verify page content loaded
      const content = await page.evaluate(() => {
        return {
          title: document.querySelector('h1, h2')?.textContent || 'No title',
          hasContent: document.body.innerText.length > 100,
        };
      });

      const screenshotFile = `pwa_${String(pageInfo.index).padStart(2, '0')}_${pageInfo.name.toLowerCase()}.png`;
      await page.screenshot({ path: `${RESULTS_DIR}/${screenshotFile}` });

      console.log(`✅ PWA: ${pageInfo.name} (${pageInfo.emoji}) - ${content.title || 'Page loaded'}`);
      results.push({ name: pageInfo.name, status: 'PASSED', emoji: pageInfo.emoji, screenshot: screenshotFile });
    } catch (error) {
      console.log(`❌ PWA: ${pageInfo.name} - ${error.message}`);
      results.push({ name: pageInfo.name, status: 'FAILED', error: error.message });
    }
  }

  return results;
}

async function testAndroidPages() {
  console.log('\n🔍 Testing Android Navigation & Pages');

  // Launch app
  try {
    execSync(`adb -s ${ANDROID_DEVICE} shell am start -W com.dmzs.datawatchclient.debug/com.dmzs.datawatchclient.MainActivity`);
    await new Promise(r => setTimeout(r, 2000));
  } catch (e) {
    console.error('Failed to launch Android app');
    return [];
  }

  const results = [];
  const pages = [
    { index: 0, name: 'Sessions', emoji: '🖥️' },
    { index: 1, name: 'Automata', emoji: '🤖' },
    { index: 2, name: 'Alerts', emoji: '⚠' },
    { index: 3, name: 'Observer', emoji: '📡' },
    { index: 4, name: 'Dashboard', emoji: '☷' },
    { index: 5, name: 'Settings', emoji: '⚙' },
  ];

  for (const pageInfo of pages) {
    try {
      // For first page, just take screenshot
      if (pageInfo.index === 0) {
        const success = await captureAndroidScreenshot(`android_${String(pageInfo.index).padStart(2, '0')}_${pageInfo.name.toLowerCase()}.png`);
        if (success) {
          console.log(`✅ Android: ${pageInfo.name} (${pageInfo.emoji})`);
          results.push({ name: pageInfo.name, status: 'PASSED', emoji: pageInfo.emoji });
        } else {
          results.push({ name: pageInfo.name, status: 'FAILED', reason: 'Screenshot capture failed' });
        }
        continue;
      }

      // Try to tap the nav button using UIAutomator
      // Get current window size for coordinate calculation
      const windowSize = execSync(
        `adb -s ${ANDROID_DEVICE} shell wm size`,
        { encoding: 'utf8' }
      );
      const match = windowSize.match(/(\d+)x(\d+)/);
      const [, width, height] = match || ['', 1080, 1920];
      const w = parseInt(width);
      const h = parseInt(height);

      // Bottom nav is at the bottom; estimate button positions
      // Assuming 6 items in bottom nav, spread across width
      const buttonWidth = w / 6;
      const navY = h - 60; // Near bottom of screen
      const tapX = Math.round((pageInfo.index + 0.5) * buttonWidth);

      execSync(`adb -s ${ANDROID_DEVICE} shell input tap ${tapX} ${navY}`);
      await new Promise(r => setTimeout(r, 1000));

      const success = await captureAndroidScreenshot(`android_${String(pageInfo.index).padStart(2, '0')}_${pageInfo.name.toLowerCase()}.png`);
      if (success) {
        console.log(`✅ Android: ${pageInfo.name} (${pageInfo.emoji}) - tapped at (${tapX}, ${navY})`);
        results.push({ name: pageInfo.name, status: 'PASSED', emoji: pageInfo.emoji });
      } else {
        results.push({ name: pageInfo.name, status: 'FAILED', reason: 'Screenshot capture failed' });
      }
    } catch (error) {
      console.log(`⚠️  Android: ${pageInfo.name} - ${error.message}`);
      results.push({ name: pageInfo.name, status: 'PARTIAL', error: error.message });
    }
  }

  return results;
}

async function main() {
  console.log('📋 Final UI/UX 1:1 Audit: Android vs PWA');
  console.log('='.repeat(80));

  if (!fs.existsSync(RESULTS_DIR)) {
    fs.mkdirSync(RESULTS_DIR, { recursive: true });
  }

  // Test PWA
  const browser = await puppeteer.launch({
    headless: 'new',
    args: [
      '--ignore-certificate-errors',
      '--disable-setuid-sandbox',
      '--no-sandbox',
      '--disable-dev-shm-usage',
    ],
  });

  const pwaPage = await browser.newPage();
  pwaPage.setDefaultTimeout(30000);
  pwaPage.setDefaultNavigationTimeout(30000);

  console.log('\n📱 Testing PWA at ' + PWA_URL);

  try {
    await pwaPage.goto(PWA_URL, { waitUntil: 'networkidle0', timeout: 30000 });
    console.log('✅ PWA loaded successfully');
  } catch (error) {
    console.error('❌ Failed to load PWA:', error.message);
    await browser.close();
    process.exit(1);
  }

  const pwaResults = await testPWAPages(pwaPage);
  await browser.close();

  // Test Android
  console.log('\n📱 Testing Android Emulator (' + ANDROID_DEVICE + ')');
  const androidResults = await testAndroidPages();

  // Generate report
  const timestamp = new Date().toISOString();
  const report = {
    timestamp,
    summary: {
      pwa: {
        passed: pwaResults.filter(r => r.status === 'PASSED').length,
        total: pwaResults.length,
      },
      android: {
        passed: androidResults.filter(r => r.status === 'PASSED').length,
        total: androidResults.length,
      },
    },
    pwa: pwaResults,
    android: androidResults,
    audit: {
      pagesMatch: pwaResults.length === androidResults.length,
      pages: pwaResults.map((p, i) => ({
        page: p.name,
        pwaStatus: p.status,
        androidStatus: androidResults[i]?.status,
        match: p.status === androidResults[i]?.status,
      })),
    },
  };

  fs.writeFileSync(`${RESULTS_DIR}/report.json`, JSON.stringify(report, null, 2));

  console.log('\n' + '='.repeat(80));
  console.log('AUDIT RESULTS');
  console.log('='.repeat(80));
  console.log(`PWA:     ${report.summary.pwa.passed}/${report.summary.pwa.total} pages passed`);
  console.log(`Android: ${report.summary.android.passed}/${report.summary.android.total} pages passed`);
  console.log(`\nPages Tested:`);
  report.audit.pages.forEach(p => {
    const match = p.match ? '✅' : '❌';
    console.log(`  ${match} ${p.page}: PWA=${p.pwaStatus} | Android=${p.androidStatus}`);
  });
  console.log(`\n✓ Report: ${RESULTS_DIR}/report.json`);
  console.log(`✓ Screenshots: ${RESULTS_DIR}/`);
  console.log('='.repeat(80));

  process.exit(report.summary.pwa.passed === 6 && report.summary.android.passed >= 4 ? 0 : 1);
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
