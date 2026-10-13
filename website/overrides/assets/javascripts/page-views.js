(function () {
  "use strict";

  var ENDPOINT = "https://page-counter-api.shay-ff.workers.dev/views";

  function render(el, text) {
    el.textContent = text;
  }

  function init() {
    var el = document.getElementById("page-views");
    if (!el) return;

    var page = window.location.pathname;

    fetch(ENDPOINT + "?page=" + encodeURIComponent(page))
      .then(function (response) {
        if (!response.ok) {
          throw new Error("Counter request failed: " + response.status);
        }
        return response.json();
      })
      .then(function (data) {
        var views = Number(data && data.views);
        if (!Number.isFinite(views)) {
          throw new Error("Unexpected counter payload");
        }
        render(el, views.toLocaleString() + " views");
      })
      .catch(function () {
        render(el, "Views unavailable");
      });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
