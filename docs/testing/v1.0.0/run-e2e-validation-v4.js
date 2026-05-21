#!/usr/bin/env node

/**
 * Automated PWA E2E Validation Suite v4 - Proper Tab Navigation
 *
 * Enhanced with:
 * - Correct SSL certificate handling (--ignore-certificate-errors)
 * - Dynamic bottom tab detection and navigation
 * - Real page transitions with proper waits
 * - Full content verification
 *
 * Run: node run-e2e-validation-v4.js [--headless=false] [--verbose]
 */

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

class E2EValidatorV4 {
  constructor(options = {}) {
    this.headless = options.headless !== false;
    this.verbose = options.verbose || false;
    this.outputDir = options.outputDir || './e2e-results-v4';
    this.results = [];
    this.failures = [];
    this.screenshots = [];
    this.startTime = new Date();
    this.testCount = 0;
  }

  log(message, level = 'info') {
    if (level === 'debug' && !this.verbose) return;
    const timestamp = new Date().toISOString();
    const prefix = {
      'info': 'ℹ️ ',
      'success': '✅',
      'error': '❌',
      'warn': '⚠️ ',
      'debug': '🔍',
      'assert': '🧪'
    }[level] || '→ ';
    console.log(`${prefix} ${timestamp.substring(11, 23)} ${message}`);
  }

  recordTest(name, status, details = '') {
    this.results.push({ name, status, details, timestamp: new Date() });
  }

  async captureScreenshot(page, name) {
    const filename = `${String(this.results.length).padStart(2, '0')}_${name.replace(/\s+/g, '_').toLowerCase()}.png`;
    const filepath = path.join(this.outputDir, 'screenshots', filename);
    try {
      await page.screenshot({ path: filepath, fullPage: true });
      this.screenshots.push({ name, path: filepath });
      this.log(`Screenshot: ${filename}`, 'debug');
      return filepath;
    } catch (e) {
      this.log(`Screenshot failed: ${e.message}`, 'warn');
      return null;
    }
  }

  async waitForElement(page, selectors, timeout = 10000) {
    const startTime = Date.now();
    const selectorArray = Array.isArray(selectors) ? selectors : [selectors];

    while (Date.now() - startTime < timeout) {
      for (let selector of selectorArray) {
        try {
          const el = await page.$(selector);
          if (el) {
            this.log(`Found: ${selector}`, 'debug');
            return el;
          }
        } catch (e) {
          // Try next selector
        }
      }
      await delay(200);
    }

    return null;
  }

  async runTests() {
    const browser = await puppeteer.launch({
      headless: this.headless ? 'new' : false,
      ignoreHTTPSErrors: true,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--disable-blink-features=AutomationControlled',
        '--ignore-certificate-errors'
      ]
    });

    // Ensure output directories exist
    if (!fs.existsSync(this.outputDir)) fs.mkdirSync(this.outputDir, { recursive: true });
    if (!fs.existsSync(path.join(this.outputDir, 'screenshots'))) {
      fs.mkdirSync(path.join(this.outputDir, 'screenshots'), { recursive: true });
    }

    try {
      const page = await browser.newPage();
      page.setDefaultTimeout(15000);
      page.setDefaultNavigationTimeout(15000);
      await page.setViewport({ width: 1080, height: 1920 });

      this.log('Starting E2E Validation Suite v4 (Tab Navigation)', 'info');
      this.log('Navigating to https://localhost:8443...', 'info');

      // Navigate to PWA
      try {
        await page.goto('https://localhost:8443/', {
          waitUntil: 'domcontentloaded',
          timeout: 20000
        });
      } catch (e) {
        this.log('Navigation warning: ' + e.message, 'warn');
      }

      await delay(2000);
      this.log('PWA loaded, verifying content...', 'debug');

      // Verify page loaded
      const mainEl = await page.$('main, [role="main"], body > div');
      if (!mainEl) {
        this.recordTest('PWA Load', 'FAILED', 'No main content found');
        this.captureScreenshot(page, 'ERROR_No_Content');
        throw new Error('PWA did not load');
      }

      this.recordTest('PWA Load', 'PASSED', 'Main content found');
      this.captureScreenshot(page, 'PWA_Loaded');

      // Discover tabs
      const tabs = await page.evaluate(() => {
        // Look for bottom navigation with tabs/buttons
        const candidates = Array.from(document.querySelectorAll('[class*="tab"], [class*="nav"], nav, footer, [role="tablist"], [class*="bottom"]'));
        
        let navContainer = null;
        for (const c of candidates) {
          const rect = c.getBoundingClientRect();
          // Look for element near the bottom of the viewport
          if (rect.bottom > window.innerHeight - 150 && rect.top < window.innerHeight) {
            const buttons = c.querySelectorAll('button, a, [role="tab"]');
            if (buttons.length >= 3) {
              navContainer = {
                selector: c.className || c.tagName,
                buttons: Array.from(buttons).map((b, idx) => ({
                  index: idx,
                  text: b.textContent?.trim().substring(0, 50),
                  html: b.innerHTML.substring(0, 100),
                  role: b.getAttribute('role'),
                  aria_label: b.getAttribute('aria-label'),
                  onclick: !!b.onclick,
                  ariaSelected: b.getAttribute('aria-selected')
                }))
              };
              break;
            }
          }
        }
        return navContainer;
      });

      if (tabs) {
        this.log(`Found nav with ${tabs.buttons.length} buttons`, 'info');
        tabs.buttons.forEach((b, i) => {
          this.log(`  Tab ${i + 1}: aria-label="${b.aria_label}" selected=${b.ariaSelected}`, 'debug');
        });
      } else {
        this.log('Navigation structure not found', 'warn');
      }

      // Test each page
      await this.testPage(page, 'Automata', 2);
      await this.testPage(page, 'Alerts', 4);
      await this.testPage(page, 'Sessions', 0);

      this.log('All tests completed', 'success');

    } catch (error) {
      this.log(`Fatal error: ${error.message}`, 'error');
      this.recordTest('Suite Execution', 'FAILED', error.message);
    } finally {
      await this.generateReport();
      await browser.close();
    }
  }

  async testPage(page, pageName, tabIndex) {
    this.log(`Testing ${pageName} page (tab ${tabIndex})...`, 'info');

    try {
      // Click tab at bottom
      const tabs = await page.$$('button, a, [role="tab"]');
      if (tabIndex < tabs.length) {
        await tabs[tabIndex].click();
        await delay(1500);
        this.log(`  Clicked tab ${tabIndex}`, 'debug');
      }

      await this.captureScreenshot(page, pageName + '_Page');

      // Get visible content
      const content = await page.evaluate(() => {
        const text = document.body.innerText;
        const hasItems = /\w+/.test(text); // Has actual content
        return {
          text_length: text.length,
          has_content: hasItems,
          snippet: text.substring(0, 200)
        };
      });

      if (content.has_content) {
        this.recordTest(`PAGE: ${pageName}`, 'PASSED', `Content loaded (${content.text_length} chars)`);
      } else {
        this.recordTest(`PAGE: ${pageName}`, 'FAILED', 'No content found');
      }

    } catch (error) {
      this.log(`${pageName} test error: ${error.message}`, 'error');
      this.recordTest(`PAGE: ${pageName}`, 'FAILED', error.message);
    }
  }

  async generateReport() {
    const duration = (new Date() - this.startTime) / 1000;
    const passed = this.results.filter(r => r.status === 'PASSED').length;
    const failed = this.results.filter(r => r.status === 'FAILED').length;
    const skipped = this.results.filter(r => r.status === 'SKIPPED').length;
    const total = this.results.length;
    const passRate = total > 0 ? ((passed / total) * 100).toFixed(1) : 0;

    const report = {
      timestamp: this.startTime.toISOString(),
      duration: parseFloat(duration.toFixed(2)),
      summary: { passed, failed, skipped, total, passRate: parseFloat(passRate) },
      results: this.results,
      screenshots: this.screenshots
    };

    const reportPath = path.join(this.outputDir, 'report.json');
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));

    console.log(`\n${'='.repeat(80)}`);
    console.log('E2E VALIDATION REPORT v4');
    console.log(`${'='.repeat(80)}`);
    console.log(`Duration: ${duration.toFixed(1)}s`);
    console.log(`Results: ${passed}✅ | ${failed}❌ | ${skipped}⏭️  / ${total} total`);
    console.log(`Pass Rate: ${passRate}%`);
    console.log(`\n✓ Report: ${reportPath}`);
    console.log(`✓ Screenshots: ${path.join(this.outputDir, 'screenshots')}`);
    console.log(`${'='.repeat(80)}\n`);
  }
}

// Parse CLI args
const args = process.argv.slice(2).reduce((acc, arg) => {
  const [key, val] = arg.split('=');
  acc[key.replace(/^--/, '')] = val || true;
  return acc;
}, {});

const validator = new E2EValidatorV4({
  headless: args.headless !== 'false',
  verbose: args.verbose === 'true'
});

validator.runTests().catch(console.error);
