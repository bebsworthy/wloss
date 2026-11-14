/* Run from the repository root with Playwright in NODE_PATH and a local server on 8766. */
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const out='docs/design/flows/evidence/WLO-0166';
(async()=>{
 const browser=await chromium.launch({headless:true});
 const page=await browser.newPage({viewport:{width:1400,height:1120}});
 const errors=[],external=[],checks=[];
 page.on('pageerror',e=>errors.push(e.message));
 page.on('request',r=>{if(!r.url().startsWith('http://127.0.0.1:8766/'))external.push(r.url());});
 const url='http://127.0.0.1:8766/docs/design/flows/12-recipe-creation.html';
 const act=async(a,extra='')=>page.locator(`[data-action="${a}"]${extra}`).click();
 const shot=async(name)=>{await page.evaluate(()=>document.fonts.ready);await page.locator('.device').screenshot({path:`${out}/${name}.png`,animations:'disabled'});};
 const state=()=>page.evaluate(()=>window.recipeDemo.state);
 try {
 await page.goto(url);await shot('library');
 await page.screenshot({path:`${out}/workbench.png`,fullPage:true});
 await act('back');await shot('agenda');await act('add-meal','[data-meal="Dinner"]');await shot('acquisition');
 for(let i=0;i<12;i++){await page.keyboard.press('Tab');assert(await page.evaluate(()=>!!document.activeElement.closest('dialog')));}
 await page.keyboard.press('Escape');assert.equal(await page.locator('dialog').evaluate(x=>x.open),false);checks.push('Modal traps keyboard focus; Escape closes it.');
 await act('add-meal','[data-meal="Dinner"]');await act('route','[data-id="ai"]');await shot('ai-request');await act('generate');await shot('ai-review');
 const original=(await state()).draft.title;await act('refine');await act('lentils');assert.match((await state()).draft.title,/lentils/);await act('undo-refine');assert.equal((await state()).draft.title,original);
 await page.locator('#portion').fill('0.5');await page.locator('#portion').press('Tab');await act('save-add');let s=await state();assert.equal(s.added.length,1);assert.equal(s.added[0].portion,.5);assert(s.added[0].kcal>240&&s.added[0].kcal<250);checks.push('AI draft refinement/undo, half-portion calculation and atomic save/add preserve existing meals.');
 await act('scenario','[data-id="import"]');await act('extract');await shot('import-review');assert.equal((await state()).draft.yield,null);assert(await page.locator('[data-action="save"]').isEnabled());
 await act('ingredient','[data-index="0"]');await shot('ingredient-correction');await page.locator('#row-food').selectOption('chickpeas');await page.locator('#row-grams').fill('240');await act('apply-ingredient');
 const n=(await state()).draft.rows.length;await act('add-ingredient');await page.keyboard.press('Escape');assert.equal((await state()).draft.rows.length,n);
 await act('save');const saved=(await state()).selected;await act('add-saved');assert.match(await page.locator('#sheet-title').textContent(),/Yield needed/);await act('close-sheet');await act('back');
 await page.locator('#library-search').fill('chickpeas lemon');assert(await page.locator('#recipe-results .recipe-list-row').count()>=1);await page.locator('#library-search').fill('zxqnone');assert.match(await page.locator('#recipe-results').innerText(),/No matching recipes/);await act('clear-search');
 checks.push('Missing-yield import saves; ambiguous ingredient is correctable; cancel does not add an empty row; portion use stays gated; search/empty recovery work.');
 await act('scenario','[data-id="manual"]');await page.locator('#recipe-title').fill('Family tomato sauce');await page.locator('#ingredient-text').fill('Sauce:\n200 g tomatoes\nOlive oil, to taste');await act('toggle-text');assert.equal((await state()).draft.rows[0].heading,true);assert.equal((await state()).draft.rows[1].match,'tomato');assert.equal((await state()).draft.rows[2].match,null);await shot('manual-review');
 await act('back');await page.reload();await act('resume-draft');assert.equal(await page.locator('#recipe-title').inputValue(),'Family tomato sauce');await act('save');checks.push('Manual sections and unresolved lines survive row conversion, leaving and reloading; no yield is invented.');
 await act('back');await page.locator('#library-search').fill('WLO kitchen');await page.locator('#recipe-results [data-action="open"]').first().click();await act('edit-notes');await page.locator('#saved-notes').fill('Use extra lemon next time.');await act('save-notes');
 await act('cook');await page.locator('[data-cook]').first().check();await act('step','[data-index="1"]');await page.reload();assert.equal((await state()).session.checked.length,1);assert.equal((await state()).session.step,1);await shot('cooking');
 const oldTitle=(await state()).session.recipe.title;await act('leave-cook');await act('edit');await page.locator('#recipe-title').fill(oldTitle+' updated');await act('save');await page.locator('#actions [data-action="cook"]').click();assert.equal((await state()).session.recipe.title,oldTitle);assert.equal((await state()).session.checked.length,1);const count=(await state()).added.length;await act('finish');assert.equal((await state()).added.length,count);checks.push('Cooking marks and step survive reload and recipe edits; pinned revision stays stable; finishing does not log food.');
 await act('variation');assert.notEqual((await state()).draft.id,(await state()).selected);await act('save');checks.push('Variation saves with a distinct identity.');
 await page.evaluate(()=>window.recipeDemo.reset());await act('back');await act('add-meal','[data-meal="Breakfast"]');await act('route','[data-id="ai"]');await page.locator('#request-yield').fill('4');await act('generate');assert.equal((await state()).draft.rows[0].grams,480);await act('save-add');assert.match(await page.locator('.agenda-meal').first().innerText(),/Lemon chickpeas/);assert.match(await page.locator('.agenda-meal').first().innerText(),/Yogurt, oats/);assert.match(await page.locator('.recipe-macros').innerText(),/72 g/);checks.push('A four-serving AI fixture scales ingredients; adding to breakfast keeps existing food visible and updates day macros.');
 await page.evaluate(()=>window.recipeDemo.reset());await page.setViewportSize({width:360,height:800});await page.goto(url+'?view=phone');await shot('library-mobile');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await act('new');await act('route','[data-id="manual"]');await page.locator('#recipe-title').fill('A much longer family recipe name');await page.locator('#ingredient-text').fill('Dressing:\n30 g lemon juice\n10 g olive oil');await act('toggle-text');await shot('manual-mobile');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await act('back');await page.evaluate(()=>{document.querySelectorAll('.device input,.device button,.device strong,.device small,.device h2,.device h3,.device p,.device label').forEach(el=>el.style.fontSize=parseFloat(getComputedStyle(el).fontSize)*1.5+'px');});await shot('library-large-text');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);checks.push('360px mobile library/editor and text-enlarged library have no page-width overflow.');
 assert.deepEqual(errors,[]);assert.deepEqual(external,[]);checks.push('No JavaScript errors or external network requests across checked flows.');
 fs.writeFileSync(`${out}/verification.json`,JSON.stringify({checks,errors,external,limits:['HTML browser interaction test, not Android/TalkBack certification','AI/import use fixtures; no extraction quality claim','Large text check covers the library only; native IME and wake-lock reliability need Android validation']},null,2));
 console.log(JSON.stringify({passed:checks.length,checks},null,2));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
