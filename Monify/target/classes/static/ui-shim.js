/* ============================================================
   UI SHIM — substitui alert()/confirm() nativos.
   Dentro do iframe sandboxed do artifact publicado, os diálogos
   nativos window.alert/window.confirm/window.prompt ficam
   bloqueados pelo navegador (sandbox sem "allow-modals") e
   simplesmente não fazem nada — alert() não mostra nada e
   confirm() retorna false sem perguntar, fazendo qualquer ação
   protegida por "if (!confirm(...)) return;" falhar em silêncio.
   Este shim resolve isso com um toast e um modal próprios.
   ============================================================ */
(function () {
  'use strict';

  // ---------- toast (substitui alert) ----------
  let toastHost;
  function ensureToastHost() {
    if (toastHost) return toastHost;
    toastHost = document.createElement('div');
    toastHost.id = 'hbj-toast-host';
    toastHost.style.cssText = [
      'position:fixed', 'left:50%', 'bottom:24px', 'transform:translateX(-50%)',
      'z-index:9999', 'display:flex', 'flex-direction:column', 'gap:8px',
      'align-items:center', 'pointer-events:none', 'width:100%', 'max-width:420px', 'padding:0 16px'
    ].join(';');
    document.body.appendChild(toastHost);
    return toastHost;
  }

  window.alert = function (message) {
    try {
      const host = ensureToastHost();
      const el = document.createElement('div');
      el.textContent = String(message);
      el.style.cssText = [
        'pointer-events:auto', 'background:#1a1a1a', 'color:#fff',
        'border:1px solid rgba(184,134,59,0.5)', 'border-radius:10px',
        'padding:0.75rem 1rem', 'font-family:Sora,Segoe UI,sans-serif', 'font-size:0.88rem',
        'box-shadow:0 10px 30px rgba(0,0,0,0.35)', 'text-align:center', 'width:100%',
        'opacity:0', 'transition:opacity 0.2s ease'
      ].join(';');
      host.appendChild(el);
      requestAnimationFrame(() => { el.style.opacity = '1'; });
      setTimeout(() => {
        el.style.opacity = '0';
        setTimeout(() => el.remove(), 250);
      }, 4200);
    } catch (e) { /* no-op */ }
  };

  // ---------- confirm modal (substitui confirm) ----------
  window.customConfirm = function (message) {
    return new Promise((resolve) => {
      try {
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.style.zIndex = '9998';

        const box = document.createElement('div');
        box.className = 'modal-box';
        box.style.maxWidth = '380px';
        box.innerHTML =
          '<div class="modal-header"><h3>Confirmar</h3></div>' +
          '<p style="color:#333;font-size:0.92rem;line-height:1.5;margin-bottom:1.5rem;">' +
          String(message).replace(/[<>&]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' }[c])) +
          '</p>' +
          '<div class="modal-footer">' +
          '<button type="button" class="btn btn-outline" data-act="cancel">Cancelar</button>' +
          '<button type="button" class="btn btn-primary-dark" data-act="ok">Confirmar</button>' +
          '</div>';

        overlay.appendChild(box);
        document.body.appendChild(overlay);

        function finish(result) {
          overlay.remove();
          resolve(result);
        }
        overlay.addEventListener('click', (e) => { if (e.target === overlay) finish(false); });
        box.querySelector('[data-act="cancel"]').addEventListener('click', () => finish(false));
        box.querySelector('[data-act="ok"]').addEventListener('click', () => finish(true));
      } catch (e) {
        resolve(true); // se algo falhar ao montar o modal, não bloqueia a ação
      }
    });
  };
})();
