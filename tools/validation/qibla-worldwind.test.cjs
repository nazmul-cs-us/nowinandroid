/* Run with Node + Playwright/Chromium and the @nasaworldwind/worldwind@0.11.0 build/dist directory:
 * node tools/validation/qibla-worldwind.test.cjs /path/to/worldwind/build/dist
 * Exercises the production page with the real WorldWind engine; remote map detail is optional.
 */
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const http = require('node:http');
const root = path.resolve(__dirname, '../..');
const dist = path.resolve(process.argv[2] || 'node_modules/@nasaworldwind/worldwind/build/dist');
const source = fs.readFileSync(path.join(root, 'shared/src/commonMain/kotlin/com/starception/submission/shared/ui/QiblaWorldWindPage.kt'), 'utf8');
const template = source.split('): String = """')[1].split('""".trimIndent()')[0];
const radians = x => x * Math.PI / 180;
function bearing(lat, lon) {
  const p1 = radians(lat), p2 = radians(21.4225), dl = radians(39.8262 - lon);
  return (Math.atan2(Math.sin(dl) * Math.cos(p2), Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)) * 180 / Math.PI + 360) % 360;
}
function pageHtml(lat, lon, expanded) {
  return template.replace(/\$\{if \(expanded\) "([^"\n]*)" else "([^"\n]*)"\}/g, (_, yes, no) => expanded ? yes : no)
    .replaceAll('${expanded}', String(expanded)).replaceAll('$latitude', String(lat)).replaceAll('$longitude', String(lon))
    .replaceAll('$qiblaBearing', String(bearing(lat, lon))).replaceAll('$WORLDWIND_SCRIPT', '/worldwind.min.js');
}
const fixtures = new Map();
const server = http.createServer((request, response) => {
  const url = new URL(request.url, 'http://localhost');
  if (fixtures.has(url.pathname)) {
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    response.end(fixtures.get(url.pathname));
    return;
  }
  const file = path.resolve(dist, '.' + decodeURIComponent(url.pathname));
  if (!file.startsWith(dist + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    response.writeHead(404).end(); return;
  }
  response.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.png') ? 'image/png' : 'image/jpeg');
  fs.createReadStream(file).pipe(response);
});
(async () => {
  assert.ok(fs.existsSync(path.join(dist, 'worldwind.min.js')), 'Pass the WorldWind build/dist directory');
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium', headless:true,
    args:['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader'] });
  const cities = [['Dubai',25.2048,55.2708],['London',51.5074,-.1278],['Toronto',43.6532,-79.3832],
    ['Jakarta',-6.2,106.8167],['Sydney',-33.8688,151.2093],['Auckland',-36.8485,174.7633],
    ['Date line',0,179.8],['Tromso',69.6492,18.9553],['Cape Town',-33.9249,18.4241],['Kaaba',21.4225,39.8262]];
  let cases = 0;
  try {
    for (const [name, lat, lon] of cities) for (const viewport of [{width:390,height:430},{width:700,height:300}]) {
      const url = `/case-${cases}.html`; fixtures.set(url, pageHtml(lat,lon,true));
      const page = await browser.newPage({viewport,deviceScaleFactor:cases % 2 + 1});
      const errors=[]; page.on('pageerror', error => errors.push(error.message));
      // Use the packaged base Earth imagery. The live high-detail tile servers are
      // intentionally excluded so network availability cannot mask projection failures.
      await page.route('**/*', route => route.request().url().startsWith(base) ? route.continue() : route.abort());
      await page.goto(base + url);
      await page.waitForFunction(() => document.querySelector('#kaaba-marker').style.visibility === 'visible');
      assert.deepEqual(errors, [], name + ': script errors');
      const initial = await page.locator('#heading-cone').getAttribute('opacity');
      assert.equal(initial, '0', 'No heading must hide the cone without hiding the locations');
      await page.evaluate(h => window.updateCompassHeading(h, 5), bearing(lat,lon));
      if (name !== 'Kaaba') await page.waitForFunction(() => document.querySelector('#heading-cone').dataset.angle !== undefined);
      const state = await page.evaluate(() => {
        const center = id => {
          const matrix=document.getElementById(id).transform.baseVal.getItem(0).matrix;
          return {x:matrix.e,y:matrix.f};
        };
        return {user:center('user-marker'),kaaba:center('kaaba-marker'), angle:Number(document.getElementById('heading-cone').dataset.angle),
          color:document.querySelector('#heading-light stop').getAttribute('stop-color'),
          label:document.getElementById('kaaba-label').textContent,hiddenUser:document.getElementById('user-marker').style.visibility === 'hidden'};
      });
      for (const [label, point] of Object.entries({user:state.user,kaaba:state.kaaba})) {
        assert.ok(Number.isFinite(point.x) && Number.isFinite(point.y), name + ': invalid ' + label);
        assert.ok(point.x >= 20 && point.x <= viewport.width-20 && point.y >= 30 && point.y <= viewport.height-30,
          `${name}: ${label} outside camera frame: ${JSON.stringify(point)}`);
      }
      if (name === 'Kaaba') {
        assert.equal(state.label,'You · Kaaba'); assert.ok(state.hiddenUser);
      } else {
        assert.ok(state.kaaba.y < state.user.y, name + ': Kaaba should be above user');
        const routeAngle=Math.atan2(state.kaaba.x-state.user.x,state.user.y-state.kaaba.y)*180/Math.PI;
        const error=Math.abs(((state.angle-routeAngle+540)%360)-180);
        assert.ok(error < 2, `${name}: aligned facing cone misses Kaaba by ${error} degrees`);
        assert.equal(state.color, '#00c853', name + ': aligned cone should be green');
      }
      if (name === 'Dubai' && viewport.width === 390) {
        await page.screenshot({path:'/tmp/qibla-direction-parity.png'});
        await page.evaluate(() => window.updateCompassHeading(359, 5));
        await page.waitForTimeout(800);
        const before=await page.locator('#heading-cone').getAttribute('data-angle');
        await page.evaluate(() => window.updateCompassHeading(1, 5));
        await page.waitForTimeout(800);
        const after=await page.locator('#heading-cone').getAttribute('data-angle');
        assert.ok(Math.abs(((Number(after)-Number(before)+540)%360)-180)<5,'North crossing takes short arc');
        await page.evaluate(() => window.updateCompassHeading(null, null));
        assert.equal(await page.locator('#heading-cone').getAttribute('opacity'),'0');
        assert.equal(await page.locator('#user-marker').evaluate(n => n.style.visibility),'visible');
        await page.setViewportSize({width:320,height:352});
        await page.waitForTimeout(150);
        const box=await page.locator('#overlay').getAttribute('viewBox');
        assert.equal(box,'0 0 320 352','Resizing refits camera and overlay coordinates');
      }
      await page.close(); cases++; console.log(`PASS ${name} ${viewport.width}x${viewport.height}`);
    }
    // The circular full-screen Qibla mode shares this page with the bottom sheet.
    fixtures.set('/circular.html',pageHtml(25.2048,55.2708,false));
    const circular=await browser.newPage({viewport:{width:350,height:350}});
    await circular.route('**/*', route => route.request().url().startsWith(base) ? route.continue() : route.abort());
    await circular.goto(base+'/circular.html');
    await circular.waitForFunction(() => document.querySelector('#kaaba-marker').style.visibility === 'visible');
    assert.equal(await circular.locator('#guidance').textContent(),'');
    await circular.close();
    console.log(`${cases+1} WorldWind rendering cases passed`);
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); server.close(); process.exitCode=1; });
