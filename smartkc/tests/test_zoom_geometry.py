import os
from pathlib import Path
import sys
import unittest
import numpy as np
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root))
cache=root/"artifacts"/"test-plot-cache";cache.mkdir(parents=True,exist_ok=True)
os.environ.setdefault("MPLCONFIGDIR",str(cache));os.environ.setdefault("MPLBACKEND","Agg")
from preprocess import undo_zoom,crop_around_center,project_marked_center

class ZoomGeometryTest(unittest.TestCase):
    def test_unity_zoom_preserves_pixels(self):
        source=np.arange(101*75*3,dtype=np.uint8).reshape((101,75,3))
        image,offset=undo_zoom(source,1)
        self.assertEqual(offset,(0,0));np.testing.assert_array_equal(source,image)
    def test_fractional_zoom_preserves_full_canvas_and_offset(self):
        source=np.full((101,75,3),100,dtype=np.uint8)
        image,(x,y)=undo_zoom(source,1.5)
        self.assertEqual(image.shape,source.shape)
        self.assertEqual((x,y),(12,17))
        self.assertEqual(int(image[17,12,0]),100);self.assertEqual(int(image[16,12,0]),0)
    def test_legacy_even_dimensions_unchanged(self):
        image,offset=undo_zoom(np.ones((400,300,3),dtype=np.uint8),2)
        self.assertEqual(offset,(75,100));self.assertEqual(image.shape,(400,300,3))
    def test_invalid_zoom_rejected(self):
        for value in (0,0.5,float("nan"),float("inf"),11):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):undo_zoom(np.zeros((10,10,3),dtype=np.uint8),value)
    def test_normalized_center_does_not_drop_an_exact_pixel(self):
        self.assertEqual(project_marked_center([0.512,0.5065],3000,4000,2,(750,1000)),[1518,2013])
    def test_marked_center_tracks_crop_offset(self):
        image=np.zeros((400,300,3),dtype=np.uint8)
        crop,center=crop_around_center(image,crop_dims=(100,100),center=(180,220),marked_center=[190,230])
        self.assertEqual(crop.shape,(100,100,3));self.assertEqual(center,[60,60])

if __name__=="__main__":unittest.main()
