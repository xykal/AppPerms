"""Asset/resource regression checks. This is not an Android compilation test."""
from pathlib import Path
from html.parser import HTMLParser
import hashlib
import json
import re
import unittest
import xml.etree.ElementTree as ET

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
RES = ROOT / 'app/src/main/res'
ANDROID_NS = '{http://schemas.android.com/apk/res/android}'
NAMES = {'download', 'security-report', 'faq', 'versions', 'permissions', 'update', 'search', 'empty-404'}


class Markup(HTMLParser):
    def __init__(self, path):
        super().__init__()
        self.tags = []
        self.feed(path.read_text(encoding='utf-8'))

    def handle_starttag(self, tag, attrs):
        self.tags.append((tag, dict(attrs)))


class IllustrationAssets(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.items = json.loads((DOCS / 'images/illustrations/manifest.json').read_text())['items']

    def test_all_eight_transparent_masters_are_present(self):
        self.assertEqual({i['name'] for i in self.items}, NAMES)
        self.assertEqual(len(list((ROOT / 'design/illustrations').glob('*.png'))), 8)

    def test_manifest_sizes_hashes_dimensions_and_alpha(self):
        for item in self.items:
            entries = [item['source'], item['fallback'], *item['web']]
            if 'android' in item:
                entries.append(item['android'])
            for entry in entries:
                with self.subTest(path=entry['path']):
                    path = ROOT / entry['path']
                    data = path.read_bytes()
                    self.assertEqual(len(data), entry['bytes'])
                    self.assertEqual(hashlib.sha256(data).hexdigest(), entry['sha256'])
                    with Image.open(path) as image:
                        self.assertEqual(image.size, (entry['width'], entry['height']))
                        self.assertEqual(image.mode, 'RGBA')
                        alpha = image.getchannel('A')
                        self.assertEqual(alpha.getextrema(), (0, 255))
                        self.assertGreater(alpha.histogram()[0], image.width * image.height // 4)
                        self.assertIsNone(alpha.crop((0, 0, image.width, 4)).getbbox())
                        self.assertIsNone(alpha.crop((0, image.height - 4, image.width, image.height)).getbbox())

    def test_webp_and_android_size_budgets(self):
        webp = [entry for item in self.items for entry in item['web']]
        native = [item['android'] for item in self.items if 'android' in item]
        self.assertLess(sum(e['bytes'] for e in webp), 1400 * 1024)
        self.assertLess(sum(e['bytes'] for e in native), 128 * 1024)
        self.assertEqual(len(native), 4)
        for item in self.items:
            self.assertEqual([e['width'] for e in item['web']], [320, 640, 960])
            self.assertEqual(item['fallback']['width'], 640)
        for entry in native:
            self.assertEqual(entry['width'], 384)
            self.assertIn('drawable-nodpi/', entry['path'])

    def test_every_illustration_is_used_on_the_website(self):
        markup = Markup(DOCS / 'index.html')
        used = {attrs['data-illustration'] for _, attrs in markup.tags if 'data-illustration' in attrs}
        self.assertEqual(used, NAMES)

    def test_picture_sources_fallback_dimensions_and_loading(self):
        for page in ['index.html', '404.html']:
            markup = Markup(DOCS / page)
            images = [a for tag, a in markup.tags if tag == 'img' and 'images/illustrations/' in a.get('src', '')]
            self.assertGreater(len(images), 0)
            self.assertEqual(sum(i.get('fetchpriority') == 'high' for i in images), 1)
            for attrs in images:
                self.assertEqual((attrs['width'], attrs['height']), ('640', '640'))
                self.assertEqual(attrs.get('alt'), '')  # decorative; adjacent copy names the section
                self.assertEqual(attrs['decoding'], 'async')
                self.assertIn(attrs['loading'], ['eager', 'lazy'])
                self.assertTrue((DOCS / attrs['src'].lstrip('/')).is_file())
            for tag, attrs in markup.tags:
                if tag == 'source' and 'images/illustrations/' in attrs.get('srcset', ''):
                    self.assertEqual(attrs['type'], 'image/webp')
                    self.assertIn('sizes', attrs)
                    for candidate in attrs['srcset'].split(','):
                        url, width = candidate.strip().split()
                        self.assertTrue((DOCS / url.lstrip('/')).is_file(), url)
                        self.assertIn(width, ['320w', '640w', '960w'])

    def test_unique_html_ids_and_local_enhancement_files(self):
        for page in ['index.html', '404.html']:
            markup = Markup(DOCS / page)
            ids = [a['id'] for _, a in markup.tags if 'id' in a]
            self.assertEqual(len(ids), len(set(ids)), page)
            for tag, attrs in markup.tags:
                path = attrs.get('src') if tag == 'script' else attrs.get('href') if tag == 'link' else None
                if path and 'assets/illustrations.' in path:
                    self.assertTrue((DOCS / path.lstrip('/')).is_file(), path)

    def test_all_android_xml_is_well_formed(self):
        for path in RES.rglob('*.xml'):
            with self.subTest(path=path.relative_to(ROOT)):
                ET.parse(path)

    def test_android_artwork_references_exist_and_are_used(self):
        actual = {p.stem for p in (RES / 'drawable-nodpi').glob('illus_*.webp')}
        text = '\n'.join(p.read_text() for p in RES.rglob('*.xml'))
        text += '\n'.join(p.read_text() for p in (ROOT / 'app/src/main/java').rglob('*.kt'))
        used = set(re.findall(r'(?:@drawable/|R\.drawable\.)(illus_\w+)', text))
        self.assertEqual(actual, used)
        self.assertEqual(actual, {'illus_faq', 'illus_versions', 'illus_permissions', 'illus_search'})

    def test_native_illustrations_are_decorative_and_not_tinted(self):
        found = 0
        for path in RES.glob('layout/*.xml'):
            for element in ET.parse(path).iter('ImageView'):
                if element.get(ANDROID_NS + 'src', '').startswith('@drawable/illus_'):
                    found += 1
                    self.assertEqual(element.get(ANDROID_NS + 'importantForAccessibility'), 'no')
                    self.assertEqual(element.get(ANDROID_NS + 'contentDescription'), '@null')
                    self.assertEqual(element.get(ANDROID_NS + 'scaleType'), 'fitCenter')
                    self.assertFalse(any(key.endswith('}tint') for key in element.attrib))
        self.assertEqual(found, 4)

    def test_android_state_images_and_localized_faq_link(self):
        code = (ROOT / 'app/src/main/java/app/appsperms/ui/MainActivity.kt').read_text()
        for state, art in [('empty_no_access', 'permissions'), ('empty_no_data', 'versions'),
                           ('empty_no_explicit', 'permissions'), ('empty_no_result', 'search')]:
            self.assertRegex(code, rf'R\.drawable\.illus_{art}\)\s+binding\.emptyTitle\.setText\(R\.string\.{state}\)')
        for locale in ['values', 'values-en']:
            strings = ET.parse(RES / locale / 'strings.xml')
            matches = [node for node in strings.getroot() if node.get('name') == 'about_faq']
            self.assertEqual(len(matches), 1)
            self.assertIn('browser', matches[0].text)
        code = (ROOT / 'app/src/main/java/app/appsperms/ui/AboutSheet.kt').read_text()
        self.assertIn('b.aboutFaq.setOnClickListener', code)
        self.assertIn('https://appsperms.xyverse.my.id/#faq', code)

    def test_no_internet_permission_or_new_image_runtime(self):
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
        names = {node.get(ANDROID_NS + 'name') for node in manifest.findall('uses-permission')}
        self.assertNotIn('android.permission.INTERNET', names)
        gradle = (ROOT / 'app/build.gradle.kts').read_text()
        self.assertNotRegex(gradle.lower(), r'coil|glide|fresco')

    def test_landscape_artwork_is_smaller(self):
        def dimension(folder):
            tree = ET.parse(RES / folder / 'dimens.xml')
            return int(tree.find("dimen[@name='illustration_empty_size']").text.removesuffix('dp'))
        self.assertGreater(dimension('values'), dimension('values-land'))
        self.assertGreater(dimension('values-land'), 0)


if __name__ == '__main__':
    unittest.main()
