// Replaces Scaladoc's scripts/theme.js in build/api (scripts/theme-api.mjs copies it there). Scaladoc loads that file
// early in <head>, so the colour mode is applied before the first paint, as Scaladoc's own version did.
(function () {
  // Colour mode, shared with the rest of the site: Docusaurus keeps it in localStorage "theme" ("light" or "dark",
  // absent for the system setting), and its navbar toggle cycles system -> light -> dark -> system.
  var KEY = 'theme';
  var LABELS = { system: 'system mode', light: 'light mode', dark: 'dark mode' };
  var systemDark = window.matchMedia('(prefers-color-scheme: dark)');

  function readMode() {
    try {
      var stored = localStorage.getItem(KEY);
      return stored === 'light' || stored === 'dark' ? stored : 'system';
    } catch (e) {
      return 'system';
    }
  }

  function writeMode(mode) {
    try {
      if (mode === 'system') localStorage.removeItem(KEY);
      else localStorage.setItem(KEY, mode);
    } catch (e) {}
  }

  var mode = readMode();

  function apply() {
    var dark = mode === 'system' ? systemDark.matches : mode === 'dark';
    var root = document.documentElement;
    // Scaladoc's stylesheets switch on the theme-dark class; data-theme mirrors what Docusaurus sets.
    root.classList.toggle('theme-dark', dark);
    root.setAttribute('data-theme', dark ? 'dark' : 'light');
    root.setAttribute('data-theme-choice', mode);
    var toggles = document.querySelectorAll('[data-color-mode-toggle]');
    for (var i = 0; i < toggles.length; i++) {
      toggles[i].title = LABELS[mode];
      toggles[i].setAttribute('aria-label', 'Switch between dark and light mode (currently ' + LABELS[mode] + ')');
    }
  }

  function next(m) {
    return m === 'system' ? 'light' : m === 'light' ? 'dark' : 'system';
  }

  apply();
  systemDark.addEventListener('change', function () {
    if (mode === 'system') apply();
  });
  // Another tab (a docs page, say) changed the mode.
  window.addEventListener('storage', function (e) {
    if (e.key === KEY) {
      mode = readMode();
      apply();
    }
  });

  window.addEventListener('DOMContentLoaded', function () {
    apply();
    var toggles = document.querySelectorAll('[data-color-mode-toggle], #mobile-theme-toggle');
    for (var i = 0; i < toggles.length; i++) {
      toggles[i].addEventListener('click', function () {
        mode = next(mode);
        writeMode(mode);
        apply();
      });
    }
  });

  document.addEventListener(
    'click',
    function (e) {
      var target = e.target instanceof Element ? e.target : null;
      // Scaladoc loads every same-origin link in place, swapping in the fetched page's #main. The navbar's site links
      // (/overview, /#catalog) are not Scaladoc pages, so stop the click before it reaches Scaladoc's handler on the
      // link itself (this listener runs first, in the capture phase) and let the browser load them normally.
      if (target && target.closest('#header a, .zb-mobile-links a')) e.stopPropagation();

      // The "Onboard your agent" button, as on the rest of the site (src/components/OnboardAgentButton): copy the
      // prompt and show a check for two seconds, or offer it in a dialog when the clipboard is unavailable.
      var button = target && target.closest('[data-onboard-prompt]');
      if (!button) return;
      var prompt = button.getAttribute('data-onboard-prompt');
      var copied = function () {
        button.classList.add('zb-onboard--copied');
        clearTimeout(button._zbTimer);
        button._zbTimer = setTimeout(function () {
          button.classList.remove('zb-onboard--copied');
        }, 2000);
      };
      var legacy = function () {
        var ok = false;
        try {
          var ta = document.createElement('textarea');
          ta.value = prompt;
          ta.style.position = 'fixed';
          ta.style.opacity = '0';
          document.body.appendChild(ta);
          ta.focus();
          ta.select();
          ok = document.execCommand('copy');
          document.body.removeChild(ta);
        } catch (err) {}
        if (ok) copied();
        else window.prompt('Copy this prompt for your coding agent:', prompt);
      };
      if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(prompt).then(copied, legacy);
      else legacy();
    },
    true,
  );
})();
