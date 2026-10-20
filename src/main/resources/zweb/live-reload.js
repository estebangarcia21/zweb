(() => {
  const instance = document.currentScript.dataset.zwebVersion;
  let previous;

  async function refreshStyles(version) {
    const styles = [...document.querySelectorAll('link[rel="stylesheet"]')].filter(
      (link) => new URL(link.href).origin === window.location.origin,
    );

    await Promise.all(styles.map((link) =>
      new Promise((resolve, reject) => {
        const next = link.cloneNode();
        const url = new URL(link.href);
        url.searchParams.set("_zweb", version);
        next.href = url.href;
        const timeout = window.setTimeout(() => {
          next.remove();
          reject(new Error("Stylesheet refresh timed out"));
        }, 5000);

        next.onload = () => {
          window.clearTimeout(timeout);
          link.remove();
          resolve();
        };
        next.onerror = () => {
          window.clearTimeout(timeout);
          next.remove();
          reject(new Error("Stylesheet refresh failed"));
        };

        link.after(next);
      })
    ));
  }

  async function poll() {
    try {
      const response = await fetch("/_zweb/live-reload", {
        cache: "no-store",
        credentials: "same-origin",
        signal: AbortSignal.timeout(5000),
      });

      if (response.ok) {
        const version = (await response.text()).trim().split(":");

        // Ignore authentication/error documents returned by application middleware.
        if (version.length === 4 && version.every((part) => /^[a-f0-9-]+$/.test(part))) {
          const [server, code, css, assets] = version;

          if (
            server !== instance || (previous && (code !== previous[1] || assets !== previous[3]))
          ) {
            window.location.reload();
            return;
          }

          if (previous && css !== previous[2]) {
            await refreshStyles(css);
          }

          previous = version;
        }
      }
    } catch (_) {
      // Keep the document usable during compilation, restarts, and transient asset failures.
    }

    window.setTimeout(poll, 1000);
  }

  poll();
})();
