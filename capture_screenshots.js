const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const PAGES = [
  { name: 'Sessions', emoji: '🖥️', selector: '.nav-btn', text: 'Sessions' },
  { name: 'Automata', emoji: '🤖', selector: '.nav-btn', text: 'Automata' },
  { name: 'Alerts', emoji: '⚠', selector: '.nav-btn', text: 'Alerts' },
  { name: 'Observer', emoji: '📡', selector: '.nav-btn', text: 'Observer' },
  { name: 'Dashboard', emoji: '☷', selector: '.nav-btn', text: 'Dashboard' },
  { name: 'Settings', emoji: '⚙', selector: '.nav-btn', text: 'Settings' },
];

const BASE_URL = 'https://localhost:8443';
const OUTPUT_DIR = '/home/dmz/workspace/datawatch-app/docs/media/pwa';

async function captureScreenshots() {
  console.log('🎬 Capturing PWA screenshots...\n');

  const browser = await puppeteer.launch({
    headless: true,
    args: ['--ignore-certificate-errors', '--no-sandbox'],
  });

  try {
    const page = await browser.newPage();
    page.setViewport({ width: 800, height: 600 });

    console.log(`📍 Navigating to ${BASE_URL}...`);
    await page.goto(BASE_URL, { waitUntil: 'networkidle2', timeout: 30000 });
    await page.waitForTimeout(2000);

    // Create output directory
    if (!fs.existsSync(OUTPUT_DIR)) {
      fs.mkdirSync(OUTPUT_DIR, { recursive: true });
    }

    // Capture each page
    for (let i = 0; i < PAGES.length; i++) {
      const pageInfo = PAGES[i];
      console.log(`\n[${i + 1}/6] Capturing ${pageInfo.emoji} ${pageInfo.name}...`);

      if (i > 0) {
        // Click on the nav button for this page
        await page.click(`.nav-btn:nth-child(${i + 1})`);
        await page.waitForTimeout(1000);
      }

      // Verify page loaded
      const pageTitle = await page.evaluate(() => document.title);
      console.log(`  ✓ Page loaded: ${pageTitle}`);

      // Take screenshot
      const filename = `${String(i).padStart(2, '0')}-${pageInfo.name.toLowerCase()}-v1.0.0.png`;
      const filepath = path.join(OUTPUT_DIR, filename);

      await page.screenshot({
        path: filepath,
        fullPage: false,
      });

      const stats = fs.statSync(filepath);
      console.log(`  ✓ Screenshot saved: ${filename} (${(stats.size / 1024).toFixed(1)}KB)`);
    }

    console.log('\n✅ PWA screenshot capture complete!\n');
  } finally {
    await browser.close();
  }
}

captureScreenshots().catch(err => {
  console.error('❌ Error:', err);
  process.exit(1);
});
