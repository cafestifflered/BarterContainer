# 📦 BarterBarrels

A chest-based bartering system built for **MineHa.us** — players can convert containers into **shops**, browse a global **Catalogue**, and view a **Shop Owner Directory** with polished MiniMessage-powered GUIs.

---

## 🔧 Features
- 🪵 Convert containers into **barter shops** (admin tools)
- 🛒 **Catalogue**: browse/search all active shops (`/catalog`)
- 📇 **Shop Owner Directory**: owner heads + top sellers (`/directory`)
- 🧭 **Tracking**: guide players to shop locations (`/endtracking` to cancel)
- 📊 **Logs & Analytics** dashboards (per-shop & all-shops)
- 🎨 All player-facing text in **`messages.yml`** (MiniMessage)
- ⚙️ Structure-only options in **`config.yml`** (item types, ranges, formats)
- 🧩 Optional Bedrock/Floodgate head cache for correct GUI skins
- 🔄 Reload configuration/messages without restarting

---

## 📦 Requirements
- **Minecraft 1.20+**
- **Paper** (or a compatible fork, e.g., Purpur/Pufferfish)

---

## 🧩 Commands

| Command                      | Description                                      | Permission                 |
|-----------------------------|--------------------------------------------------|----------------------------|
| `/barterbarrels reload`     | Reload plugin configuration and messages         | `barterbarrels.reload`     |
| `/barterbarrels givelister` | Give the **Shop Lister** tool                    | `barterbarrels.givelister` |
| `/barterbarrels givefixer`  | Give the **Fixer Stick** tool                    | `barterbarrels.givefixer`  |
| `/catalog`                  | Open the global **Barter Catalogue** UI          | `barterchests.catalog`     |
| `/directory`                | Open the **Shop Owner Directory** UI             | `barterbarrels.directory`  |
| `/endtracking`              | Stop active shop tracking                        | *(none)*                   |

> 🔄 Alias for the admin root: **`/barterbarrel`**

---

## 🔐 Permissions

| Node                       | Description                                      | Default |
|---------------------------|--------------------------------------------------|---------|
| `barterbarrels.admin`     | Master admin (root command)                      | OP      |
| `barterbarrels.reload`    | Use `/barterbarrels reload`                      | OP      |
| `barterbarrels.givelister`| Use `/barterbarrels givelister`                  | OP      |
| `barterbarrels.givefixer` | Use `/barterbarrels givefixer`                   | OP      |
| `barterchests.catalog`    | Open the Catalogue (`/catalog`)                  | true    |
| `barterbarrels.directory` | Open the Directory (`/directory`)                | true    |
| `barterchests.shopping-list` | (Optional) Shopping list features              | true    |

> ⚠️ **OPs inherit all permissions.** Use a permissions plugin (e.g., LuckPerms) for fine-grained control.

---

## 🧰 Admin Tools
- **Shop Lister** — converts a vanilla container into a barter shop
- **Fixer Stick** — reattaches block location metadata to an existing shop
> Obtain via `/barterbarrels givelister` and `/barterbarrels givefixer`.

---

## 🧭 Catalogue & Directory
- **Catalogue** (`/catalog`) — browse/search all shops; choose a listing to view or track
- **Directory** (`/directory`) — one head per owner with shop counts and top sellers
- **Tracking** — follow in-world guidance; use `/endtracking` to stop

---

## 📁 Configuration

- **`config.yml`** — structure-only:
    - Item **types** (e.g., `shop-lister-item.type`, `catalog-search-button-item.type`)
    - Numeric **ranges/toggles** (e.g., `tracking-system.track-range`, `head-cache.*`)
    - **Time/date** format patterns (e.g., `transactions.time_pattern`)
- **`messages.yml`** — all player-facing strings with **MiniMessage** (GUI titles, lore, analytics lines, notifications, etc.)
    - Example title:
      ```yaml
      title: "<gradient:#8b5cf6:#3b82f6><italic><player>'s Barter Barrel</italic></gradient>"
      ```

> 📝 Keep **formatting** (colors/gradients/interactive tags) in `messages.yml`; keep **types/ranges/formats** in `config.yml`.

---

## 📜 License

BarterBarrels is **proprietary software**.  
Use of this plugin requires purchasing a valid license from an authorized source.

- You may run the plugin only on server(s) you own or operate.
- You may modify the source code for private use only (no redistribution).
- Redistribution, resale, or sublicensing is strictly prohibited.
- Support and updates are available only to licensed users.

See [LICENSE](LICENSE) for full terms.
