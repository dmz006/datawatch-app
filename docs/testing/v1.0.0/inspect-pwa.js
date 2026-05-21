#!/usr/bin/env node
const puppeteer = require('puppeteer');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

async function inspect() {
  const browser = await puppeteer.launch({
    headless: 'new',
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--disable-blink-features=AutomationControlled',
      '--ignore-certificate-errors'
    ]
  });

  try {
    const page = await browser.newPage();
    page.setDefaultTimeout(15000);
    await page.setViewport({ width: 1080, height: 1920 });

    console.log('🔍 Loading PWA with certificate bypass...');
    
    try {
      await page.goto('https://localhost:8443/', {
        waitUntil: 'domcontentloaded',
        timeout: 20000
      });
    } catch (e) {
      console.log('  Navigation error (expected for self-signed cert):', e.message);
    }

    await delay(2000);

    // Click proceed button if present
    const proceedBtn = await page.$('a.small-link');
    if (proceedBtn) {
      console.log('  Found SSL proceed link, attempting click...');
      await proceedBtn.click();
      await delay(3000);
    }

    console.log('📸 Taking screenshot...');
    await page.screenshot({ path: 'pwa-inspect-initial.png', fullPage: true });

    const pageTitle = await page.title();
    console.log('\n✅ Current page: ' + pageTitle + '\n');

    // Get the main content
    const content = await page.evaluate(() => ({
      title: document.title,
      has_main: !!document.querySelector('main'),
      body_html_length: document.body.innerHTML.length,
      visible_text: document.body.innerText.substring(0, 200)
    }));

    console.log('HTML loaded:', content.body_html_length, 'chars');
    console.log('Has main element:', content.has_main);
    console.log('Visible text:', content.visible_text);

  } catch (error) {
    console.error('❌ Error:', error.message);
  } finally {
    await browser.close();
  }
}

inspect();
