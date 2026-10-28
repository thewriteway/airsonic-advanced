/*
 * Keyboard shortcuts of the application shell (see more.html for the list). They act on the play
 * queue and the shell, so they are only bound in the shell document.
 */
/* global Mousetrap, Page, playQueue */
(function () {
    "use strict";

    if (!window.keyboardShortcutsEnabled || !window.playQueue || !window.Shell) {
        return;
    }

    function go(url) {
        return function () {
            Page.navigate(url);
        };
    }

    Mousetrap.bind('space', function () { playQueue.onToggleStartStop(); return false; });
    Mousetrap.bind('left',  function () { playQueue.onPrevious(); });
    Mousetrap.bind('right', function () { playQueue.onNext('OFF'); });
    Mousetrap.bind('*',     function () { playQueue.onStarCurrent(); });
    Mousetrap.bind('plus',  function () { playQueue.onGainAdd(+5); });
    Mousetrap.bind('-',     function () { playQueue.onGainAdd(-5); });
    Mousetrap.bind('q',     function () { playQueue.onTogglePlayQueue(!playQueue.isVisible); });

    Mousetrap.bind('/',     function () { document.getElementById("query").focus(); return false; });
    Mousetrap.bind('m',     function () { window.TopNav.toggleSideBar(); });

    Mousetrap.bind('g h', go("home.view?"));
    Mousetrap.bind('g p', go("playlists.view?"));
    Mousetrap.bind('g o', go("podcastChannels.view?"));
    Mousetrap.bind('g s', go("settings.view?"));
    Mousetrap.bind('g b', go("bookmarks.view?"));
    Mousetrap.bind('g t', go("starred.view?"));
    Mousetrap.bind('g r', go("more.view?"));
    Mousetrap.bind('g a', go("help.view?"));
    Mousetrap.bind('?',   go("more.view#shortcuts"));
})();
