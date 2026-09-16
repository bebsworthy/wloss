// Internal browser capture adapter for agent.py; options arrive as JSON on stdin.
const fs = require('node:fs');
(async () => {
  const options = JSON.parse(fs.readFileSync(0, 'utf8'));
  const {chromium} = require('playwright');
  const browser = await chromium.launch({headless:true});
  try {
    const page = await browser.newPage({viewport:{width:options.viewportWidth,height:options.viewportHeight},deviceScaleFactor:1});
    await page.goto(options.url, {waitUntil:'load',timeout:30000});
    await page.evaluate(() => document.fonts.ready);
    if (options.prepareScript) await page.evaluate(script => (0, eval)(script), options.prepareScript);
    for (const selector of options.clicks) await page.locator(selector).click();
    const target = page.locator(options.selector);
    if (await target.count() !== 1) throw Error('Capture selector must match exactly one element');
    const bounds = await target.boundingBox();
    let anchor = null;
    if (options.anchorSelector) {
      const node = page.locator(options.anchorSelector);
      if (await node.count() !== 1) throw Error('Reference anchor must match exactly one element');
      const a = await node.boundingBox();
      if (!a) throw Error('Reference anchor is not visible');
      anchor = {x:a.x-bounds.x,y:a.y-bounds.y,width:a.width,height:a.height};
    }
    await target.screenshot({path:options.output,animations:'disabled'});
    fs.writeFileSync(options.metadata,JSON.stringify({url:options.url,bounds,anchor,anchorSelector:options.anchorSelector},null,2));
  } finally { await browser.close(); }
})().catch(error=>{console.error(error.message);process.exitCode=1});
