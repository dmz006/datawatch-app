#!/usr/bin/env node
const puppeteer = require('puppeteer');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

(async () => {
  const browser = await puppeteer.launch({
    headless: 'new',
    ignoreHTTPSErrors: true,
    args: ['--no-sandbox', '--ignore-certificate-errors']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1080, height: 1920 });

  try {
    await page.goto('https://localhost:8443/', { waitUntil: 'domcontentloaded', timeout: 20000 });
    await delay(2000);

    // Debug button structure
    const buttons = await page.evaluate(() => {
      const allButtons = Array.from(document.querySelectorAll('button'));
      return allButtons.map((b, i) => ({
        idx: i,
        text: b.textContent?.trim().substring(0, 30),
        classList: b.className,
        disabled: b.disabled,
        visible: b.offsetHeight > 0 && b.offsetWidth > 0,
        html: b.innerHTML.substring(0, 60),
        rect: (() => {
          const r = b.getBoundingClientRect();
          return `(${Math.round(r.top)},${Math.round(r.left)},${Math.round(r.width)}x${Math.round(r.height)})`;
        })()
      }));
    });

    console.log('ALL BUTTONS IN PAGE:');
    buttons.forEach(b => {
      if (b.visible) {
        console.log(`  ${b.idx}: text="${b.text}" disabled=${b.disabled} ${b.rect} class="${b.classList}"`);
      }
    });

    // Look for navigation specifically
    const nav = await page.evaluate(() => {
      const candidates = [
        ...document.querySelectorAll('[class*="tab"]'),
        ...document.querySelectorAll('[class*="nav"]'),
        ...document.querySelectorAll('nav'),
        ...document.querySelectorAll('[class*="bottom"]')
      ];

      // Filter unique elements
      const unique = Array.from(new Set(candidates));
      
      return unique.map(el => ({
        tag: el.tagName,
        class: el.className,
        children_count: el.children.length,
        buttons: Array.from(el.querySelectorAll('button')).length,
        position: (() => {
          const r = el.getBoundingClientRect();
          return `(${Math.round(r.top)},${Math.round(r.left)},${Math.round(r.width)}x${Math.round(r.height)})`;
        })()
      }));
    });

    console.log('\nNAVIGATION CANDIDATES:');
    nav.forEach(n => {
      console.log(`  <${n.tag}> class="${n.class}" buttons=${n.buttons} ${n.position}`);
    });

  } catch (error) {
    console.error('Error:', error.message);
  } finally {
    await browser.close();
  }
})();
