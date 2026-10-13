/*
 * Status page: renders the report of /statistics/summary and, for administrators, /actuator/health.
 *
 * The page may be shown inside the application shell, where every page shares one document and
 * this script stays loaded. status.html therefore mounts it on every visit (StatusPage.mount), and
 * unmounting the page stops what the visit started (refresh timer, listeners, charts); the refresh
 * loop also stops itself as soon as the page is no longer in the document.
 *
 * All values are inserted as text, never as HTML: paths, tags and user names are user controlled.
 * Values from the server are never used as property names either: lookups by name go through Maps.
 */
/* global Chart, Page */
(function () {
    "use strict";

    var INTERVALS = [10000, 30000, 60000, 300000];
    var DEFAULT_INTERVAL = 30000;
    var INTERVAL_STORAGE_KEY = "airsonic.status.refreshInterval";
    var USERS_SHOWN = 10;
    var HISTORY_DAYS = [7, 30, 90, 365];
    var DEFAULT_HISTORY_DAYS = 30;
    var HISTORY_STORAGE_KEY = "airsonic.status.historyDays";
    // the history changes slowly: fetch it with every fifth refresh or so, and when asked to
    var HISTORY_REFRESH_MILLIS = 300000;
    // the transfer types of the report: message key and CSS class name, and chart color
    var TRANSFER_TYPES = new Map([
        ["STREAM", {key: "stream", color: "rgb(230, 170, 30)"}],
        ["DOWNLOAD", {key: "download", color: "rgb(235, 80, 110)"}],
        ["UPLOAD", {key: "upload", color: "rgb(54, 140, 220)"}]
    ]);
    var UNKNOWN_TRANSFER_TYPE = {key: "unknown", color: "rgb(128, 128, 128)"};
    // the series of the transfer charts
    var TRANSFER_SERIES = [
        {label: "streamed", type: "STREAM", bytes: function (row) {
            return row.streamed;
        }},
        {label: "downloaded", type: "DOWNLOAD", bytes: function (row) {
            return row.downloaded;
        }},
        {label: "uploaded", type: "UPLOAD", bytes: function (row) {
            return row.uploaded;
        }}
    ];

    var state = null;

    // ---------------------------------------------------------------- helpers

    function el(tag, attributes, children) {
        var element = document.createElement(tag);
        Object.entries(attributes || {}).forEach(function ([name, value]) {
            if (value === null || value === undefined || value === false) {
                return;
            }
            if (name === "text") {
                element.textContent = value;
            } else if (name === "className") {
                element.className = value;
            } else if (name.indexOf("on") === 0) {
                element.addEventListener(name.substring(2), value);
            } else {
                element.setAttribute(name, value === true ? "" : value);
            }
        });
        (children || []).forEach(function (child) {
            if (child !== null && child !== undefined) {
                element.appendChild(typeof child === "string" ? document.createTextNode(child) : child);
            }
        });
        return element;
    }

    function svg(tag, attributes) {
        var element = document.createElementNS("http://www.w3.org/2000/svg", tag);
        Object.entries(attributes).forEach(function ([name, value]) {
            element.setAttribute(name, value);
        });
        return element;
    }

    function byId(id) {
        return document.getElementById(id);
    }

    function message(key) {
        var text = state.messages.has(key) ? state.messages.get(key) : key;
        var args = Array.prototype.slice.call(arguments, 1);
        return text.replace(/\{(\d+)\}/g, function (match, index) {
            var value = args.at(Number(index));
            return value !== undefined ? value : match;
        });
    }

    function transferType(type) {
        return TRANSFER_TYPES.get(type) || UNKNOWN_TRANSFER_TYPE;
    }

    function storageGet(key) {
        try {
            return window.localStorage.getItem(key);
        } catch (e) {
            return null;
        }
    }

    function storageSet(key, value) {
        try {
            window.localStorage.setItem(key, value);
        } catch (e) {
            // private mode or blocked storage: the choice is simply not remembered
        }
    }

    // ---------------------------------------------------------------- formatting

    function formatNumber(value, fractionDigits) {
        if (value === null || value === undefined) {
            return "–";
        }
        return new Intl.NumberFormat(state.config.locale, {
            maximumFractionDigits: fractionDigits || 0
        }).format(value);
    }

    function formatPercent(ratio) {
        if (ratio === null || ratio === undefined) {
            return "–";
        }
        return new Intl.NumberFormat(state.config.locale, {
            style: "percent", minimumFractionDigits: 1, maximumFractionDigits: 1
        }).format(ratio);
    }

    function formatUnit(value, unit, fractionDigits, unitDisplay) {
        try {
            return new Intl.NumberFormat(state.config.locale, {
                style: "unit", unit: unit, unitDisplay: unitDisplay || "short", maximumFractionDigits: fractionDigits || 0
            }).format(value);
        } catch (e) {
            return formatNumber(value, fractionDigits) + " " + unit;
        }
    }

    var BYTE_UNITS = ["byte", "kilobyte", "megabyte", "gigabyte", "terabyte", "petabyte"];

    /** Picks the binary unit for a byte count, as the rest of Airsonic does (1 KB = 1024 bytes). */
    function byteUnit(bytes) {
        var index = 0;
        while (index < BYTE_UNITS.length - 1 && Math.abs(bytes) >= Math.pow(1024, index + 1)) {
            index++;
        }
        return {unit: BYTE_UNITS.at(index), divisor: Math.pow(1024, index)};
    }

    function formatBytes(bytes) {
        if (bytes === null || bytes === undefined) {
            return "–";
        }
        var unit = byteUnit(bytes);
        return formatUnit(bytes / unit.divisor, unit.unit, unit.divisor === 1 ? 0 : 1);
    }

    function formatKbps(kbps) {
        if (kbps === null || kbps === undefined) {
            return "–";
        }
        return formatUnit(kbps, "kilobit-per-second", 0);
    }

    /** A duration as its two largest units, e.g. "3 days 4 hr". */
    function formatDuration(millis) {
        if (millis === null || millis === undefined) {
            return "–";
        }
        var seconds = Math.max(0, Math.round(millis / 1000));
        var parts = [
            ["day", Math.floor(seconds / 86400)],
            ["hour", Math.floor(seconds % 86400 / 3600)],
            ["minute", Math.floor(seconds % 3600 / 60)],
            ["second", seconds % 60]
        ];
        var first = parts.findIndex(function (part) {
            return part[1] > 0;
        });
        if (first === -1) {
            return formatUnit(0, "second");
        }
        return parts.slice(first, first + 2).filter(function (part) {
            return part[1] > 0;
        }).map(function (part) {
            return formatUnit(part[1], part[0]);
        }).join(" ");
    }

    /** A point in time relative to the report, e.g. "5 minutes ago" or "in 2 hours". */
    function formatRelative(time) {
        var diff = (time - state.now) / 1000;
        var abs = Math.abs(diff);
        var units = [["year", 31536000], ["month", 2592000], ["week", 604800], ["day", 86400],
            ["hour", 3600], ["minute", 60], ["second", 1]];
        var unit = units.find(function (u) {
            return abs >= u[1];
        }) || units.at(-1);
        return new Intl.RelativeTimeFormat(state.config.locale, {numeric: "auto"})
            .format(Math.round(diff / unit[1]), unit[0]);
    }

    /** A LocalDate from the server ("2026-10-12") as a local Date. */
    function parseDate(value) {
        var parts = Array.isArray(value) ? value : String(value).split("-").map(Number);
        return new Date(parts[0], parts[1] - 1, parts[2]);
    }

    function formatDate(value, options) {
        return new Intl.DateTimeFormat(state.config.locale, options || {dateStyle: "medium"}).format(parseDate(value));
    }

    function formatDateTime(time) {
        return new Intl.DateTimeFormat(state.config.locale, {dateStyle: "medium", timeStyle: "medium"})
            .format(new Date(time));
    }

    /** A cell showing a relative time, with the full date on hover. */
    function timeCell(time, className) {
        if (time === null || time === undefined) {
            return el("td", {className: className, text: message("never")});
        }
        if (time === -1) {
            return el("td", {className: className, text: message("incalculable")});
        }
        return el("td", {className: className}, [
            el("time", {datetime: new Date(time).toISOString(), title: formatDateTime(time), text: formatRelative(time)})
        ]);
    }

    // ---------------------------------------------------------------- theme

    /** The colors of the current theme, for the charts. */
    function themeColors() {
        var color = getComputedStyle(state.root).color;
        var match = color.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
        var rgb = match ? match.slice(1, 4).join(", ") : "128, 128, 128";
        return {text: "rgb(" + rgb + ")", grid: "rgba(" + rgb + ", 0.15)"};
    }

    // ---------------------------------------------------------------- data

    function fetchJson(url) {
        return fetch(url, {credentials: "same-origin", headers: {Accept: "application/json"}}).then(function (response) {
            // the health endpoint answers 503 with a regular body when a component is down
            if (!response.ok && response.status !== 503) {
                throw new Error(response.status + " " + response.statusText);
            }
            return response.json();
        });
    }

    /**
     * @param forceHistory also fetch the transfer history if it is not due yet
     */
    function refresh(forceHistory) {
        var current = state;
        clearTimeout(current.timer);
        current.stale = false;
        setUpdating(true);
        var summary = fetchJson(current.config.urls.summary);
        var health = current.config.admin
            ? fetchJson(current.config.urls.health).catch(function () {
                return null;
            })
            : Promise.resolve(null);
        var historyAge = Date.now() - current.historyFetched;
        var historyDue = forceHistory === true || !current.historyFetched || historyAge > HISTORY_REFRESH_MILLIS;
        // undefined: keep the history shown, it is fetched again next time
        var history = historyDue
            ? fetchJson(current.config.urls.history + "?days=" + current.historyDays).catch(function () {
                return undefined;
            })
            : Promise.resolve(undefined);
        return Promise.all([summary, health, history]).then(function (results) {
            if (current !== state) {
                return;
            }
            current.report = results[0];
            current.health = results[1] || null;
            if (results[2] !== undefined) {
                current.history = results[2];
                current.historyFetched = Date.now();
            }
            current.now = current.report.generated;
            render();
            setUpdated();
        }).catch(function (error) {
            if (current === state) {
                showError(message("updatefailed", error.message));
            }
        }).finally(function () {
            if (current === state) {
                setUpdating(false);
                schedule();
            }
        });
    }

    function schedule() {
        clearTimeout(state.timer);
        if (state.paused) {
            return;
        }
        var current = state;
        current.timer = setTimeout(function () {
            if (current !== state) {
                return;
            }
            if (!document.body.contains(current.root)) {
                // another page replaced this one
                stop();
            } else if (document.hidden) {
                // refresh when the tab is shown again
                current.stale = true;
            } else {
                refresh();
            }
        }, current.interval);
    }

    function onVisibilityChange() {
        if (!document.hidden && state && state.stale && document.body.contains(state.root)) {
            refresh();
        }
    }

    // ---------------------------------------------------------------- toolbar

    function setUpdating(updating) {
        state.root.setAttribute("aria-busy", updating ? "true" : "false");
        byId("statusRefresh").disabled = updating;
    }

    function setUpdated() {
        var updated = byId("statusUpdated");
        updated.classList.remove("status-error");
        updated.textContent = message("lastupdated", new Intl.DateTimeFormat(state.config.locale, {timeStyle: "medium"})
            .format(new Date())) + (state.paused ? " · " + message("paused") : "");
    }

    function showError(text) {
        var updated = byId("statusUpdated");
        updated.classList.add("status-error");
        updated.textContent = text;
    }

    function notify(text, isError) {
        var box = byId("statusMessage");
        box.textContent = text;
        box.classList.toggle("status-error", !!isError);
        box.hidden = false;
        clearTimeout(state.notifyTimer);
        state.notifyTimer = setTimeout(function () {
            box.hidden = true;
        }, 6000);
    }

    function initToolbar() {
        var select = byId("statusInterval");
        INTERVALS.forEach(function (interval) {
            select.appendChild(el("option", {value: String(interval), text: formatDuration(interval)}));
        });
        select.value = String(state.interval);
        select.addEventListener("change", function () {
            state.interval = Number(select.value);
            storageSet(INTERVAL_STORAGE_KEY, select.value);
            schedule();
        });
        byId("statusRefresh").addEventListener("click", function () {
            refresh(true);
        });
        var historyDays = byId("statusHistoryDays");
        HISTORY_DAYS.forEach(function (days) {
            historyDays.appendChild(el("option", {value: String(days), text: formatUnit(days, "day", 0, "long")}));
        });
        historyDays.value = String(state.historyDays);
        historyDays.addEventListener("change", function () {
            state.historyDays = Number(historyDays.value);
            storageSet(HISTORY_STORAGE_KEY, historyDays.value);
            refresh(true);
        });
        var pause = byId("statusPause");
        pause.addEventListener("click", function () {
            state.paused = !state.paused;
            pause.textContent = message(state.paused ? "resume" : "pause");
            pause.setAttribute("aria-pressed", state.paused ? "true" : "false");
            if (state.paused) {
                clearTimeout(state.timer);
            } else {
                refresh();
            }
            if (state.report) {
                setUpdated();
            }
        });
        state.root.querySelectorAll("[data-section]").forEach(function (button) {
            button.addEventListener("click", function () {
                var section = byId(button.dataset.section);
                if (section) {
                    // keep the heading clear of the sticky toolbar, whose height depends on the width
                    section.style.scrollMarginTop = (state.root.querySelector(".status-toolbar").offsetHeight + 8) + "px";
                    section.scrollIntoView({behavior: "smooth", block: "start"});
                }
            });
        });
    }

    // ---------------------------------------------------------------- rendering

    function render() {
        renderCards();
        renderTransfers();
        renderUsers();
        renderHistory();
        if (state.config.admin) {
            renderHealth();
            renderSystem();
            renderStorage();
            renderServices();
            renderLog();
            renderCaches();
            renderTasks();
            renderSessions();
            renderPathWatchers();
        }
    }

    function replaceRows(tableId, rows, emptyId) {
        var table = byId(tableId);
        var body = table.tBodies[0];
        body.replaceChildren.apply(body, rows);
        table.hidden = rows.length === 0;
        if (emptyId) {
            byId(emptyId).hidden = rows.length !== 0;
        }
    }

    function card(title, value, detail, className) {
        return el("div", {className: "status-card" + (className ? " " + className : "")}, [
            el("div", {className: "status-card-title", text: title}),
            el("div", {className: "status-card-value"}, [value]),
            detail ? el("div", {className: "status-card-detail", text: detail}) : null
        ]);
    }

    function pill(status) {
        var known = ["UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN"];
        var name = status && known.indexOf(status) >= 0 ? status : "UNKNOWN";
        return el("span", {className: "status-pill status-pill-" + name.toLowerCase(), text: status || message("unknown")});
    }

    function renderCards() {
        var report = state.report;
        var cards = [];
        if (state.config.admin) {
            var health = state.health ? state.health.status : null;
            cards.push(card(message("card.health"), pill(health),
                health === "UP" ? message("healthup") : (health ? unhealthyComponents().join(", ") : message("healthunknown"))));
        }

        var active = report.transfers.filter(function (t) {
            return t.active;
        });
        var count = function (type) {
            return active.filter(function (t) {
                return t.type === type;
            }).length;
        };
        cards.push(card(message("card.streams"), formatNumber(count("STREAM")),
            message("card.transfersdetail", formatNumber(count("DOWNLOAD")), formatNumber(count("UPLOAD")))));

        if (state.config.admin) {
            var sessions = report.sessions || {};
            cards.push(card(message("card.sessions"), formatNumber(sessions.current),
                message("card.sessionsdetail", formatNumber(sessions.created), formatNumber(sessions.expired))));
            cards.push(card(message("card.uptime"), formatDuration(report.uptimeMillis)));
            if (report.log && report.log.readable) {
                cards.push(card(message("card.log"), formatNumber(report.log.errors),
                    message("card.logdetail", formatNumber(report.log.warnings)),
                    report.log.errors > 0 ? "status-card-alert" : null));
            }
        }

        var library = report.library;
        if (library.songs !== null) {
            cards.push(card(message("card.library"), message("library.songs", formatNumber(library.songs)),
                message("library.detail", formatNumber(library.albums), formatNumber(library.artists),
                    formatBytes(library.bytes), formatDuration(library.durationSeconds * 1000))));
        }
        var lastScan;
        if (library.scanning) {
            lastScan = card(message("card.lastscan"), message("scanning"), message("scanningdetail", formatNumber(library.scanCount)),
                "status-card-busy");
        } else if (library.lastScan) {
            lastScan = card(message("card.lastscan"), el("time", {
                datetime: new Date(library.lastScan).toISOString(), title: formatDateTime(library.lastScan),
                text: formatRelative(library.lastScan)
            }), formatDateTime(library.lastScan));
        } else {
            lastScan = card(message("card.lastscan"), message("notscanned"));
        }
        cards.push(lastScan);

        var container = byId("statusCards");
        container.replaceChildren.apply(container, cards);
    }

    function sparkline(points, color) {
        var width = 120;
        var height = 28;
        var graphic = svg("svg", {
            viewBox: "0 0 " + width + " " + height, width: width, height: height,
            class: "status-sparkline", "aria-hidden": "true", focusable: "false"
        });
        if (points.length < 2) {
            return graphic;
        }
        var max = Math.max.apply(null, points.map(function (p) {
            return p.kbps;
        })) || 1;
        var first = points[0].time;
        var span = (points[points.length - 1].time - first) || 1;
        var coordinates = points.map(function (p) {
            var x = (p.time - first) / span * (width - 2) + 1;
            var y = height - 1 - p.kbps / max * (height - 2);
            return x.toFixed(1) + "," + y.toFixed(1);
        });
        graphic.appendChild(svg("polyline", {
            points: coordinates.join(" "), fill: "none", stroke: color, "stroke-width": "1.5",
            "stroke-linejoin": "round", "stroke-linecap": "round"
        }));
        return graphic;
    }

    function progressBar(ratio, label, fillClass) {
        return el("div", {
            className: "status-bar", role: "progressbar", "aria-valuemin": "0", "aria-valuemax": "100",
            "aria-valuenow": String(Math.round(ratio * 100)), "aria-label": label
        }, [el("div", {
            className: "status-bar-fill" + (fillClass ? " " + fillClass : ""),
            style: "width: " + (Math.min(1, Math.max(0, ratio)) * 100).toFixed(1) + "%"
        })]);
    }

    /** Bar fill for a share of a resource in use: amber from 85 %, red from 95 %. */
    function usageClass(ratio) {
        return ratio >= 0.95 ? "status-bar-danger" : ratio >= 0.85 ? "status-bar-warn" : null;
    }

    /** A stacked bar chart of streamed, downloaded and uploaded bytes; updates the chart if there is one. */
    function transferChart(chart, canvasId, labels, rows, horizontal) {
        var max = Math.max.apply(null, rows.map(function (row) {
            return row.streamed + row.downloaded + row.uploaded;
        }).concat([1]));
        var unit = byteUnit(max);
        var unitLabel = formatUnit(0, unit.unit).replace(/[\d\s.,]/g, "");
        var data = {
            labels: labels,
            datasets: TRANSFER_SERIES.map(function (series) {
                var color = transferType(series.type).color;
                var bytes = rows.map(series.bytes);
                return {
                    label: message(series.label),
                    data: bytes.map(function (value) {
                        return value / unit.divisor;
                    }),
                    bytes: bytes,
                    backgroundColor: color.replace("rgb", "rgba").replace(")", ", 0.6)"),
                    borderColor: color,
                    borderWidth: 1
                };
            })
        };
        if (chart) {
            chart.data = data;
            var scales = chart.options.scales;
            (horizontal ? scales.x : scales.y).title.text = unitLabel;
            chart.update();
            return chart;
        }
        var colors = themeColors();
        var valueAxis = {stacked: true, beginAtZero: true, ticks: {color: colors.text}, grid: {color: colors.grid},
            title: {display: true, text: unitLabel, color: colors.text}};
        var labelAxis = {stacked: true, ticks: {color: colors.text}, grid: {color: colors.grid}};
        return new Chart(byId(canvasId), {
            type: "bar",
            data: data,
            options: {
                indexAxis: horizontal ? "y" : "x",
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                plugins: {
                    legend: {labels: {color: colors.text}},
                    tooltip: {
                        callbacks: {
                            label: function (context) {
                                return context.dataset.label + ": " + formatBytes(context.dataset.bytes.at(context.dataIndex));
                            }
                        }
                    }
                },
                scales: horizontal ? {x: valueAxis, y: labelAxis} : {x: labelAxis, y: valueAxis}
            }
        });
    }

    function renderTransfers() {
        var rows = state.report.transfers.map(function (t) {
            var type = transferType(t.type);
            var stateText = t.active ? (t.idleMillis > 15000 ? message("idle") : null) : message("finished");
            var progress = t.progress === null
                ? el("td", {className: "status-muted", text: "–"})
                : el("td", {}, [progressBar(t.progress, message("progress")), el("span", {className: "status-small", text: formatPercent(t.progress)})]);
            return el("tr", {className: t.active ? null : "status-inactive"}, [
                el("td", {}, [
                    el("span", {className: "status-type status-type-" + type.key, text: message(type.key)}),
                    stateText ? el("div", {className: "status-small status-muted", text: stateText}) : null
                ]),
                el("td", {text: t.username || message("unknown")}),
                el("td", {}, [t.player || message("unknown"),
                    t.playerType ? el("div", {className: "status-small status-muted status-agent", text: t.playerType, title: t.playerType}) : null]),
                el("td", {className: "status-path", text: t.path || message("unknown"), title: t.path}),
                progress,
                el("td", {className: "status-number", text: formatBytes(t.bytesTransferred)}),
                el("td", {className: "status-bitrate"}, [
                    el("span", {className: "status-number", text: formatKbps(t.currentKbps)}),
                    sparkline(t.bitrate, type.color)
                ]),
                timeCell(t.started)
            ]);
        });
        replaceRows("statusTransfers", rows, "statusTransfersEmpty");
    }

    function renderUsers() {
        var users = state.report.users.filter(function (u) {
            return u.streamed + u.downloaded + u.uploaded > 0;
        });
        byId("statusUsersEmpty").hidden = users.length > 0;
        byId("statusUsersChartBox").hidden = users.length === 0;
        var toggle = byId("statusUsersToggle");
        toggle.hidden = users.length <= USERS_SHOWN;
        toggle.textContent = state.showAllUsers ? message("showtop", USERS_SHOWN) : message("showall", users.length);
        if (!state.showAllUsers) {
            users = users.slice(0, USERS_SHOWN);
        }
        if (users.length === 0) {
            return;
        }

        byId("statusUsersChartBox").style.height = (users.length * 28 + 70) + "px";
        state.userChart = transferChart(state.userChart, "statusUsersChart", users.map(function (u) {
            return u.user;
        }), users, true);
    }

    function renderHistory() {
        var history = state.history;
        var note = byId("statusHistoryNote");
        if (!history) {
            note.textContent = "";
            return;
        }
        note.textContent = history.start ? message("historysince", formatDate(history.start)) : message("historypending");
        var labels = history.days.map(function (day) {
            return formatDate(day.date, {month: "short", day: "numeric"});
        });
        state.historyChart = transferChart(state.historyChart, "statusHistoryChart", labels, history.days, false);
    }

    /** The components that are not UP, as "db" or "diskSpace" or "parent/child". */
    function unhealthyComponents() {
        var result = [];
        flattenHealth(state.health ? state.health.components : null, "").forEach(function (component) {
            if (component.status !== "UP") {
                result.push(component.name);
            }
        });
        return result;
    }

    function flattenHealth(components, prefix) {
        var result = [];
        Object.entries(components || {}).sort(function (a, b) {
            return a[0].localeCompare(b[0]);
        }).forEach(function ([name, component]) {
            if (component.components) {
                result = result.concat(flattenHealth(component.components, prefix + name + "/"));
            } else {
                result.push({name: prefix + name, status: component.status, details: component.details});
            }
        });
        return result;
    }

    function detailsList(details) {
        var entries = Object.entries(details || {});
        if (entries.length === 0) {
            return el("span", {className: "status-muted", text: "–"});
        }
        var list = el("dl", {className: "status-details"});
        entries.forEach(function ([key, value]) {
            if (/bytes|total|free|threshold/i.test(key) && typeof value === "number") {
                value = formatBytes(value);
            } else if (typeof value === "object" && value !== null) {
                value = JSON.stringify(value);
            }
            list.appendChild(el("dt", {text: key}));
            list.appendChild(el("dd", {text: String(value)}));
        });
        return el("details", {}, [el("summary", {text: message("details")}), list]);
    }

    function renderHealth() {
        var banner = byId("statusHealthBanner");
        if (!state.health) {
            banner.textContent = message("healthunknown");
            banner.hidden = false;
            replaceRows("statusHealth", []);
            return;
        }
        var unhealthy = unhealthyComponents();
        banner.hidden = unhealthy.length === 0;
        banner.textContent = message("healthdown", unhealthy.join(", "));
        replaceRows("statusHealth", flattenHealth(state.health.components, "").map(function (component) {
            return el("tr", {}, [
                el("td", {text: component.name}),
                el("td", {}, [pill(component.status)]),
                el("td", {}, [detailsList(component.details)])
            ]);
        }));
    }

    function clearCache(name) {
        var url = state.config.urls.caches + (name ? "/" + encodeURIComponent(name) : "");
        var question = name ? message("cacheclearconfirm", name) : message("cacheclearallconfirm");
        Page.confirm(question).then(function (ok) {
            if (ok) {
                deleteCache(url, name);
            }
        });
    }

    function deleteCache(url, name) {
        var headers = [[state.config.csrf.header, state.config.csrf.token]];
        fetch(url, {method: "DELETE", credentials: "same-origin", headers: headers}).then(function (response) {
            if (!response.ok) {
                throw new Error(response.status + " " + response.statusText);
            }
            notify(name ? message("cachecleared", name) : message("cachesallcleared"));
            refresh();
        }).catch(function (error) {
            notify(message("cacheclearfailed", error.message), true);
        });
    }

    function renderCaches() {
        replaceRows("statusCaches", (state.report.caches || []).map(function (cache) {
            var ratio = cache.hitRatio;
            return el("tr", {}, [
                el("td", {text: cache.name}),
                el("td", {className: "status-ratio"}, ratio === null
                    ? [el("span", {className: "status-muted", text: "–"})]
                    : [progressBar(ratio, message("cachehitratio")), el("span", {className: "status-small", text: formatPercent(ratio)})]),
                el("td", {className: "status-number", text: formatNumber(cache.hits)}),
                el("td", {className: "status-number", text: formatNumber(cache.misses)}),
                el("td", {className: "status-number", text: formatNumber(cache.puts)}),
                el("td", {className: "status-number", text: formatNumber(cache.removals)}),
                el("td", {}, [el("button", {
                    type: "button", text: message("cacheclear"), "aria-label": message("cacheclear") + " " + cache.name,
                    onclick: function () {
                        clearCache(cache.name);
                    }
                })])
            ]);
        }), "statusCachesEmpty");
    }

    function renderTasks() {
        replaceRows("statusTasks", (state.report.tasks || []).map(function (task) {
            var overdue = task.nextRun !== null && task.nextRun > 0 && task.nextRun < state.now - 60000;
            var title = [
                message("scheduledcreated") + ": " + (task.created ? formatDateTime(task.created) : message("never")),
                message("scheduledfirstrun") + ": " + (task.firstRun ? formatDateTime(task.firstRun) : message("never"))
            ].join("\n");
            var next = timeCell(task.nextRun, overdue ? "status-overdue" : null);
            if (overdue) {
                next.appendChild(el("span", {className: "status-pill status-pill-down", text: message("overdue")}));
            }
            var last = timeCell(task.lastRun);
            if (task.lastStatus === "ERROR") {
                last.appendChild(el("span", {className: "status-pill status-pill-down", text: message("taskfailed"), title: task.lastError}));
            } else if (task.lastStatus === "STARTED") {
                last.appendChild(el("span", {className: "status-pill status-pill-up", text: message("taskrunning")}));
            }
            return el("tr", {}, [
                el("td", {text: task.name, title: title}),
                el("td", {text: task.type ? message("tasktype." + task.type) : message("unknown")}),
                last,
                next,
                el("td", {text: task.scheduledBy || message("unknown")})
            ]);
        }), "statusTasksEmpty");
    }

    function meter(label, value, ratio) {
        return el("div", {}, [
            el("span", {text: label}),
            el("strong", {text: value}),
            ratio === null || ratio === undefined ? null : progressBar(ratio, label, usageClass(ratio))
        ]);
    }

    function definitions(container, items) {
        container.replaceChildren();
        items.forEach(function (item) {
            if (item[1] === null || item[1] === undefined || item[1] === "") {
                return;
            }
            container.appendChild(el("dt", {text: item[0]}));
            container.appendChild(el("dd", {}, [item[1]]));
        });
    }

    function renderSystem() {
        var system = state.report.system;
        if (!system) {
            return;
        }
        definitions(byId("statusSystemInfo"), [
            [message("version"), system.version],
            [message("build"), [system.buildNumber, system.buildDate ? formatDateTime(system.buildDate) : null]
                .filter(Boolean).join(" \u00b7 ")],
            [message("java"), system.java],
            [message("os"), system.os],
            [message("server"), system.server],
            [message("database"), system.database || message("unknown")],
            [message("startedat"), formatDateTime(system.startTime) + " (" + formatRelative(system.startTime) + ")"]
        ]);
        var cpu = function (load) {
            return load === null ? "\u2013" : formatPercent(load);
        };
        var meters = byId("statusSystemMeters");
        meters.replaceChildren(
            meter(message("memory"), message("memoryused", formatBytes(system.heapUsed), formatBytes(system.heapMax)),
                system.heapMax > 0 ? system.heapUsed / system.heapMax : null),
            meter(message("cpuairsonic"), cpu(system.processCpu), system.processCpu),
            meter(message("cpusystem"), cpu(system.systemCpu), system.systemCpu),
            meter(message("processors"), formatNumber(system.processors)),
            meter(message("threads"), formatNumber(system.threads)));
    }

    function renderStorage() {
        replaceRows("statusStorage", (state.report.storage || []).map(function (storage) {
            var status = !storage.exists ? ["down", message("missing")]
                : !storage.enabled ? ["unknown", message("disabled")] : ["up", message("ok")];
            var space = el("td", {className: "status-muted", text: "\u2013"});
            if (storage.totalBytes) {
                var used = 1 - storage.freeBytes / storage.totalBytes;
                space = el("td", {className: "status-space"}, [
                    progressBar(used, message("free"), usageClass(used)),
                    el("span", {className: "status-small", text: message("freeof", formatBytes(storage.freeBytes), formatBytes(storage.totalBytes))})
                ]);
            }
            return el("tr", {}, [
                el("td", {text: storage.kind === "HOME" ? message("home") : (storage.name || message("unknown"))}),
                el("td", {className: "status-path", text: storage.path, title: storage.path}),
                el("td", {}, [el("span", {className: "status-pill status-pill-" + status[0], text: status[1]})]),
                space
            ]);
        }));
    }

    function renderServices() {
        var playback = state.report.playback;
        var network = state.report.network;
        if (!playback || !network) {
            return;
        }
        var flag = function (on, onText, offText) {
            return el("span", {className: "status-pill status-pill-" + (on ? "up" : "unknown"), text: on ? onText : offText});
        };
        definitions(byId("statusServices"), [
            [message("upnp"), flag(network.upnpRunning, message("running"), message("stopped"))],
            [message("dlna"), flag(network.dlnaEnabled, message("enabled"), message("disabled"))],
            [message("sonos"), flag(network.sonosEnabled, message("enabled"), message("disabled"))],
            [message("upnpport"), String(network.upnpPort)],
            [message("transcoder"), playback.transcoder
                ? el("span", {className: "status-path", text: playback.transcoder, title: playback.transcoder})
                : el("span", {className: "status-pill status-pill-down", text: message("notfound"),
                    title: playback.transcodeDirectory})],
            [message("activeplayers"), formatNumber(playback.activePlayers)]
        ]);
    }

    function renderLog() {
        var log = state.report.log;
        if (!log) {
            return;
        }
        byId("statusLogSummary").textContent = !log.readable ? message("logunreadable", log.file || message("unknown"))
            : message("logsummary", formatNumber(log.errors), formatNumber(log.warnings), log.file);
        replaceRows("statusLog", log.entries.map(function (entry) {
            return el("tr", {}, [
                timeCell(entry.time),
                el("td", {}, [el("span", {
                    className: "status-pill " + (entry.level === "ERROR" ? "status-pill-down" : "status-pill-warn"),
                    text: entry.level
                })]),
                el("td", {className: "status-small status-muted status-logger", text: entry.logger, title: entry.logger}),
                el("td", {className: "status-log-message", text: entry.message})
            ]);
        }), log.readable ? "statusLogEmpty" : null);
    }

    function renderSessions() {
        var sessions = state.report.sessions || {};
        [["current", sessions.current], ["created", sessions.created], ["expired", sessions.expired],
            ["rejected", sessions.rejected]].forEach(function ([key, value]) {
            byId("statusSessions-" + key).textContent = formatNumber(value);
        });
    }

    function renderPathWatchers() {
        replaceRows("statusPathWatchers", (state.report.pathWatchers || []).map(function (watcher) {
            return el("tr", {}, [
                el("td", {text: watcher.name}),
                el("td", {className: "status-path", text: watcher.path, title: watcher.path})
            ]);
        }), "statusPathWatchersEmpty");
    }

    // ---------------------------------------------------------------- lifecycle

    function stop() {
        if (!state) {
            return;
        }
        clearTimeout(state.timer);
        clearTimeout(state.notifyTimer);
        document.removeEventListener("visibilitychange", onVisibilityChange);
        if (state.userChart) {
            state.userChart.destroy();
        }
        if (state.historyChart) {
            state.historyChart.destroy();
        }
        state = null;
    }

    function init(config) {
        stop();
        var stored = Number(storageGet(INTERVAL_STORAGE_KEY));
        var storedDays = Number(storageGet(HISTORY_STORAGE_KEY));
        state = {
            config: config,
            messages: new Map(Object.entries(config.messages)),
            root: byId("statusPage"),
            interval: INTERVALS.indexOf(stored) >= 0 ? stored : DEFAULT_INTERVAL,
            paused: false,
            stale: false,
            timer: null,
            notifyTimer: null,
            report: null,
            health: null,
            now: Date.now(),
            userChart: null,
            showAllUsers: false,
            historyDays: HISTORY_DAYS.indexOf(storedDays) >= 0 ? storedDays : DEFAULT_HISTORY_DAYS,
            history: null,
            historyFetched: 0,
            historyChart: null
        };
        initToolbar();
        byId("statusUsersToggle").addEventListener("click", function () {
            state.showAllUsers = !state.showAllUsers;
            renderUsers();
        });
        var clearAll = byId("statusCachesClearAll");
        if (clearAll) {
            clearAll.addEventListener("click", function () {
                clearCache(null);
            });
        }
        document.addEventListener("visibilitychange", onVisibilityChange);
        refresh(true);
    }

    window.StatusPage = {
        init: init,
        stop: stop,
        /** Starts the page for a mounted page object (page.js) and stops it when the page is unmounted. */
        mount: function (page, config) {
            init(config);
            page.onDestroy(stop);
        },
        refresh: function () {
            return state ? refresh() : Promise.resolve();
        }
    };
})();
