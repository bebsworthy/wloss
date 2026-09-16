const {test}=require('node:test');const assert=require('node:assert/strict');const {alignment,foreground,automaticMode}=require('./compare.js');
test('anchor translation includes uniform candidate scale',()=>{assert.deepEqual(alignment({x:100,y:200},{x:30,y:80},2),{x:40,y:40})});
test('dark foreground masks background, feathers faint text, retains bright text and alpha',()=>{const p=foreground(new Uint8ClampedArray([20,20,20,255,90,90,90,255,240,240,240,128]),'dark',45,90,[255,0,0]);assert.deepEqual([...p],[255,0,0,0,255,0,0,128,255,0,0,128])});
test('light foreground masks white and keeps dark ink',()=>{const p=foreground(new Uint8ClampedArray([255,255,255,255,0,0,0,255]),'light',45,90,[255,0,0]);assert.equal(p[3],0);assert.equal(p[7],255)});
test('none preserves source transparency',()=>assert.equal(foreground(new Uint8ClampedArray([0,0,0,83]),'none',45,90,[255,0,0])[3],83));
test('automatic mode handles either background',()=>{assert.equal(automaticMode(new Uint8ClampedArray(64).fill(255)),'light');const d=new Uint8ClampedArray(64);for(let i=3;i<64;i+=4)d[i]=255;assert.equal(automaticMode(d),'dark')});
