// Enhances every [data-copy] button. Buttons are hidden by CSS until the `js` class is set.
// Results are also announced through the single [data-copy-status] live region.
const status = document.querySelector('[data-copy-status]');
let clearStatus;

function announce(message) {
  if (!status) return;
  status.textContent = message;
  clearTimeout(clearStatus);
  clearStatus = setTimeout(() => { status.textContent = ''; }, 1500);
}

for (const button of document.querySelectorAll('[data-copy]')) {
  const label = button.textContent;
  const what = (button.getAttribute('aria-label') ?? label).replace(/^Copy\s+/i, '');
  button.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(button.dataset.copy);
      button.textContent = 'Copied';
      announce(`Copied ${what}`);
    } catch {
      button.textContent = 'Copy failed';
      announce('Copy failed');
    }
    setTimeout(() => { button.textContent = label; }, 1500);
  });
}
