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
    base: "ChromeHeadless",
    flags: [
        "--no-sandbox",
        "--disable-setuid-sandbox",
        "--disable-dev-shm-usage",
        "--enable-unsafe-webgpu",
        "--enable-features=Vulkan",
        "--use-angle=vulkan"
    ]
};

config.browsers = ["PuppeteerChromeHeadless"];
