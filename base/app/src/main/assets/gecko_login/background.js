let sent = null;

function report(value) {
  if (!value || value.length < 20 || value === sent) return;
  sent = value;
  browser.runtime.sendNativeMessage("belutify", { sp_dc: value }).catch(() => {
    sent = null;
  });
}

function check() {
  browser.cookies.getAll({ name: "sp_dc", domain: "spotify.com" })
    .then((list) => {
      if (list && list.length > 0 && list[0].value) {
        report(list[0].value);
      }
    })
    .catch(() => {});
}

browser.cookies.onChanged.addListener((info) => {
  if (info && !info.removed && info.cookie && info.cookie.name === "sp_dc") {
    const dom = info.cookie.domain || "";
    if (dom === "spotify.com" || dom.endsWith(".spotify.com")) {
      report(info.cookie.value);
    }
  }
});

setInterval(check, 800);
check();
