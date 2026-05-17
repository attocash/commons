const puppeteer = require("puppeteer");

process.env.CHROME_BIN = puppeteer.executablePath();

config.set({
  client: {
    mocha: {
      timeout: 60000
    }
  }
});

config.customLaunchers = config.customLaunchers || {};
config.customLaunchers.PuppeteerChromeHeadless = {
  base: "Chrome",
  flags: [
    "--headless=new",
    "--no-sandbox",
    "--disable-setuid-sandbox",
    "--disable-dev-shm-usage",
    "--ignore-gpu-blocklist",
    "--enable-unsafe-webgpu",
    "--enable-features=Vulkan,WebGPU,WebGPUDeveloperFeatures",
    "--use-angle=vulkan"
  ]
};

config.browsers = ["PuppeteerChromeHeadless"];
