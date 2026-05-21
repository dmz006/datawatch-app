#!/usr/bin/env node

/**
 * Automated PWA E2E Validation Suite v5 - Fixed Navigation
 */

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

class E2EValidatorV5 {
  constructor() {
    this.outputDir = './e2e-results-v5';
    this.results = [];
    this.screenshots = [];
    this.startTime = new Date();
  }

  log(message) {
    const timestamp = new Date().toISOString().substring(11, 23);
    console.log(`ℹ️  ${timestamp} ${message}`);
  }

  recordTest(name, status, details = '') {
    this.results.push({ name, status, details });
  }

  async captureScreenshot(page, name) {
    const filename = `${String(this.results.length).padStart(2, '0')}_${name.replace(/\s+/g, '_').toLowerCase()}.png`;
    const filepath = path.join(this.outputDir, 'screenshots', filename);
    try {
      await page.screenshot({ path: filepath, fullPage: true });
      this.screenshots.push({ name, path: filename });
      return true;
    } catch (e) {
      console.error(`❌ Screenshot failed: ${e.message}`);
      return false;
    }
  }

  async runTests() {
    // Create output dirs
    if (!fs.existsSync(this.outputDir)) fs.mkdirSync(this.outputDir, { recursive: true });
    if (!fs.existsSync(path.join(this.outputDir, 'screenshots'))) {
      fs.mkdirSync(path.join(this.outputDir, 'screenshots'), { recursive: true });
    }

    const browser = await puppeteer.launch({
      headless: 'new',
      ignoreHTTPSErrors: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--ignore-certificate-errors']
    });

    try {
      const page = await browser.newPage();
      page.setDefaultTimeout(20000);
      await page.setViewport({ width: 1080, height: 1920 });

      this.log('Starting E2E Suite v5 - Navigation & Screenshots');

      // Load PWA
      try {
        await page.goto('https://localhost:8443/', { waitUntil: 'domcontentloaded', timeout: 20000 });
      } catch (e) {
        console.log(`⚠️  Navigation warning: ${e.message}`);
      }

      await delay(2000);
      await this.captureScreenshot(page, 'Initial Load');

      const hasContent = await page.$('main, body > div');
      if (!hasContent) {
        this.recordTest('PWA Load', 'FAILED', 'No content');
        throw new Error('PWA did not load');
      }
      this.recordTest('PWA Load', 'PASSED', 'Content found');

      // Find nav buttons
      const navButtons = await page.$$('.nav-btn');
      this.log(`Found ${navButtons.length} navigation buttons`);

      const pageNames = ['Sessions', 'Automata', 'Alerts', 'Observer', 'Dashboard', 'Settings'];

      // Test navigation to each page
      for (let i = 0; i < Math.min(navButtons.length, 6); i++) {
        try {
          this.log(`Clicking tab ${i}: ${pageNames[i] || 'Unknown'}`);
          await navButtons[i].click();
          await delay(1200);
          await this.captureScreenshot(page, `${pageNames[i] || `Tab${i}`} Page`);
          this.recordTest(`Page: ${pageNames[i] || `Tab ${i}`}`, 'PASSED', 'Navigated');
        } catch (error) {
          console.error(`❌ Tab ${i} error: ${error.message}`);
          this.recordTest(`Page: ${pageNames[i] || `Tab ${i}`}`, 'FAILED', error.message);
        }
      }

      this.log('All tests completed');

    } catch (error) {
      console.error(`❌ Fatal error: ${error.message}`);
      this.recordTest('Suite', 'FAILED', error.message);
    } finally {
      await this.generateReport();
      await browser.close();
    }
  }

  async generateReport() {
    const duration = ((new Date() - this.startTime) / 1000).toFixed(1);
    const passed = this.results.filter(r => r.status === 'PASSED').length;
    const failed = this.results.filter(r => r.status === 'FAILED').length;
    const total = this.results.length;
    const passRate = total > 0 ? Math.round((passed / total) * 100) : 0;

    const report = {
      timestamp: this.startTime.toISOString(),
      duration: parseFloat(duration),
      summary: { passed, failed, total, passRate },
      results: this.results,
      screenshots: this.screenshots
    };

    const reportPath = path.join(this.outputDir, 'report.json');
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));

    console.log(`\n${'='.repeat(80)}`);
    console.log('E2E VALIDATION REPORT v5');
    console.log(`${'='.repeat(80)}`);
    console.log(`Duration: ${duration}s`);
    console.log(`Results: ${passed}✅ | ${failed}❌ / ${total} total`);
    console.log(`Pass Rate: ${passRate}%`);
    console.log(`\n✓ Report: ${reportPath}`);
    console.log(`✓ Screenshots: ${this.screenshots.length} captured`);
    console.log(`${'='.repeat(80)}\n`);
  }
}

const validator = new E2EValidatorV5();
validator.runTests().catch(console.error);
