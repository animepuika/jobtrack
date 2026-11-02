// Small enhancements from the design handoff. Every page works without this file:
// forms still submit and messages still show.
document.addEventListener('DOMContentLoaded', () => {
	// After a form submit, move focus to the message so screen readers announce it.
	const message = document.querySelector('.error-summary, .flash[tabindex="-1"]');
	if (message) {
		message.focus();
	}

	// Search: show that a search is running and ignore repeated clicks.
	const search = document.getElementById('search-form');
	if (search) {
		search.addEventListener('submit', (event) => {
			const button = search.querySelector('button[type="submit"]');
			if (!button) {
				return;
			}
			if (button.getAttribute('aria-disabled') === 'true') {
				event.preventDefault();
				return;
			}
			button.setAttribute('aria-disabled', 'true');
			button.lastChild.textContent = 'Searching';
		});
	}

	// Dashboard: apply the stage and "show archived" filters as soon as they change.
	const filters = document.querySelector('form.filters[role="search"]');
	if (filters) {
		filters.querySelectorAll('select, input[type="checkbox"]').forEach((field) => {
			field.addEventListener('change', () => filters.requestSubmit());
		});
	}
});
