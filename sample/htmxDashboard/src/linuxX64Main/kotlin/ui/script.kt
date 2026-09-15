package ui

val dashboardScript = """
(function() {
    htmx.config.historyCacheSize = 0;
    const main = document.getElementById('main-content');
    const alerts = document.getElementById('alerts');
    const theme = document.getElementById('theme-toggle');
    const requestStatus = document.getElementById('request-status');
    const modal = document.getElementById('confirm-dialog');
    const confirmInput = document.getElementById('confirm-input');
    const confirmSubmit = document.getElementById('confirm-submit');
    function updateThemeButton() {
        const dark = document.documentElement.dataset.theme !== 'light';
        const label = dark ? 'Switch to light theme' : 'Switch to dark theme';
        theme.setAttribute('aria-label',label); theme.setAttribute('title',label);
        theme.querySelector('[data-theme-sun]').hidden = !dark;
        theme.querySelector('[data-theme-moon]').hidden = dark;
    }
    theme.onclick = function() {
        const next = document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
        document.documentElement.dataset.theme = next;
        try { localStorage.setItem('dashboard-theme',next); } catch (_) {}
        updateThemeButton();
    };
    updateThemeButton();
    function notify(message, error) {
        const notice = document.createElement('div');
        notice.className = 'notice ' + (error ? 'notice-error' : 'notice-success');
        notice.setAttribute('role', error ? 'alert' : 'status');
        const text = document.createElement('span'); text.textContent = message;
        const close = document.createElement('button'); close.type = 'button'; close.className = 'btn btn-small'; close.dataset.dismiss = ''; close.textContent = '×'; close.setAttribute('aria-label','Dismiss notification');
        notice.append(text, close); alerts.append(notice);
    }
    document.addEventListener('click', function(event) {
        const copy = event.target.closest('[data-copy]');
        if (!copy) return;
        if (!navigator.clipboard) { notify('Clipboard unavailable. Select and copy the value manually.', true); return; }
        navigator.clipboard.writeText(copy.dataset.copy).then(() => notify('Copied.', false), () => notify('Could not copy. Select the value manually.', true));
    });
    const notices = {started:'Container started.',stopped:'Container stopped.',removed:'Resource removed.',pruned:'Cleanup completed.',created:'Volume created.'};
    const initialUrl = new URL(location.href);
    if (notices[initialUrl.searchParams.get('notice')]) {
        notify(notices[initialUrl.searchParams.get('notice')], false);
        initialUrl.searchParams.delete('notice'); history.replaceState(history.state, '', initialUrl);
    }
    function closeMenus(except, restoreFocus) {
        document.querySelectorAll('.action-menu[open]').forEach(menu => {
            if (menu === except) return;
            menu.open = false;
            if (restoreFocus) menu.querySelector('summary').focus();
        });
    }
    document.addEventListener('toggle', function(event) {
        const menu = event.target;
        if (!menu.matches?.('.action-menu') || !menu.open) return;
        closeMenus(menu, false);
        const trigger = menu.querySelector('summary').getBoundingClientRect();
        const content = menu.querySelector('.menu-content');
        const width = content.offsetWidth;
        const height = content.offsetHeight;
        content.style.left = Math.max(12, Math.min(trigger.right - width, document.documentElement.clientWidth - width - 12)) + 'px';
        const below = trigger.bottom + 6;
        content.style.top = Math.max(12, below + height <= innerHeight - 12 ? below : trigger.top - height - 6) + 'px';
    }, true);
    document.addEventListener('click', function(event) {
        closeMenus(event.target.closest('.action-menu'), false);
        if (event.target.closest('.menu-content a,.menu-content button')) closeMenus(null, false);
    });
    document.addEventListener('keydown', function(event) {
        if (event.key === 'Escape' && document.querySelector('.action-menu[open]')) {
            closeMenus(null, true); event.preventDefault();
        }
    });
    document.addEventListener('scroll', () => closeMenus(null, false), true);
    window.addEventListener('resize', () => closeMenus(null, false));
    let confirmRequest = null;
    document.addEventListener('htmx:confirm', function(event) {
        if (!event.detail.question) return;
        event.preventDefault();
        if (modal.open) return;
        confirmRequest = {detail:event.detail, element:event.detail.elt};
        document.getElementById('confirm-message').textContent = event.detail.question;
        document.getElementById('confirm-title').textContent = event.detail.elt.textContent.trim();
        const name = event.detail.elt.dataset.confirmName;
        document.getElementById('confirm-label').hidden = !name;
        confirmInput.value = ''; confirmInput.placeholder = name || '';
        confirmInput.oninput = function() { confirmSubmit.disabled = !!name && confirmInput.value !== name; };
        confirmInput.oninput(); modal.returnValue = ''; modal.showModal();
    });
    modal.addEventListener('close', function() {
        const pending = confirmRequest; confirmRequest = null;
        if (modal.returnValue === 'confirm' && !confirmSubmit.disabled && pending?.element.isConnected) pending.detail.issueRequest(true);
    });
    document.addEventListener('click', function(event) {
        const dismiss = event.target.closest('[data-dismiss]');
        if (dismiss) dismiss.closest('.notice').remove();
        const reset = event.target.closest('[data-reset-filters]');
        if (reset) {
            main.querySelector('[data-search]').value = '';
            const state = main.querySelector('[data-filter]'); if (state) state.value = '';
            filterRows(true);
        }
        const add = event.target.closest('[data-add-row]');
        if (add) {
            const group = add.closest('[data-repeat]');
            const row = group.querySelector('.repeat-row').cloneNode(true);
            row.querySelector('input').value = ''; group.querySelector('.repeat-fields').append(row); row.querySelector('input').focus();
        }
        const remove = event.target.closest('[data-remove-row]');
        if (remove) {
            const group = remove.closest('[data-repeat]');
            if (group.querySelectorAll('.repeat-row').length > 1) remove.closest('.repeat-row').remove();
            else group.querySelector('input').value = '';
        }
    });
    document.addEventListener('htmx:configRequest', function(event) {
        const form = event.detail.elt.matches('form[data-config-form]') ? event.detail.elt : null;
        if (!form) return;
        form.querySelectorAll('[data-repeat]').forEach(function(group) {
            event.detail.parameters[group.dataset.repeat] = Array.from(group.querySelectorAll('input')).map(el => el.value).filter(value => value.trim()).join('\n');
        });
    });
    let pending = 0;
    const listLocations = new Map();
    document.addEventListener('htmx:beforeSend', function(event) {
        pending++; requestStatus.textContent = 'Working…';
        const element = event.detail.elt;
        element.setAttribute('aria-busy','true');
        const buttons = element.matches('button') ? [element] : element.querySelectorAll('button[type=submit]');
        buttons.forEach(button => { if (!button.disabled) { button.dataset.requestDisabled = ''; button.disabled = true; } });
    });
    document.addEventListener('htmx:afterRequest', function(event) {
        pending = Math.max(0,pending-1); if (!pending) requestStatus.textContent = '';
        event.detail.elt.removeAttribute('aria-busy');
        const element = event.detail.elt;
        const buttons = element.matches('[data-request-disabled]') ? [element] : element.querySelectorAll('[data-request-disabled]');
        buttons.forEach(button => { button.disabled = false; delete button.dataset.requestDisabled; });
    });
    document.addEventListener('htmx:beforeSwap', function(event) {
        if (event.detail.xhr.status === 422) { event.detail.shouldSwap = true; event.detail.isError = false; }
    });
    document.addEventListener('htmx:responseError', function() { notify('The request failed. Your current view has been kept. Try again.',true); });
    document.addEventListener('htmx:sendError', function() { notify('Dashboard connection lost. Check the server and retry.',true); });
    function filterRows(save) {
        const input = main.querySelector('[data-search]'); if (!input) return;
        const state = main.querySelector('[data-filter]'); const sort = main.querySelector('[data-sort]');
        const query = input.value.trim().toLowerCase(); const selected = state?.value || '';
        const rows = Array.from(main.querySelectorAll('tr[data-search-text]'));
        rows.sort((a,b) => (a.dataset[sort.value] || '').localeCompare(b.dataset[sort.value] || '') || a.dataset.name.localeCompare(b.dataset.name));
        let count = 0;
        rows.forEach(row => { row.parentElement.append(row); row.hidden = !row.dataset.searchText.includes(query) || (!!selected && row.dataset.state !== selected); if (!row.hidden) count++; });
        main.querySelector('[data-no-results]').hidden = count > 0 || rows.length === 0;
        main.querySelector('[data-result-count]').textContent = count + ' of ' + rows.length + ' resources';
        if (save) {
            const url = new URL(location.href);
            for (const [key,value] of [['q',input.value],['state',selected],['sort',sort.value === 'name' ? '' : sort.value]]) {
                if (value) url.searchParams.set(key,value); else url.searchParams.delete(key);
            }
            history.replaceState(history.state,'',url);
            listLocations.set(location.pathname, url.pathname + url.search);
        }
    }
    function setup(focus) {
        const section = location.pathname.startsWith('/exec') ? '/containers' : '/' + location.pathname.split('/')[1];
        document.querySelectorAll('[data-section]').forEach(link => {
            if (link.dataset.section === section) link.setAttribute('aria-current','page'); else link.removeAttribute('aria-current');
        });
        main.querySelectorAll('a[href]').forEach(link => {
            const saved = listLocations.get(link.getAttribute('href'));
            if (saved && link.getAttribute('href') !== saved) {
                link.setAttribute('href',saved);
                if (link.hasAttribute('hx-get')) { link.setAttribute('hx-get',saved); htmx.process(link); }
            }
        });
        const heading = main.querySelector('h1');
        if (heading) { document.title = heading.textContent + ' · Docker dashboard'; if (focus) { heading.focus({preventScroll:true}); window.scrollTo({top:0,behavior:'instant'}); } }
        const input = main.querySelector('[data-search]');
        if (input) {
            listLocations.set(location.pathname,location.pathname+location.search);
            const params = new URL(location.href).searchParams; input.value = params.get('q') || '';
            const state = main.querySelector('[data-filter]'); if (state) state.value = params.get('state') || '';
            const sort = main.querySelector('[data-sort]'); sort.value = params.get('sort') === 'state' && state ? 'state' : 'name';
            input.oninput = () => filterRows(true); if (state) state.onchange = () => filterRows(true); sort.onchange = () => filterRows(true); filterRows(false);
        }
    }
    document.addEventListener('htmx:afterSettle', function(event) { if (event.detail.target === main) setup(true); });
    document.addEventListener('htmx:pushedIntoHistory', function() { setup(false); });
    document.addEventListener('htmx:historyRestore', function() { setup(true); });
    document.addEventListener('htmx:beforeHistorySave', function() { main.querySelectorAll('[data-stream],[data-terminal]').forEach(el => el.dispatchEvent(new Event('dashboard:dispose'))); });
    setup(false);
})();
""".trimIndent()
