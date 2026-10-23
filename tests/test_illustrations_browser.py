"""Chromium smoke checks. Local fixtures only; tests never trigger an APK download."""
from functools import partial
from http.server import ThreadingHTTPServer
from pathlib import Path
from threading import Thread
import json
import os
import unittest

from playwright.sync_api import sync_playwright, expect
from tools.serve_docs import DocsHandler

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'


class PagesHandler(DocsHandler):
    def log_message(self, *args):
        pass


class IllustrationBrowser(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), partial(PagesHandler, directory=str(DOCS)))
        cls.thread = Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = f'http://127.0.0.1:{cls.server.server_port}'
        cls.pw = sync_playwright().start()
        cls.browser = cls.pw.chromium.launch()

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.pw.stop()
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=5)

    def setUp(self):
        self.context = self.browser.new_context(viewport={'width': 1280, 'height': 900}, locale='id-ID')
        self.page = self.context.new_page()
        self.errors = []
        self.external_requests = []
        self.page.on('pageerror', lambda error: self.errors.append(str(error)))
        self.page.on('request', lambda request: self.external_requests.append(request.url)
                     if not request.url.startswith(self.url + '/') else None)
        self.context.route('**/*', lambda route: route.continue_()
                           if route.request.url.startswith(self.url + '/') else route.abort())

    def tearDown(self):
        self.context.close()
        self.assertEqual(self.errors, [], 'Browser JavaScript exceptions')

    def load(self, path='/'):
        return self.page.goto(self.url + path, wait_until='networkidle')

    def screenshot(self, name):
        directory = os.environ.get('ILLUSTRATION_SCREENSHOTS')
        if directory:
            path = Path(directory)
            path.mkdir(parents=True, exist_ok=True)
            self.page.screenshot(path=str(path / f'{name}.png'), full_page=True)

    def test_responsive_layout_and_hero_webp(self):
        for width in [320, 390, 768, 1024, 1440]:
            with self.subTest(width=width):
                self.page.set_viewport_size({'width': width, 'height': 900})
                self.load()
                self.assertLessEqual(self.page.evaluate('document.documentElement.scrollWidth'), width)
                image = self.page.locator('.hero-illustration img')
                self.assertTrue(image.evaluate('(img) => img.complete && img.naturalWidth > 0'))
                self.assertIn('.webp', image.evaluate('(img) => img.currentSrc'))
                self.assertEqual(self.page.locator('.paper-illustration').count(), 9)
                self.assertEqual(
                    self.page.locator('.paper-illustration').evaluate_all(
                        '(items) => new Set(items.map((item) => item.dataset.illustration)).size'),
                    8)
                if width in [390, 1440]:
                    self.screenshot(f'web-{width}')

    def test_visible_art_loads_without_broken_paths(self):
        self.load()
        for picture in self.page.locator('.paper-illustration').all():
            if picture.is_visible():
                picture.scroll_into_view_if_needed()
                picture.locator('img').evaluate('(img) => img.decode()')
                self.assertGreater(picture.locator('img').evaluate('(img) => img.naturalWidth'), 0)

    def test_faq_search_reset_keyboard_and_language_persistence(self):
        self.load()
        search = self.page.locator('#faq-search')
        search.fill('  WIRELESS   Debugging ')
        expect(self.page.locator('.faq-item:visible')).to_have_count(1)
        expect(self.page.locator('#faq-count-id')).to_have_text('1 dari 6 jawaban')
        search.fill('no-match-xyverse-12345')
        expect(self.page.locator('.faq-item:visible')).to_have_count(0)
        expect(self.page.locator('#faq-empty')).to_be_visible()
        self.page.locator('#faq-reset').click()
        expect(self.page.locator('.faq-item:visible')).to_have_count(6)
        expect(search).to_be_focused()

        first = self.page.locator('.faq-item').first
        first.locator('summary').focus()
        self.page.keyboard.press('Enter')
        expect(first).to_have_attribute('open', '')
        self.page.locator('#langToggle').click()
        expect(self.page.locator('html')).to_have_attribute('lang', 'en')
        expect(self.page.locator('#faq-title .lang-en')).to_be_visible()
        self.page.reload(wait_until='networkidle')
        expect(self.page.locator('html')).to_have_attribute('lang', 'en')

    def test_version_tabs_and_immutable_download_routes(self):
        self.load()
        tab = self.page.locator('.version-tab-btn[data-target="v173"]')
        tab.click()
        expect(tab).to_have_attribute('aria-selected', 'true')
        expect(self.page.locator('#pane-v173')).to_be_visible()
        expect(self.page.locator('#pane-v180')).to_be_hidden()
        expect(self.page.locator('#btnRelease')).to_have_attribute(
            'href', 'https://github.com/xykal/AppPerms/releases/download/v1.8.0/AppsPerms-1.8.0-stable-universal-release.apk')
        expect(self.page.locator('#btnMirror')).to_have_attribute(
            'href', 'https://github.com/xykal/AppPerms/releases/tag/v1.8.0')
        self.assertEqual(self.page.locator('#btnDebug').count(), 0)

    def test_completed_scan_is_shown_only_for_matching_release_hash(self):
        fixture = {
            'schema_version': 1,
            'version': '9.9.9',
            'channel': 'stable',
            'artifacts': {'release': {
                'public': True,
                'package_id': 'app.appsperms',
                'signing_certificate_sha256':
                    '76ffdc65e3f8c6aef0d3ff8b75099ede336381305cfb2f7dda8d29d831ccac05',
                'filename': 'AppsPerms-9.9.9-stable-universal-release.apk',
                'sha256': 'a' * 64,
                'bytes': 10,
                'urls': [
                    'https://github.com/xykal/AppPerms/releases/download/v9.9.9/'
                    'AppsPerms-9.9.9-stable-universal-release.apk'
                ]}},
            'virustotal': {'status': 'complete', 'checked': True, 'detected': 3,
                           'total': 62, 'sha256': 'a' * 64,
                           'permalink': 'https://www.virustotal.com/gui/file/' + 'a' * 64},
        }
        self.page.route('**/data/release.json', lambda route: route.fulfill(json=fixture))
        self.load()
        expect(self.page.locator('#vtScore')).to_have_text('3 / 62')
        expect(self.page.locator('#vtVerdict')).to_contain_text('3')
        expect(self.page.locator('#hashRelease')).to_contain_text('a' * 64)
        expect(self.page.locator('#vtLink')).to_have_attribute('href', fixture['virustotal']['permalink'])

    def test_untrusted_manifest_url_cannot_replace_static_download_fallback(self):
        fixture = json.loads((DOCS / 'data/release.json').read_text(encoding='utf-8'))
        fixture['artifacts']['release']['urls'] = [
            'https://attacker.invalid/AppsPerms-1.8.0-stable-universal-release.apk'
        ]
        self.page.route('**/data/release.json', lambda route: route.fulfill(json=fixture))
        self.load()
        expect(self.page.locator('#btnRelease')).to_have_attribute(
            'href',
            'https://github.com/xykal/AppPerms/releases/download/v1.8.0/'
            'AppsPerms-1.8.0-stable-universal-release.apk',
        )
        expect(self.page.locator('#vtVerdict')).to_contain_text('Status tidak tersedia')

    def test_incomplete_or_mismatched_scan_never_says_clean(self):
        self.load()
        expect(self.page.locator('#vtScore')).to_have_text('—')
        expect(self.page.locator('#vtVerdict')).to_contain_text('Belum dipindai')
        body = self.page.locator('body').inner_text().lower()
        self.assertNotIn('100% clean', body)
        self.assertNotIn('0/67 clean', body)

    def test_png_fallback_is_loadable(self):
        self.load()
        self.page.locator('.hero-illustration source').evaluate('(source) => source.remove()')
        self.page.locator('.hero-illustration img').evaluate('(img) => img.decode()')
        self.assertTrue(self.page.locator('.hero-illustration img').evaluate(
            '(img) => img.currentSrc.endsWith("download.png")'))

    def test_faq_and_release_fallback_work_without_javascript(self):
        context = self.browser.new_context(java_script_enabled=False, locale='id-ID')
        try:
            page = context.new_page()
            page.goto(self.url + '/', wait_until='networkidle')
            expect(page.locator('#faq-tools')).to_be_hidden()
            expect(page.locator('.faq-item:visible')).to_have_count(6)
            page.locator('.faq-item summary').first.click()
            expect(page.locator('.faq-answer').first).to_be_visible()
            expect(page.locator('#btnRelease')).to_have_attribute(
                'href', 'https://github.com/xykal/AppPerms/releases/download/v1.8.0/AppsPerms-1.8.0-stable-universal-release.apk')
        finally:
            context.close()

    def test_thanks_page_uses_manifest_without_auto_download_probe(self):
        self.load('/thanks.html?var=release')
        expect(self.page.locator('#varTag')).to_have_text('Release')
        expect(self.page.locator('#fileText')).to_have_text('AppsPerms-1.8.0-stable-universal-release.apk')
        expect(self.page.locator('#hashText')).to_have_text(
            'c7e83326a75b405bbb698be172e583333a5f149adb2906882153a2caf88beb80')
        expect(self.page.locator('#btnManualDl')).to_have_attribute(
            'href', 'https://github.com/xykal/AppPerms/releases/download/v1.8.0/AppsPerms-1.8.0-stable-universal-release.apk')
        expect(self.page.locator('#vtThanksPill')).to_have_text('Not scanned')
        self.assertFalse(any(url.endswith('.apk') for url in self.external_requests))

        self.load('/thanks.html?var=debug')
        expect(self.page.locator('#varTag')).to_have_text('Debug unavailable')
        expect(self.page.locator('#fileText')).to_contain_text('CI-only')
        expect(self.page.locator('#btnManualDl')).to_be_hidden()
        expect(self.page.locator('#vtThanksPill')).to_have_text('Not published')

    def test_custom_404_at_nested_url_and_language_toggle(self):
        self.page.set_viewport_size({'width': 390, 'height': 844})
        response = self.load('/missing/nested/page')
        self.assertEqual(response.status, 404)
        expect(self.page.locator('.error-art')).to_be_visible()
        self.assertLessEqual(self.page.evaluate('document.documentElement.scrollWidth'), 390)
        self.page.locator('#error-lang').click()
        expect(self.page.locator('html')).to_have_attribute('lang', 'en')
        self.screenshot('web-404')

    def test_mobile_navigation_reaches_faq(self):
        self.page.set_viewport_size({'width': 390, 'height': 844})
        self.load()
        expect(self.page.locator('#navToggle')).to_have_attribute('aria-expanded', 'false')
        self.page.locator('#navToggle').click()
        expect(self.page.locator('#navToggle')).to_have_attribute('aria-expanded', 'true')
        self.page.locator('#navMenu a[href="#faq"]').click()
        expect(self.page.locator('#navToggle')).to_have_attribute('aria-expanded', 'false')
        self.assertTrue(self.page.url.endswith('#faq'))

    def test_no_tracking_requests_are_made(self):
        self.load()
        self.assertEqual(self.external_requests, [])


if __name__ == '__main__':
    unittest.main()
