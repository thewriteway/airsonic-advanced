/*
 * Drag and drop of media in the application shell: songs, albums and artists can be dragged from
 * the pages onto the play queue or onto a playlist in the left navigation.
 *
 * Drag sources:
 * - elements with data-media-ids="id,id,..." (rows of static tables),
 * - rows of DataTables marked with data-drag-media (the row data's id; when the row is selected,
 *   every selected row of the table),
 * - cover art (.coverart), which drags its album or playlist.
 *
 * Drop targets register with the page that owns them and use MediaDrag.accepts(event) in their
 * dragover handler and MediaDrag.payload() on drop. While something is dragged, the <html>
 * element has the class media-dragging, so that targets can show where a drop is possible.
 */
/* global jQuery */
(function () {
    "use strict";

    var TYPE = "application/x-airsonic-media";
    var current = null;

    function parseIds(value) {
        return String(value || "").split(/[\s,[\]]+/)
            .map(function (id) { return parseInt(id, 10); })
            .filter(function (id) { return !Number.isNaN(id); });
    }

    function dataTableOf(row) {
        var table = row.closest("table[data-drag-media]");
        if (!table || !row.closest("tbody") || !jQuery || !jQuery.fn.dataTable || !jQuery.fn.dataTable.isDataTable(table)) {
            return null;
        }
        return jQuery(table).DataTable();
    }

    /** The element a drag would start from, if the target is part of a drag source. */
    function sourceOf(target) {
        if (!target || !target.closest) {
            return null;
        }
        return target.closest("[data-media-ids], .coverart, table[data-drag-media] > tbody > tr");
    }

    /** What a drag from the source carries: {ids: [...]} or {playlistId: n}, plus a label. */
    function payloadOf(source) {
        if (source.hasAttribute("data-media-ids")) {
            var ids = parseIds(source.getAttribute("data-media-ids"));
            return ids.length ? {ids: ids, label: source.textContent.trim().split(/\s{2,}/)[0]} : null;
        }
        if (source.classList.contains("coverart")) {
            var overlay = source.querySelector(".coverart-play");
            var label = (source.querySelector(".coverart-frame") || source).getAttribute("title") || "";
            if (overlay && overlay.dataset.albumId) {
                return {ids: parseIds(overlay.dataset.albumId), label: label};
            }
            if (overlay && overlay.dataset.playlistId) {
                return {playlistId: parseInt(overlay.dataset.playlistId, 10), label: label};
            }
            return null;
        }
        var table = dataTableOf(source);
        var row = table && table.row(source);
        if (!row || !row.data()) {
            return null;
        }
        var rows = source.classList.contains("selected") ? table.rows({selected: true, order: "current"}).data().toArray() : [row.data()];
        var rowIds = rows.map(function (data) { return data.id; }).filter(function (id) { return id != null; });
        return rowIds.length ? {ids: rowIds, label: rows.length > 1 ? String(rows.length) : (row.data().title || row.data().name || "")} : null;
    }

    // rows are not draggable by default: make a source draggable when the pointer reaches it
    document.addEventListener("pointerover", function (event) {
        var source = sourceOf(event.target);
        if (source && !source.hasAttribute("draggable") && !source.closest(".playqueue-container")) {
            source.setAttribute("draggable", "true");
        }
    }, {passive: true});

    document.addEventListener("dragstart", function (event) {
        var source = sourceOf(event.target);
        if (!source || source.closest(".playqueue-container")) {
            return;
        }
        // form controls inside a row keep their own behaviour
        if (event.target.closest && event.target.closest("input, select, textarea, button")) {
            event.preventDefault();
            return;
        }
        var payload = payloadOf(source);
        if (!payload) {
            return;
        }
        current = payload;
        event.dataTransfer.effectAllowed = "copy";
        event.dataTransfer.setData(TYPE, JSON.stringify(payload));
        if (payload.label) {
            event.dataTransfer.setData("text/plain", payload.label);
        }
        document.documentElement.classList.add("media-dragging");
    });

    function reset() {
        current = null;
        document.documentElement.classList.remove("media-dragging");
    }
    document.addEventListener("dragend", reset);
    // dragend does not reach the document when the source left it during the drag (e.g. the page
    // changed); a drop on a target bubbles up to the window after the target has handled it
    window.addEventListener("drop", reset);

    window.MediaDrag = {
        /** Whether the drag over a target carries media; call preventDefault() to accept it. */
        accepts: function (event) {
            return current !== null && Array.prototype.indexOf.call(event.dataTransfer.types, TYPE) !== -1;
        },
        /** The media being dragged: {ids: [...]} or {playlistId: n}, or null. */
        payload: function () {
            return current;
        }
    };
})();
