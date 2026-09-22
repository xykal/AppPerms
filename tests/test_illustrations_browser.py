"""Chromium smoke checks. Serve local fixtures only; never download an APK.

python -m playwright install --with-deps chromium
python -m unittest discover -s tests -p 'test_illustrations*.py' -v
"""
from functools import partial
from http.server import ThreadingHTTPServer
from pathlib import Path
from threading import Thread
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
        # Ephemeral, local-only test fixture; not a deployed server.
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
        self.page.on('pageerror', lambda error: self.errors.append(str(error)))
        # Block third-party requests: no production writes, APK downloads or API usage.
        self.context.route('**/*', lambda route: route.continue_() if route.request.url.startswith(self.url + '/') else route.abort())

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
            self.page.screenshot(path=str(path / f'{name}.png'))

    def test_responsive_layout_and_hero_webp(self):
        for width in [320, 390, 768, 1024, 1440]:
            with self.subTest(width=width):
                self.page.set_viewport_size({'width': width, 'height': 900})
                self.load()
                self.assertLessEqual(self.page.evaluate('document.documentElement.scrollWidth'), width)
                image = self.page.locator('.hero-art img')
                self.assertTrue(image.evaluate('(img) => img.complete && img.naturalWidth > 0'))
                self.assertIn('.webp', image.evaluate('(img) => img.currentSrc'))
                self.assertEqual(self.page.locator('.paper-illustration').count(), 8)
                if width in [390, 1440]:
                    self.screenshot(f'web-{width}')

    def test_visible_art_loads_without_broken_paths(self):
        self.load()
        for picture in self.page.locator('.paper-illustration').all():
            if not picture.is_visible():
                continue
            picture.scroll_into_view_if_needed()
            picture.locator('img').evaluate('(img) => img.decode()')
            self.assertGreater(picture.locator('img').evaluate('(img) => img.naturalWidth'), 0)

    def test_faq_search_is_case_and_space_insensitive(self):
        self.load()
        self.page.locator('#faq-search').fill('  WIRELESS   Debugging ')
        expect(self.page.locator('.faq-item:visible')).to_have_count(1)
        expect(self.page.locator('#faq-count-id')).to_have_text('1 dari 6 jawaban')
        expect(self.page.locator('#faq-empty')).to_be_hidden()

    def test_faq_empty_reset_and_keyboard_focus(self):
        self.load()
        search = self.page.locator('#faq-search')
        search.fill('no-match-xyverse-12345')
        expect(self.page.locator('.faq-item:visible')).to_have_count(0)
        expect(self.page.locator('#faq-empty')).to_be_visible()
        self.page.locator('#faq-empty img').evaluate('(img) => img.decode()')
        self.screenshot('faq-no-results')
        self.page.locator('#faq-reset').click()
        expect(self.page.locator('.faq-item:visible')).to_have_count(6)
        expect(self.page.locator('#faq-empty')).to_be_hidden()
        expect(search).to_be_focused()
        expect(search).to_have_value('')

    def test_native_faq_keyboard_and_language_persistence(self):
        self.load()
        first = self.page.locator('.faq-item').first
        first.locator('summary').focus()
        self.page.keyboard.press('Enter')
        expect(first).to_have_attribute('open', '')
        self.page.locator('#langToggle').click()
        expect(self.page.locator('html')).to_have_attribute('lang', 'en')
        expect(self.page.locator('#faq-title .lang-en')).to_be_visible()
        expect(self.page.locator('#faq-title .lang-id')).to_be_hidden()
        self.page.reload(wait_until='networkidle')
        expect(self.page.locator('html')).to_have_attribute('lang', 'en')
        self.page.locator('#faq').scroll_into_view_if_needed()
        self.screenshot('faq-english')

    def test_existing_version_tabs_and_download_routes(self):
        self.load()
        self.page.locator('.version-tab-btn[data-target="v130"]').click()
        expect(self.page.locator('#pane-v130')).to_be_visible()
        expect(self.page.locator('#pane-v171')).to_be_hidden()
        expect(self.page.locator('#btnRelease')).to_have_attribute('href', 'https://dl.appsperms.xyverse.my.id/AppsPerms-latest.apk')
        expect(self.page.locator('#btnDebug')).to_have_attribute('href', 'https://dl.appsperms.xyverse.my.id/AppsPerms-debug-latest.apk')
        self.assertTrue((DOCS / 'thanks.html').is_file())

    def test_scan_art_does_not_replace_live_scan_data(self):
        fixture = {'version': 'test-only', 'updated_utc': '2026-09-22T00:00:00Z',
                   'release': {'sha256': 'a' * 64},
                   'virustotal': {'checked': True, 'detected': 3, 'total': 62,
                                 'permalink': 'https://www.virustotal.com/gui/file/' + 'a' * 64}}
        self.page.route('**/data/virustotal.json', lambda route: route.fulfill(json=fixture))
        self.load()
        expect(self.page.locator('#vtScore')).to_have_text('3 / 62')
        expect(self.page.locator('#vtVerdict')).to_contain_text('3 vendor')
        expect(self.page.locator('#hashRelease')).to_contain_text('a' * 64)
        expect(self.page.locator('#vtLink')).to_have_attribute('href', fixture['virustotal']['permalink'])

    def test_png_fallback_is_loadable(self):
        self.load()
        self.page.locator('.hero-art source').evaluate('(source) => source.remove()')
        self.page.locator('.hero-art img').evaluate('(img) => img.decode()')
        self.assertTrue(self.page.locator('.hero-art img').evaluate('(img) => img.currentSrc.endsWith("download.png")'))

    def test_faq_works_without_javascript(self):
        context = self.browser.new_context(java_script_enabled=False, locale='id-ID')
        try:
            page = context.new_page()
            page.goto(self.url + '/', wait_until='networkidle')
            expect(page.locator('#faq-tools')).to_be_hidden()
            expect(page.locator('.faq-item:visible')).to_have_count(6)
            page.locator('.faq-item summary').first.click()
            expect(page.locator('.faq-answer').first).to_be_visible()
        finally:
            context.close()

    def test_custom_404_at_nested_url_and_language_toggle(self):
        self.page.set_viewport_size({'width': 390, 'height': 844})
        response = self.load('/missing/nested/page')
        self.assertEqual(response.status, 404)
        expect(self.page.locator('.error-art')).to_be_visible()
        self.page.locator('.error-art img').evaluate('(img) => img.decode()')
        self.assertLessEqual(self.page.evaluate('document.documentElement.scrollWidth'), 390)
        self.page.locator('#error-lang').click()
        expect(self.page.locator('html')).to_have_attribute('lang', 'en')
        expect(self.page.locator('.error-links a').first).to_have_attribute('href', '/')
        self.screenshot('web-404')

    def test_mobile_navigation_reaches_faq(self):
        self.page.set_viewport_size({'width': 390, 'height': 844})
        self.load()
        self.page.locator('#navToggle').click()
        self.page.locator('#navMenu a[href="#faq"]').click()
        expect(self.page.locator('#navToggle')).to_have_attribute('aria-expanded', 'false')
        self.assertTrue(self.page.url.endswith('#faq'))


if __name__ == '__main__':
    unittest.main()
