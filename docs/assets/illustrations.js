/* Progressive enhancement: all answers remain readable with JavaScript off. */
(function () {
  'use strict';
  var input = document.getElementById('faq-search');
  if (!input) return;
  var tools = document.getElementById('faq-tools');
  var empty = document.getElementById('faq-empty');
  var reset = document.getElementById('faq-reset');
  var countId = document.getElementById('faq-count-id');
  var countEn = document.getElementById('faq-count-en');
  var entries = Array.from(document.querySelectorAll('#faq-list .faq-item'));

  function normalize(value) {
    return value.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/\s+/g, ' ').trim();
  }
  // Index both languages once; hidden translations stay searchable as well.
  var corpus = entries.map(function (entry) { return normalize(entry.textContent); });
  function filter() {
    var terms = normalize(input.value).split(' ').filter(Boolean);
    var visible = 0;
    entries.forEach(function (entry, index) {
      var matches = terms.every(function (term) { return corpus[index].includes(term); });
      entry.hidden = !matches;
      if (matches) visible += 1;
    });
    empty.hidden = visible !== 0;
    countId.textContent = visible + ' dari ' + entries.length + ' jawaban';
    countEn.textContent = visible + ' of ' + entries.length + ' answers';
  }

  input.addEventListener('input', filter);
  reset.addEventListener('click', function () {
    input.value = '';
    filter();
    input.focus();
  });
  tools.hidden = false;
  filter();
})();
