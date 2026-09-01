/**
 * CARTelemetry car card — top-view vehicle with sensor overlays and
 * control tiles. Config:
 *
 * type: custom:cartelemetry-car-card
 * car: my_car            # car_name slug as used in entity ids
 * title: My Car          # optional
 * tiles:                 # optional, defaults below
 *   - entity: switch.my_car_ac_state
 *     name: A/C
 *     icon: mdi:air-conditioner
 *
 * Overlays resolve entities by trying sensor./binary_sensor./lock./switch.
 * prefixes with the configured car slug. Unknown keys are skipped.
 */
// Embedded assets (base64): top-view fallback + vehicle-type icons.
const CAR_TOP_DEFAULT = "data:image/png;base64,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UKzDwzjYfj/azct/N7P3n/yKiFharMa7/3DPCQATr3vwF6lUrUZl5bkIAUBBrKYUqt1qRbvZjNbeXqFf99HeXjwq9CvCdCnH0fVVGvcLYWZISQBISAAAQEICAAASEgAAkJAAAICEBAAAJCQAACAhAQAACQkAAEhIAABAQgIAABISAACQkAAAgIQEAAAkJAAAICEBAAAJCQAASEgAAEBCAgAAEhIAAJCQAACAhAQAACQkAAAgIQEAAAkJAABISAAAQEICAAASEgAAkJAAAICEBAAAJCQAACAhAQAACQkAAEhIAABAQgIAABISAACQkAAAgIQEAAAkJAAAICEBAAAJCQAASEgAAEBCAgAAEhIAAJCQAACAhAQAACQkAAAgIQEAAAkJAABISAAAQEICAAASEgAAkJAAAICEBAAAJCQAACAhAQAACQkAAEhIAABAQgIAABISAACQkAAAgIQEAAAkJAAAICEBAAAJCQAASEgAAEBCAgAAEhIAAJCQAACAhAQAACQkAAAgIQEAAAkJAABISAAAQEICAAASEgAAkJAAAICEBAAAJCQAACAhAQAACQkAAEhIAABAQgIAABISAACQkAAAgIQEAAAkJAAAICEBAAAJCQAASEgAAEBCAgAAEhIAAJCQAACAhAQAACQkAAAgIQEAAAkJAABISAAAQEICAAASEgAAkJAAAICEBAAAJCQAACAhAQAACQkAAEhIAABAQgIAABISAACQkAAAgIQEAAAkJAAAICEBAAAJCQAASEgAAEBCAgAAEhIAAJCQAACAhAQAACQkAAAgIQEAAAkJAABISAAAQEICAAASEgAAkJAAAICEBAAAJCQAACAhAQAACQkAAEhIAABAQgIAABISAACQkAAAgIQEAAAkJAAAICEBAAAJCQAASEgAAEBCAgAAEhIAAJCQAACAhAQAACQkAAAgIQEAAAkJAABISAAAQEICAAASEgAAkJAAAICEBAAAJCQAACAhAQAACQkAAEhIAABAQgIAABISAACQkAAAgIQEAAAkJAAAICEBAAAJCQAASEgAAEBCAgAAEhIAAJCQAACAhAQAACQkAAAgIQEAAAkJAABISAAAQEICAAASEgAAkJAAAICEBAAAJCQAACAhAQAACQkAAEhIAABAQgIAABISAACQkAAAgIQEAAAkJAAAICEBAAAJCQAASEgAAEBCAgAAEhIA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";
const TYPE_ICONS = {"car":"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0iY3VycmVudENvbG9yIiBkPSJNMyAxNWMwLTEgLjItMiAuOS0zbDEuNy0yLjZDNi40IDguMiA3LjIgNy41IDguNSA3LjVoN2MxLjMgMCAyLjEuNyAyLjkgMS45TDIwLjEgMTJjLjcgMSAuOSAyIC45IDN2MS41YzAgLjYtLjQgMS0xIDFoLS44YTIuMiAyLjIgMCAwIDEtNC4zIDBoLTUuOGEyLjIgMi4yIDAgMCAxLTQuMyAwSDRjLS42IDAtMS0uNC0xLTF6TTUgMTRoMTR2LjNjMCAuMy0uMi43LS41LjdINS41Yy0uMyAwLS41LS40LS41LS43ek03LjUgOWExIDEgMCAwIDAtLjkuNkw1LjQgMTJoMTMuMmwtMS4yLTIuNGExIDEgMCAwIDAtLjktLjZ6Ii8+PC9zdmc+Cg==","hatchback":"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0iY3VycmVudENvbG9yIiBkPSJNMiAxNWMwLTEgLjMtMiAxLTNsMi0yLjVDNiA4LjIgNyA3LjUgOC41IDcuNWg2YzEuNSAwIDIuNi43IDMuNiAybDEuOCAyLjVjLjcgMSAxLjEgMiAxLjEgM3YxLjVjMCAuNi0uNCAxLTEgMWgtMWEyLjIgMi4yIDAgMCAxLTQuNCAwSDguNGEyLjIgMi4yIDAgMCAxLTQuNCAwSDNjLS42IDAtMS0uNC0xLTF6TTQuNCAxMi41aDE1LjJMMTkgMTNINXpNNyA5YTEgMSAwIDAgMC0uOC40TDQuNyAxMWgxNC42bC0xLjUtMS42YTEgMSAwIDAgMC0uOC0uNHoiLz48L3N2Zz4K","moto":"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0iY3VycmVudENvbG9yIiBkPSJNNSAxNmEzIDMgMCAwIDEtMy0zYzAtMS41IDEtMi41IDIuNS0zbDEtNGg0bDEgMmgyLjVsMiAzLjVjMSAuNCAxLjggMSAyLjQgMS44bDEuNCAxLjdhMyAzIDAgMSAxLTEuNSAxLjNsLTEuMi0xLjVjLS41LS40LTEuMS0uOC0xLjctMWwtMSAxLjVhMyAzIDAgMSAxLTMtMS41bDEtMS41LTMtMi0xLjUgMi4yQTMgMyAwIDAgMSA1IDE2ek01IDE0YTEgMSAwIDEgMCAwLTIgMSAxIDAgMCAwIDAgMnptOSAwYTEgMSAwIDEgMCAwIDIgMSAxIDAgMCAwIDAtMnoiLz48L3N2Zz4K","pickup":"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0iY3VycmVudENvbG9yIiBkPSJNMiAxNWMwLS42LjQtMSAxLTFoMTNsMi00aDNjLjYgMCAxIC40IDEgMXYzYzAgLjYtLjQgMS0xIDFoLTFhMi4yIDIuMiAwIDAgMS00LjMgMGgtNC41YTIuMiAyLjIgMCAwIDEtNC4zIDBIM2MtLjYgMC0xLS40LTEtMXpNNSAxMmgxMHYtMUg0LjZjLS4yIDAtLjMuMS0uNC4zek0xNiAxMWwtMS40IDIuOEgyMHYtMmMwLS4zLS4yLS41LS41LS41eiIvPjwvc3ZnPgo=","suv":"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0iY3VycmVudENvbG9yIiBkPSJNMi41IDE2YzAtMS4yLjMtMi40IDEuMS0zLjRMNSAxMWwxLTRjLjMtMSAxLjItMS41IDIuMi0xLjVoNy42YzEgMCAxLjkuNSAyLjIgMS41bDEgNCAuOS44Yy45IDEgMS42IDIuMiAxLjYgMy41VjE2YzAgLjYtLjQgMS0xIDFoLS44YTIuMyAyLjMgMCAwIDEtNC41IDBIOS43YTIuMyAyLjMgMCAwIDEtNC41IDBIMy41Yy0uNiAwLTEtLjQtMS0xek03IDYuNWwtLjkgMy41aDExLjhsLS45LTMuNWMtLjEtLjMtLjMtLjUtLjctLjVINy43Yy0uNCAwLS42LjItLjcuNXpNNS41IDEyaDEzbDEuNSAxLjVjLjUuNS41IDEgLjUgMS41SDMuNWMwLS41IDAtMSAuNS0xLjV6Ii8+PC9zdmc+Cg==","truck":"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0iY3VycmVudENvbG9yIiBkPSJNMiAxNGMwLS42LjQtMSAxLTFoMTFWN2MwLS42LjQtMSAxLTFoNGMxLjUgMCAzIDEuNSAzIDN2NWMwIC42LS40IDEtMSAxaC0xYTIuMiAyLjIgMCAwIDEtNC4zIDBoLTMuNGEyLjIgMi4yIDAgMCAxLTQuMyAwSDNjLS42IDAtMS0uNC0xLTF6TTQgMTNoOVY4SDYuNWMtLjMgMC0uNS4yLS41LjV6TTE1IDl2NGg0Yy0uNC0xLTEuMy0yLTIuNS0yLjV6Ii8+PC9zdmc+Cg==","van":"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0iY3VycmVudENvbG9yIiBkPSJNMiAxNGMwLS42LjQtMSAxLTFoMVY4YzAtMSAuNS0xLjUgMS41LTEuNUgxNmw0LjUgNWMxIC45IDEuNSAyIDEuNSAzLjVWMTZjMCAuNi0uNCAxLTEgMWgtMWEyLjIgMi4yIDAgMCAxLTQuMyAwSDguM2EyLjIgMi4yIDAgMCAxLTQuMyAwSDNjLS42IDAtMS0uNC0xLTF6TTQgMTNoMTFWOEg2Yy0uNiAwLTEgLjQtMSAxek0xNSA4LjV2My41aDQuNWMtLjItLjUtLjYtMS0xLjItMS42eiIvPjwvc3ZnPgo="};

class CarTelemetryCard extends HTMLElement {
  static getStubConfig() {
    return { car: "byd_car" };
  }

  setConfig(config) {
    if (!config || !config.car) {
      throw new Error("cartelemetry-car-card: 'car' is required");
    }
    this._config = config;
    this._hass = this._hass || null;
    this.render();
  }

  set hass(hass) {
    this._hass = hass;
    if (this._content) this.update();
  }

  connectedCallback() {
    if (!this._content) this.render();
  }

  _find(key) {
    const hass = this._hass;
    if (!hass || !hass.states) return null;
    const car = this._config.car;
    for (const domain of ["sensor", "binary_sensor", "lock", "switch"]) {
      const eid = `${domain}.${car}_${key}`;
      if (hass.states[eid]) return hass.states[eid];
    }
    return null;
  }

  _state(key) {
    const st = this._find(key);
    return st ? String(st.state) : null;
  }

  _on(key, truthy) {
    const v = this._state(key);
    if (v === null) return null;
    return truthy.includes(v.toLowerCase());
  }

  _num(key, digits = 0) {
    const v = parseFloat(this._state(key));
    return isNaN(v) ? null : v.toFixed(digits);
  }

  defaultTiles() {
    const car = this._config.car;
    const t = (entity, name, icon) => ({ entity, name, icon });
    const out = [];
    const ac = `switch.${car}_ac_state`;
    if (this._hass && this._hass.states[ac]) out.push(t(ac, "Климат", "mdi:air-conditioner"));
    const lock = `lock.${car}_remote_lock_state`;
    if (this._hass && this._hass.states[lock]) out.push(t(lock, "Замок", "mdi:car-key"));
    const mirrors = `switch.${car}_mirror_heater`;
    if (this._hass && this._hass.states[mirrors]) out.push(t(mirrors, "Обогрев зеркал", "mdi:car-defrost-rear"));
    return out;
  }

  render() {
    this.innerHTML = "";
    const style = document.createElement("style");
    style.textContent = `
      ha-card { padding: 16px; }
      .car-wrap { position: relative; max-width: 320px; margin: 0 auto; }
      .car-wrap img { width: 100%; display: block; }
      .badge {
        position: absolute; transform: translate(-50%, -50%);
        background: rgba(30,30,30,.82); color: #fff; border-radius: 14px;
        padding: 2px 9px; font-size: 13px; white-space: nowrap;
        display: flex; align-items: center; gap: 4px; cursor: default;
      }
      .badge ha-icon { --mdc-icon-size: 15px; }
      .badge.warn { background: rgba(183,28,28,.9); }
      .badge.ok { background: rgba(27,94,32,.85); }
      .dot {
        position: absolute; width: 16px; height: 16px; border-radius: 50%;
        transform: translate(-50%, -50%); border: 2px solid rgba(255,255,255,.9);
      }
      .dot.open { background: #e53935; }
      .dot.closed { background: #2e7d32; opacity: .55; }
      .dot.unknown { background: #9e9e9e; opacity: .4; }
      .status { display: flex; justify-content: center; gap: 14px; margin-top: 8px;
                font-size: 14px; flex-wrap: wrap; }
      .status .item { display: flex; align-items: center; gap: 4px; }
      .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(90px, 1fr));
               gap: 8px; margin-top: 12px; }
      .tile {
        display: flex; flex-direction: column; align-items: center; gap: 2px;
        background: var(--secondary-background-color, #eee); border-radius: 10px;
        padding: 8px 4px; cursor: pointer; text-align: center; font-size: 12px;
      }
      .tile ha-icon { --mdc-icon-size: 22px; }
      .tile.on { background: var(--primary-color); color: var(--text-primary-color, #fff); }
      .title { text-align: center; font-size: 16px; font-weight: 600; margin-bottom: 6px; }
      .type-chip {
        position: absolute; left: 4px; top: 4px; width: 30px; height: 30px;
        border-radius: 50%; background: rgba(30,30,30,.82);
        display: flex; align-items: center; justify-content: center;
      }
      .type-chip img { width: 20px; height: 20px; filter: invert(1); }
    `;
    this.appendChild(style);

    const card = document.createElement("ha-card");
    const wrap = document.createElement("div");
    wrap.className = "car-wrap";
    if (this._config.title) {
      const t = document.createElement("div");
      t.className = "title";
      t.textContent = this._config.title;
      card.appendChild(t);
    }

    const img = document.createElement("img");
    img.alt = "car";
    // Top-view rig: try a state variant, then the base SVG in
    // /local/community/cartelemetry-card/, finally the embedded base64.
    const candidates = this.carImageCandidates();
    let idx = 0;
    img.addEventListener("error", () => {
      idx++;
      if (idx < candidates.length) img.src = candidates[idx];
    });
    img.src = candidates[0] || CAR_TOP_DEFAULT;
    wrap.appendChild(img);

    // Vehicle-type icon chip (base64, from the embedded set).
    const chip = document.createElement("div");
    chip.className = "type-chip";
    const tic = document.createElement("img");
    tic.src = this.vehicleTypeIcon();
    tic.alt = this.vehicleType();
    chip.appendChild(tic);
    wrap.appendChild(chip);

    // --- overlays (positions are % of the car image) ---
    const badges = [
      { key: "engine_coolant_temp", x: 50, y: 12, icon: "mdi:thermometer",
        text: () => { const v = this._num("engine_coolant_temp"); return v === null ? null : `${v}°`; } },
      { key: "outside_temp", x: 50, y: 88, icon: "mdi:thermometer",
        text: () => { const v = this._num("outside_temp"); return v === null ? null : `${v}°`; } },
      { key: "soc", x: 50, y: 50, icon: "mdi:battery",
        text: () => { const v = this._num("soc"); return v === null ? null : `${v}%`; } },
    ];
    for (const b of badges) {
      const txt = b.text();
      if (txt === null) continue;
      const el = document.createElement("div");
      el.className = "badge";
      el.style.left = b.x + "%";
      el.style.top = b.y + "%";
      const ic = document.createElement("ha-icon");
      ic.icon = b.icon;
      el.appendChild(ic);
      el.appendChild(document.createTextNode(txt));
      wrap.appendChild(el);
    }

    // doors/windows/locks dots: [key, x%, y%, label]
    const dots = [
      ["driver_door", 6, 38], ["passenger_door", 94, 38],
      ["rear_left_door", 6, 66], ["rear_right_door", 94, 66],
      ["bonnet", 50, 5], ["trunk", 50, 95],
      ["window_fl", 12, 26], ["window_fr", 88, 26],
      ["window_rl", 12, 58], ["window_rr", 88, 58],
    ];
    const openWords = ["on", "open", "открыт", "открыта", "открыто"];
    for (const [key, x, y] of dots) {
      const st = this._find(key);
      if (!st) continue;
      const isOn = this._on(key, openWords);
      const el = document.createElement("div");
      el.className = "dot " + (isOn ? "open" : "closed");
      el.style.left = x + "%";
      el.style.top = y + "%";
      el.title = `${st.attributes.friendly_name || key}: ${st.state}`;
      wrap.appendChild(el);
    }

    card.appendChild(wrap);

    // --- status line ---
    const status = document.createElement("div");
    status.className = "status";
    const speed = this._num("speed");
    const moving = speed !== null && parseFloat(speed) > 3;
    const addStatus = (icon, text) => {
      const it = document.createElement("div");
      it.className = "item";
      const ic = document.createElement("ha-icon");
      ic.icon = icon;
      it.appendChild(ic);
      it.appendChild(document.createTextNode(text));
      status.appendChild(it);
    };
    addStatus(moving ? "mdi:car-side" : "mdi:parking",
              moving ? "Едет" : "Стоит");
    const soc = this._num("soc");
    if (soc !== null) addStatus("mdi:battery", `${soc}%`);
    const range = this._num("range");
    if (range !== null) addStatus("mdi:map-marker-distance", `${range} км`);
    card.appendChild(status);

    // --- control tiles ---
    const tiles = (this._config.tiles && this._config.tiles.length)
      ? this._config.tiles : this.defaultTiles();
    if (tiles.length) {
      const grid = document.createElement("div");
      grid.className = "tiles";
      for (const t of tiles) {
        const st = this._hass && this._hass.states[t.entity];
        if (!st) continue;
        const isSwitchish = t.entity.startsWith("switch.") || t.entity.startsWith("lock.");
        const on = isSwitchish && ["on", "unlocked"].includes(String(st.state).toLowerCase());
        const el = document.createElement("div");
        el.className = "tile" + (on ? " on" : "");
        const ic = document.createElement("ha-icon");
        ic.icon = t.icon || "mdi:toggle-switch";
        el.appendChild(ic);
        el.appendChild(document.createTextNode(t.name || t.entity));
        el.addEventListener("click", () => this._toggle(t.entity, st));
        grid.appendChild(el);
      }
      card.appendChild(grid);
    }

    this.appendChild(card);
    this._content = card;
  }

  vehicleType() {
    return (this._config.vehicle_type || "car").toLowerCase();
  }

  vehicleTypeIcon() {
    return TYPE_ICONS[this.vehicleType()] || TYPE_ICONS.car;
  }

  /** Top-view image candidates: state variant → base SVG → embedded base64 fallback. */
  carImageCandidates() {
    const base = "/local/community/cartelemetry-card/";
    const list = [];
    const st = this.detectTopState();
    if (st) list.push(`${base}car_top_${st}.svg`);
    list.push(`${base}car_top.svg`);
    return list;
  }

  /** Combine open doors/windows/sunroof/boot/bonnet into a variant name. */
  detectTopState() {
    const openWords = ["on", "open", "открыт", "открыта", "открыто", "unlocked"];
    const open = [];
    if (this._on("driver_door", openWords) || this._on("passenger_door", openWords) ||
        this._on("rear_left_door", openWords) || this._on("rear_right_door", openWords)) {
      open.push("doors_open");
    }
    if (this._on("window_fl", openWords) || this._on("window_fr", openWords) ||
        this._on("window_rl", openWords) || this._on("window_rr", openWords)) {
      open.push("windows_open");
    }
    if (this._on("sunroof", openWords)) open.push("sunroof_open");
    if (this._on("trunk", openWords)) open.push("boot_open");
    if (this._on("bonnet", openWords)) open.push("bonnet_open");
    if (open.length > 1) return "all_open";
    return open[0] || "";
  }

  _toggle(entityId, stateObj) {
    if (!this._hass) return;
    const domain = entityId.split(".")[0];
    const service = domain === "lock"
      ? (["locked", "locking"].includes(String(stateObj.state)) ? "unlock" : "lock")
      : (String(stateObj.state) === "on" ? "turn_off" : "turn_on");
    this._hass.callService(domain, service, { entity_id: entityId });
  }

  update() { this.render(); }

  getCardSize() { return 4; }

  static async getConfigElement() { return null; }
}

customElements.define("cartelemetry-car-card", CarTelemetryCard);

window.customCards = window.customCards || [];
window.customCards.push({
  type: "cartelemetry-car-card",
  name: "CARTelemetry Car Card",
  description: "Top-view car with sensor overlays and control tiles",
});
