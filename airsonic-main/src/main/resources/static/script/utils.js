/* global Page */
var entityMap = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': '&quot;',
    "'": '&#39;',
    "/": '&#x2F;'
};

function escapeHtml(string) {
    return String(string).replace(/[&<>"'\/]/g, function (s) {
        return entityMap[s];
    });
}

function popup(mylink, windowname) {
    return popupSize(mylink, windowname, 400, 200);
}

function popupSize(mylink, windowname, width, height) {
    var href;
    if (typeof(mylink) == "string") {
        href = mylink;
    } else {
        href = mylink.href;
    }

    var w = window.open(href, windowname, "width=" + width + ",height=" + height + ",scrollbars=yes,resizable=yes");
    w.focus();
    w.moveTo(300, 200);
    return false;
}

if (window.Page) {
    Page.actions({
        /** Opens a link in a popup window: data-popup-name, data-popup-width and data-popup-height. */
        popup: function (link) {
            return popupSize(link, link.dataset.popupName,
                Number.parseInt(link.dataset.popupWidth || "400", 10), Number.parseInt(link.dataset.popupHeight || "200", 10));
        },

        /* Media buttons (playButtons.html, coverArt.html): the media file id is in data-id. */
        playMedia: function (element) {
            top.playQueue.onPlay(element.dataset.id);
            return false;
        },
        playPodcastEpisode: function (element) {
            top.playQueue.onPlayPodcastEpisode(element.dataset.podcastId);
            return false;
        },
        addMedia: function (element) {
            top.playQueue.onAdd(element.dataset.id);
            showSuccessToast(element.dataset.toastMsg);
            return false;
        },
        addMediaNext: function (element) {
            top.playQueue.onAddNext(element.dataset.id);
            showSuccessToast(element.dataset.toastMsg);
            return false;
        },
        /* Cover art overlays: the album, playlist or podcast channel id is in data attributes. */
        playCoverArt: function (element) {
            var data = element.dataset;
            if (data.albumId) {
                top.playQueue.onPlay(data.albumId);
            } else if (data.playlistId) {
                top.playQueue.onPlayPlaylist(data.playlistId);
            } else if (data.podcastChannelId) {
                top.playQueue.onPlayPodcastChannel(data.podcastChannelId);
            }
            return false;
        },
        addCoverArt: function (element) {
            var data = element.dataset;
            if (data.albumId) {
                top.playQueue.onAdd(data.albumId);
            } else if (data.playlistId) {
                top.playQueue.onAddPlaylist(data.playlistId);
            }
            return false;
        },
        /** Shows the image a link points to in a dialog. */
        zoomImage: function (link) {
            showImageDialog(link.href, link.title);
            return false;
        },
        /** Stars or unstars a media file; the image carries data-starred, data-star-on and data-star-off. */
        toggleStar: function (image) {
            var starred = image.dataset.starred !== "true";
            image.dataset.starred = String(starred);
            image.src = starred ? image.dataset.starOn : image.dataset.starOff;
            top.StompClient.send("/app/rate/mediafile/" + (starred ? "star" : "unstar"), JSON.stringify([Number.parseInt(image.dataset.id, 10)]));
            return false;
        }
    });
}

/** Shows an image in a modal dialog that closes on click or Esc. */
function showImageDialog(src, title) {
    var dialog = document.createElement("dialog");
    dialog.className = "page-dialog image-dialog";
    var image = document.createElement("img");
    image.src = src;
    image.alt = title || "";
    dialog.appendChild(image);
    dialog.addEventListener("click", function () {
        dialog.close();
    });
    dialog.addEventListener("close", function () {
        dialog.remove();
    });
    document.body.appendChild(dialog);
    dialog.showModal();
}

function showSuccessToast(message) {
    if (message && window.jQuery && jQuery.fn.toastmessage) {
        jQuery().toastmessage("showSuccessToast", message);
    }
}

function updateQueryStringParameter(uri, key, value) {
    var re = new RegExp("([?&])" + key + "=.*?(&|$)", "i");
    if (uri.match(re)) {
        return uri.replace(re, '$1' + key + "=" + value + '$2');
    } else {
        var separator = uri.indexOf('?') !== -1 ? "&" : "?";
        return uri + separator + key + "=" + value;
    }
}

function formatDuration(seconds) {
    const value = Math.abs(seconds);
    const days = Math.floor(value / 86400);
    const hours = Math.floor((value - (days * 86400)) / 3600);
    const min = Math.floor((value - (days * 86400) - (hours * 3600)) / 60);
    const sec = value - (days * 86400) - (hours * 3600) - (min * 60);
    var res = '';
    if (days > 0) { res = days + 'd.'; }
    if (hours >= 10) {
        res = res + hours + ':';
    } else {
        if (hours == 0 && days == 0) {
            // do nothing
        } else {
            res = res + '0' + hours + ':';
        }
    }

    if (min >= 10) {
        res = res + min + ':';
    } else {
        res = res + '0' + min + ':';
    }

    if (sec >= 10) {
        res = res + sec;
    } else {
        res = res + '0' + sec;
    }

    if (seconds < 0) { res = '-' + res; }
    return res;
}
