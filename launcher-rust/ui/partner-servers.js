"use strict";
(() => {
  const servers = [
    { name: "FlashMC", address: "play.flashmc.cyou:22217", logo: "flashmc.png" },
    { name: "CubeCraft", address: "play.cubecraft.net", logo: "cube-craft.png" },
    { name: "Mineplex", address: "us.mineplex.com", logo: "mineplex.png" },
    { name: "The Hive", address: "play.hivemc.com", logo: "The-Hive.png" },
    { name: "BlockMC", address: "play.blockmc.com", logo: "blockMC.png" },
  ];
  const list = document.getElementById("partner-list");
  const refresh = document.getElementById("partner-refresh");
  refresh.disabled = !invoke;

  const rows = servers.map(({ name, address, logo }) => {
    const row = document.createElement("article");
    row.className = "partner-row";
    const identity = document.createElement("div");
    identity.className = "partner-identity";
    const icon = new Image(38, 38);
    icon.src = "assets/" + logo;
    icon.alt = "";
    const info = document.createElement("div");
    const title = document.createElement("strong");
    title.textContent = name;
    const host = document.createElement("small");
    host.className = "partner-address";
    host.textContent = address;
    const status = document.createElement("small");
    status.className = "partner-status";
    status.textContent = "Status unchecked";
    status.setAttribute("role", "status");
    info.append(title, host);
    identity.append(icon, info);

    const copy = document.createElement("button");
    copy.type = "button";
    copy.className = "mods-secondary";
    copy.textContent = "Copy address";
    copy.setAttribute("aria-label", "Copy " + name + " server address");
    copy.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(address);
        notify("Copied " + address);
      } catch {
        notify("Copy unavailable. You can select the address on the card.");
      }
    });

    const join = document.createElement("button");
    join.type = "button";
    join.className = "mods-primary";
    join.dataset.partnerJoin = "";
    join.disabled = true;
    join.textContent = "Join";
    join.setAttribute("aria-label", "Join " + name);
    join.addEventListener("click", () => window.BatLaunch(address));
    const actions = document.createElement("div");
    actions.className = "partner-actions";
    actions.append(join, copy);
    row.append(identity, status, actions);
    list.append(row);
    return { address, status };
  });

  refresh.addEventListener("click", async () => {
    refresh.disabled = true;
    refresh.textContent = "Checking…";
    try {
      await Promise.all(rows.map(async ({ address, status }) => {
        status.textContent = "Checking…";
        status.dataset.state = "checking";
        try {
          const result = await invoke("tools_action", { action: "ping", payload: { server: address } });
          status.dataset.state = "online";
          status.textContent = Number.isInteger(result.online)
            ? result.online + (Number.isInteger(result.max) ? " / " + result.max : "") + " players"
            : "Online";
        } catch {
          status.dataset.state = "unavailable";
          status.textContent = "Status unavailable";
        }
      }));
    } finally {
      refresh.disabled = false;
      refresh.textContent = "Refresh status";
    }
  });
})();
