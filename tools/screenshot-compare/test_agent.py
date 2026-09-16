import tempfile
import unittest
from pathlib import Path
from PIL import Image
from agent import android_anchor, generate, placement, glyph_anchor

class ComparisonTest(unittest.TestCase):
    def test_scaled_anchor(self):
        self.assertEqual(placement((100,200),(200,400),{'x':10,'y':20},{'x':12,'y':24}),(.5,4,8))

    def test_unique_anchor_required(self):
        xml='<?xml version="1.0"?><hierarchy><node text="Weight" bounds="[10,20][30,40]"/></hierarchy>done'
        self.assertEqual(android_anchor(xml,'Weight'),{'x':10,'y':20,'width':20,'height':20})
        with self.assertRaises(ValueError): android_anchor(xml,'Missing')

    def test_glyph_ignores_leading_and_second_character(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'glyph.png'
            im=Image.new('RGB',(40,40),'black')
            for x in range(5,12):
                for y in range(9,25):im.putpixel((x,y),(240,240,240))
            for x in range(18,28):
                for y in range(2,30):im.putpixel((x,y),(240,240,240))
            im.save(p)
            self.assertEqual(glyph_anchor(p,{'x':0,'y':0,'width':40,'height':40}),
                             {'x':5,'y':9,'width':7,'height':16})

    def test_export_geometry_and_mask(self):
        with tempfile.TemporaryDirectory() as d:
            out=Path(d)
            ref=Image.new('RGB',(20,40),'black');ref.save(out/'ref.png')
            app=Image.new('RGB',(40,80),'black')
            for x in range(10,30):
                for y in range(10,30):app.putpixel((x,y),(255,255,255))
            app.save(out/'app.png')
            report=generate(out/'ref.png',out/'app.png',out)
            overlay=Image.open(out/'overlay.png')
            self.assertEqual(overlay.size,(20,40))
            self.assertEqual(overlay.getpixel((0,0)),(0,0,0,255))
            self.assertEqual(overlay.getpixel((8,8)),(255,37,37,255))
            with Image.open(out/'side-by-side.png') as side:
                self.assertEqual(side.size,(40,40))
            self.assertEqual(report['scale'],.5)

if __name__=='__main__':unittest.main()
