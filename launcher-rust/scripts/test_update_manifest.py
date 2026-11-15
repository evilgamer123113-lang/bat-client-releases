import tempfile
import unittest
from pathlib import Path
import importlib.util

spec = importlib.util.spec_from_file_location('manifest', Path(__file__).with_name('prepare-update-manifest.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ManifestTests(unittest.TestCase):
    def test_preserves_every_electron_field_and_defaults_disabled(self):
        original = {'version':'2.2.97','download':'DO NOT CHANGE','sha256':'legacy-hash','platforms':{'darwin':{'x64':{'website':'legacy'}}},'other':{'opaque':True}}
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'Rust Setup.exe'
            path.write_bytes(b'fixture')
            Path(str(path)+'.sig').write_text('fixture-signature',encoding='utf-8')
            result=module.prepare(original,'0.1.0',[('windows-x86_64',str(path))])
            self.assertEqual({k:v for k,v in result.items() if k!='rust'},original)
            self.assertFalse(result['rust']['enabled'])
            entry=result['rust']['platforms']['windows-x86_64']
            self.assertEqual(entry['size'],7)
            self.assertEqual(len(entry['sha256']),64)
            self.assertIn('Rust%20Setup.exe',entry['url'])
    def test_refuses_unsigned_and_unknown_targets(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'package.exe';path.write_bytes(b'fixture')
            with self.assertRaises(FileNotFoundError): module.prepare({},'0.1.0',[('windows-x86_64',str(path))])
            with self.assertRaises(ValueError): module.prepare({},'0.1.0',[('unknown',str(path))])
            with self.assertRaises(ValueError): module.prepare({},'latest',[])

if __name__=='__main__': unittest.main()
