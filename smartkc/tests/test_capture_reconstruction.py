import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

path=Path(__file__).resolve().parents[1]/"reconstruct_capture.py"
spec=importlib.util.spec_from_file_location("reconstruct_capture",path)
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class CaptureValidationTest(unittest.TestCase):
    def profile(self):
        return dict(schema=module.PROFILE_SCHEMA,attachment="original-smartkc",geometry_confirmed=True,
                    image_width=3000,image_height=4000,sensor_width_mm=4.8,sensor_height_mm=6.4,
                    focal_length_mm=4.755,capture_zoom=2.0,working_distance_mm=75,camera_gap_mm=4)
    def test_complete_measured_profile(self):
        self.assertEqual(module.validate_profile(self.profile(),3000,4000)["working_distance_mm"],75)
    def test_draft_cannot_generate_maps(self):
        p=self.profile();p["geometry_confirmed"]=False
        with self.assertRaisesRegex(ValueError,"draft"):module.validate_profile(p,3000,4000)
    def test_cropped_resolution_rejected_even_if_profile_matches(self):
        p=self.profile();p["image_height"]=2582
        with self.assertRaisesRegex(ValueError,"untouched"):module.validate_profile(p,3000,2582)
    def test_resolution_change_requires_matching_profile(self):
        with self.assertRaisesRegex(ValueError,"resolution"):module.validate_profile(self.profile(),6000,8000)
    def test_sensor_orientation_mismatch(self):
        p=self.profile();p["sensor_width_mm"],p["sensor_height_mm"]=6.4,4.8
        with self.assertRaisesRegex(ValueError,"sensor"):module.validate_profile(p,3000,4000)
    def test_invalid_values(self):
        for value in (None,float("nan"),float("inf"),True,-1,0,999):
            with self.subTest(value=value):
                p=self.profile();p["working_distance_mm"]=value
                with self.assertRaises(ValueError):module.validate_profile(p,3000,4000)
    def test_unknown_attachment(self):
        p=self.profile();p["attachment"]="modified"
        with self.assertRaises(ValueError):module.validate_profile(p,3000,4000)
    def test_finite_metrics_required(self):
        with self.assertRaises(ValueError):module.validate_metrics([41,40,90,40.5,1,float("nan")])
    def capture(self,path,request):
        with zipfile.ZipFile(path,"w") as z:
            z.writestr("request.json",json.dumps(request));z.writestr("calibration.json",json.dumps(self.profile()));z.writestr("source_image",b"image")
    def test_capture_checksum_and_uncropped_attestation(self):
        request=dict(schema=module.SCHEMA,source_sha256=hashlib.sha256(b"image").hexdigest(),source_uncropped=True,eye="left")
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as temp:
            path=Path(temp)/"capture.zip";self.capture(path,request)
            self.assertEqual(module.read_capture(path)[2],b"image")
            request["source_sha256"]="wrong";self.capture(path,request)
            with self.assertRaisesRegex(ValueError,"checksum"):module.read_capture(path)
    def test_cropped_capture_rejected(self):
        request=dict(schema=module.SCHEMA,source_sha256=hashlib.sha256(b"image").hexdigest(),source_uncropped=False,eye="left")
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as temp:
            path=Path(temp)/"capture.zip";self.capture(path,request)
            with self.assertRaisesRegex(ValueError,"untouched"):module.read_capture(path)
    def test_simulation_original_attestation_override_keeps_checksum_check(self):
        request=dict(schema=module.SCHEMA,source_sha256=hashlib.sha256(b"image").hexdigest(),source_uncropped=False,eye="left")
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as temp:
            path=Path(temp)/"capture.zip";self.capture(path,request)
            self.assertFalse(module.read_capture(path,require_original=False)[0]["source_uncropped"])
            request["source_sha256"]="wrong";self.capture(path,request)
            with self.assertRaisesRegex(ValueError,"checksum"):module.read_capture(path,require_original=False)

if __name__=="__main__":unittest.main()
